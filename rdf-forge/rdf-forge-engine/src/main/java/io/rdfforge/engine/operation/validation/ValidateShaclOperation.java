package io.rdfforge.engine.operation.validation;

import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.shacl.ShaclValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateShaclOperation implements Operation {
    private final ShaclValidator shaclValidator;

    @Override
    public String getId() {
        return "validate-shacl";
    }

    @Override
    public String getName() {
        return "Validate SHACL";
    }

    @Override
    public String getDescription() {
        return "Validate RDF data against SHACL shapes";
    }

    @Override
    public OperationType getType() {
        return OperationType.VALIDATION;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "shapeFile", new ParameterSpec("shapeFile", "Path to SHACL shapes file", String.class, false, null),
            "shapeContent", new ParameterSpec("shapeContent", "SHACL shapes as Turtle string", String.class, false, null),
            "shapeUri", new ParameterSpec("shapeUri", "URI of shapes in repository", String.class, false, null),
            "onViolation", new ParameterSpec("onViolation", "Action on violation (error|warn|continue)", String.class, false, "error"),
            "maxViolations", new ParameterSpec("maxViolations", "Maximum violations to report", Integer.class, false, 100)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String shapeFile = (String) context.parameters().get("shapeFile");
        String shapeContent = (String) context.parameters().get("shapeContent");
        String onViolation = (String) context.parameters().getOrDefault("onViolation", "error");

        if (context.inputModel() == null) {
            throw new OperationException(getId(), "No RDF model provided for validation");
        }

        Model shapesModel = loadShapes(shapeFile, shapeContent);

        // If no shapes provided, skip validation and pass through the model
        if (shapesModel == null) {
            if (context.callback() != null) {
                context.callback().onLog("WARN", "SHACL validation skipped: no shapes provided (shapeFile or shapeContent)");
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("skipped", true);
            metadata.put("reason", "No shapes provided");
            return new OperationResult(true, null, context.inputModel(), metadata, null);
        }

        ValidationReport report = shaclValidator.validate(context.inputModel(), shapesModel);

        if (context.callback() != null) {
            context.callback().onLog("INFO", "SHACL validation complete: " + 
                (report.isConforms() ? "PASSED" : "FAILED with " + report.getViolationCount() + " violations"));
            context.callback().onMetric("validationConforms", report.isConforms());
            context.callback().onMetric("violationCount", report.getViolationCount());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("conforms", report.isConforms());
        metadata.put("violationCount", report.getViolationCount());
        metadata.put("warningCount", report.getWarningCount());
        metadata.put("report", report);

        if (!report.isConforms() && "error".equalsIgnoreCase(onViolation)) {
            StringBuilder errorMsg = new StringBuilder("SHACL validation failed with ")
                .append(report.getViolationCount()).append(" violations:\n");
            
            report.getResults().stream()
                .filter(r -> r.getSeverity() == ValidationReport.ValidationResult.Severity.VIOLATION)
                .limit(5)
                .forEach(r -> errorMsg.append("  - ").append(r.getMessage()).append("\n"));

            return new OperationResult(false, null, context.inputModel(), metadata, errorMsg.toString());
        }

        return new OperationResult(true, null, context.inputModel(), metadata, null);
    }

    private Model loadShapes(String shapeFile, String shapeContent) throws OperationException {
        Model shapesModel = ModelFactory.createDefaultModel();

        if (shapeFile != null && !shapeFile.isEmpty()) {
            try {
                Path path = Path.of(shapeFile);
                if (!Files.exists(path)) {
                    throw new OperationException(getId(), "Shape file not found: " + shapeFile);
                }
                RDFDataMgr.read(shapesModel, shapeFile);
            } catch (Exception e) {
                throw new OperationException(getId(), "Error loading shapes from file: " + e.getMessage(), e);
            }
        } else if (shapeContent != null && !shapeContent.isEmpty()) {
            try {
                shapesModel.read(new StringReader(shapeContent), null, "TURTLE");
            } catch (Exception e) {
                throw new OperationException(getId(), "Error parsing shape content: " + e.getMessage(), e);
            }
        } else {
            // No shapes provided - return null to indicate skip validation
            return null;
        }

        return shapesModel;
    }
}
