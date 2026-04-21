package io.rdfforge.engine.shacl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.rdfforge.common.exception.ShaclValidationException;
import io.rdfforge.common.metrics.RdfForgeMetrics;
import io.rdfforge.common.model.ValidationReport;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.Severity;
import org.apache.jena.shacl.lib.ShLib;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Component
public class ShaclValidatorService implements io.rdfforge.engine.shacl.ShaclValidator {

    // Default validation timeout: 60 seconds
    @Value("${shacl.validation.timeout.seconds:60}")
    private int validationTimeoutSeconds;

    private final Timer validationTimer;
    private final Counter validationsTotal;
    private final Counter validationsConforming;
    private final Counter validationsNonconforming;

    public ShaclValidatorService(MeterRegistry meterRegistry) {
        this.validationTimer = Timer.builder(RdfForgeMetrics.SHACL_VALIDATION_DURATION)
                .description("Duration of SHACL validation operations")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.validationsTotal = Counter.builder(RdfForgeMetrics.SHACL_VALIDATIONS_TOTAL)
                .description("Total SHACL validations performed")
                .register(meterRegistry);
        this.validationsConforming = Counter.builder(RdfForgeMetrics.SHACL_VALIDATIONS_CONFORMING)
                .description("SHACL validations that conformed")
                .register(meterRegistry);
        this.validationsNonconforming = Counter.builder(RdfForgeMetrics.SHACL_VALIDATIONS_NONCONFORMING)
                .description("SHACL validations with violations")
                .register(meterRegistry);
    }

    private final ExecutorService executorService = new ThreadPoolExecutor(
        2, Runtime.getRuntime().availableProcessors(),
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ValidationReport validate(Model dataModel, Model shapesModel) {
        validationsTotal.increment();
        Timer.Sample sample = Timer.start();

        // Execute validation with timeout
        Future<ValidationReport> future = executorService.submit(() -> performValidation(dataModel, shapesModel));

        try {
            ValidationReport report = future.get(validationTimeoutSeconds, TimeUnit.SECONDS);
            sample.stop(validationTimer);

            if (report.isConforms()) {
                validationsConforming.increment();
            } else {
                validationsNonconforming.increment();
            }

            return report;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("SHACL validation timed out after {} seconds", validationTimeoutSeconds);
            throw new ShaclValidationException(
                "SHACL validation timed out after " + validationTimeoutSeconds + " seconds. " +
                "Consider reducing the dataset size or simplifying shapes.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShaclValidationException("Validation was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ShaclValidationException) {
                throw (ShaclValidationException) cause;
            }
            log.error("SHACL validation failed", cause);
            throw new ShaclValidationException("Validation failed: " + cause.getMessage());
        }
    }

    private ValidationReport performValidation(Model dataModel, Model shapesModel) {
        long startTime = System.currentTimeMillis();

        try {
            Graph shapesGraph = shapesModel.getGraph();
            Graph dataGraph = dataModel.getGraph();

            Shapes shapes = Shapes.parse(shapesGraph);
            org.apache.jena.shacl.ValidationReport jenaReport = ShaclValidator.get().validate(shapes, dataGraph);

            List<ValidationReport.ValidationResult> results = new ArrayList<>();
            int violationCount = 0;
            int warningCount = 0;
            int infoCount = 0;

            for (ReportEntry entry : jenaReport.getEntries()) {
                ValidationReport.ValidationResult.Severity severity = mapSeverity(entry.severity());

                switch (severity) {
                    case VIOLATION -> violationCount++;
                    case WARNING -> warningCount++;
                    case INFO -> infoCount++;
                }

                results.add(ValidationReport.ValidationResult.builder()
                    .severity(severity)
                    .focusNode(nodeToString(entry.focusNode()))
                    .resultPath(entry.resultPath() != null ? entry.resultPath().toString() : null)
                    .value(nodeToString(entry.value()))
                    .message(entry.message())
                    .sourceConstraintComponent(nodeToString(entry.sourceConstraintComponent()))
                    .sourceShape(nodeToString(entry.sourceConstraint()))
                    .build());
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.debug("SHACL validation completed in {}ms: {} violations, {} warnings",
                durationMs, violationCount, warningCount);

            return ValidationReport.builder()
                .id(UUID.randomUUID())
                .conforms(jenaReport.conforms())
                .violationCount(violationCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .results(results)
                .validatedAt(Instant.now())
                .durationMs(durationMs)
                .build();
        } catch (Exception e) {
            log.error("Error during SHACL validation", e);
            throw new ShaclValidationException("Validation failed: " + e.getMessage());
        }
    }

    @Override
    public ValidationReport validate(Model dataModel, String shapesContent) {
        Model shapesModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        try {
            shapesModel.read(new java.io.StringReader(shapesContent), null, "TURTLE");
        } catch (RiotException e) {
            throw new ShaclValidationException(
                "Invalid SHACL shapes Turtle: " + e.getMessage(), e);
        }
        return validate(dataModel, shapesModel);
    }

    @Override
    public boolean validateSyntax(String shapesContent) {
        if (shapesContent == null || shapesContent.isBlank()) {
            log.warn("SHACL content is empty or null");
            return false;
        }

        try {
            Model shapesModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            shapesModel.read(new StringReader(shapesContent), null, "TURTLE");
            Shapes.parse(shapesModel.getGraph());
            return true;
        } catch (RiotException e) {
            // RDF parsing error
            log.warn("Invalid RDF syntax: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Invalid SHACL syntax: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate SHACL syntax with detailed error message.
     */
    public ValidationSyntaxResult validateSyntaxWithDetails(String shapesContent) {
        if (shapesContent == null || shapesContent.isBlank()) {
            return new ValidationSyntaxResult(false, "SHACL content is empty or null", null, null);
        }

        try {
            Model shapesModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            shapesModel.read(new StringReader(shapesContent), null, "TURTLE");
            Shapes.parse(shapesModel.getGraph());
            return new ValidationSyntaxResult(true, "Valid SHACL syntax", null, null);
        } catch (RiotException e) {
            String message = String.format("RDF parsing error: %s", e.getMessage());
            return new ValidationSyntaxResult(false, message, null, null);
        } catch (Exception e) {
            return new ValidationSyntaxResult(false, e.getMessage(), null, null);
        }
    }

    public record ValidationSyntaxResult(boolean valid, String message, Integer line, Integer column) {
    }

    private ValidationReport.ValidationResult.Severity mapSeverity(Severity severity) {
        if (severity == null) {
            return ValidationReport.ValidationResult.Severity.VIOLATION;
        }
        if (severity == Severity.Warning) {
            return ValidationReport.ValidationResult.Severity.WARNING;
        } else if (severity == Severity.Info) {
            return ValidationReport.ValidationResult.Severity.INFO;
        }
        return ValidationReport.ValidationResult.Severity.VIOLATION;
    }

    private String nodeToString(RDFNode node) {
        if (node == null) return null;
        if (node.isURIResource()) {
            return node.asResource().getURI();
        } else if (node.isLiteral()) {
            return node.asLiteral().getString();
        } else if (node.isAnon()) {
            return "_:" + node.asResource().getId();
        }
        return node.toString();
    }

    private String nodeToString(org.apache.jena.graph.Node node) {
        if (node == null) return null;
        if (node.isURI()) {
            return node.getURI();
        } else if (node.isLiteral()) {
            return node.getLiteralLexicalForm();
        } else if (node.isBlank()) {
            return "_:" + node.getBlankNodeLabel();
        }
        return node.toString();
    }
}
