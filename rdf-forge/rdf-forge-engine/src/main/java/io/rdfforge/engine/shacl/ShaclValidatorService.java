package io.rdfforge.engine.shacl;

import io.rdfforge.common.model.ValidationReport;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.Severity;
import org.apache.jena.shacl.lib.ShLib;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ShaclValidatorService implements io.rdfforge.engine.shacl.ShaclValidator {

    @Override
    public ValidationReport validate(Model dataModel, Model shapesModel) {
        long startTime = System.currentTimeMillis();
        
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
    }

    @Override
    public ValidationReport validate(Model dataModel, String shapesContent) {
        Model shapesModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        shapesModel.read(new java.io.StringReader(shapesContent), null, "TURTLE");
        return validate(dataModel, shapesModel);
    }

    @Override
    public boolean validateSyntax(String shapesContent) {
        try {
            Model shapesModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            shapesModel.read(new java.io.StringReader(shapesContent), null, "TURTLE");
            Shapes.parse(shapesModel.getGraph());
            return true;
        } catch (Exception e) {
            log.warn("Invalid SHACL syntax: {}", e.getMessage());
            return false;
        }
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
