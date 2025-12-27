package io.rdfforge.shacl.service;

import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.common.model.ValidationReport.ValidationResult;
import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.controller.CubeValidatorController.CubeValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * Service for validating RDF Data Cubes according to cube-link specification.
 *
 * Provides functionality equivalent to barnard59-cube commands:
 * - check-metadata: Validate cube metadata against profiles
 * - check-observations: Validate observations against constraints
 * - buildCubeShape: Generate SHACL constraint from observations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CubeValidationService {

    private static final String CUBE_NS = "https://cube.link/";
    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";

    private final ShaclValidator shaclValidator;
    private final ProfileValidationService profileValidationService;

    /**
     * Validate cube metadata (excluding observations) against a profile.
     */
    public ValidationReport validateCubeMetadata(String cubeData, String dataFormat, String profile) {
        try {
            // Parse the cube data
            Model cubeModel = parseRdf(cubeData, dataFormat);

            // Extract metadata (everything except observations)
            Model metadata = extractMetadataModel(cubeModel);

            log.info("Extracted {} triples of metadata from {} total triples",
                metadata.size(), cubeModel.size());

            // Validate against profile
            return profileValidationService.validateAgainstProfile(
                serializeToTurtle(metadata),
                "TURTLE",
                profile
            );
        } catch (Exception e) {
            log.error("Error validating cube metadata", e);
            return createErrorReport("Failed to validate cube metadata: " + e.getMessage());
        }
    }

    /**
     * Validate cube observations against the cube's constraint.
     */
    public ValidationReport validateCubeObservations(
            String cubeData,
            String constraintData,
            String dataFormat,
            int batchSize) {
        try {
            // Parse the cube data
            Model cubeModel = parseRdf(cubeData, dataFormat);

            // Get or extract the constraint
            Model constraintModel;
            if (constraintData != null && !constraintData.isBlank()) {
                constraintModel = parseRdf(constraintData, dataFormat);
            } else {
                constraintModel = extractConstraintModel(cubeModel);
            }

            if (constraintModel.isEmpty()) {
                return createErrorReport("No cube:Constraint found in the cube data");
            }

            // Extract observations
            List<Resource> observations = extractObservations(cubeModel);
            log.info("Found {} observations to validate", observations.size());

            if (observations.isEmpty()) {
                return createWarningReport("No observations found in the cube data");
            }

            // Validate in batches
            String constraintTurtle = serializeToTurtle(constraintModel);
            return validateObservationsInBatches(cubeModel, observations, constraintTurtle, batchSize);

        } catch (Exception e) {
            log.error("Error validating cube observations", e);
            return createErrorReport("Failed to validate cube observations: " + e.getMessage());
        }
    }

    /**
     * Perform full cube validation (metadata + observations).
     */
    public CubeValidationResult validateFullCube(
            String cubeData,
            String dataFormat,
            String profile,
            int batchSize) {

        CubeValidationResult result = new CubeValidationResult();

        try {
            // Parse the cube data once
            Model cubeModel = parseRdf(cubeData, dataFormat);

            // Validate metadata
            Model metadata = extractMetadataModel(cubeModel);
            ValidationReport metadataReport = profileValidationService.validateAgainstProfile(
                serializeToTurtle(metadata),
                "TURTLE",
                profile
            );
            result.setMetadataReport(metadataReport);

            // Validate observations
            Model constraintModel = extractConstraintModel(cubeModel);
            List<Resource> observations = extractObservations(cubeModel);
            result.setTotalObservations(observations.size());

            if (!constraintModel.isEmpty() && !observations.isEmpty()) {
                String constraintTurtle = serializeToTurtle(constraintModel);
                ValidationReport obsReport = validateObservationsInBatches(
                    cubeModel, observations, constraintTurtle, batchSize);
                result.setObservationsReport(obsReport);

                // Count valid/invalid
                int invalidCount = obsReport.getViolationCount();
                result.setInvalidObservations(invalidCount);
                result.setValidObservations(observations.size() - invalidCount);
            } else {
                result.setValidObservations(0);
                result.setInvalidObservations(0);
            }

            // Overall conformance
            boolean conforms = metadataReport.isConforms() &&
                (result.getObservationsReport() == null || result.getObservationsReport().isConforms());
            result.setConforms(conforms);

            // Summary
            result.setSummary(buildSummary(result));

        } catch (Exception e) {
            log.error("Error in full cube validation", e);
            result.setConforms(false);
            result.setSummary("Validation failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Extract cube constraint as Turtle string.
     */
    public String extractConstraint(String cubeData, String dataFormat) {
        Model cubeModel = parseRdf(cubeData, dataFormat);
        Model constraint = extractConstraintModel(cubeModel);
        return serializeToTurtle(constraint);
    }

    /**
     * Extract cube metadata (excluding observations) as Turtle string.
     */
    public String extractMetadata(String cubeData, String dataFormat) {
        Model cubeModel = parseRdf(cubeData, dataFormat);
        Model metadata = extractMetadataModel(cubeModel);
        return serializeToTurtle(metadata);
    }

    /**
     * Build a SHACL constraint from observation stream.
     * Analyzes observations and generates a cube:Constraint describing their structure.
     */
    public Map<String, Object> buildConstraintFromObservations(
            String observationsData,
            String dataFormat,
            String cubeUri,
            String constraintUri) {

        Model model = parseRdf(observationsData, dataFormat);
        List<Resource> observations = extractObservations(model);

        if (observations.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "No observations found in the data"
            );
        }

        // Analyze observation structure
        Map<String, PropertyStats> propertyStats = analyzeObservations(model, observations);

        // Build constraint model
        Model constraintModel = buildConstraintModel(
            cubeUri,
            constraintUri != null ? constraintUri : cubeUri + "/constraint",
            propertyStats
        );

        String constraintTurtle = serializeToTurtle(constraintModel);

        return Map.of(
            "success", true,
            "constraint", constraintTurtle,
            "format", "turtle",
            "observationsAnalyzed", observations.size(),
            "propertiesDetected", propertyStats.size()
        );
    }

    // ===== Helper Methods =====

    private Model parseRdf(String data, String format) {
        Model model = ModelFactory.createDefaultModel();
        String rdfFormat = format != null ? format.toUpperCase() : "TURTLE";
        model.read(new StringReader(data), null, rdfFormat);
        return model;
    }

    private String serializeToTurtle(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, RDFFormat.TURTLE_PRETTY);
        return writer.toString();
    }

    /**
     * Extract metadata from cube (everything except observations).
     */
    private Model extractMetadataModel(Model cubeModel) {
        Model metadata = ModelFactory.createDefaultModel();
        metadata.setNsPrefixes(cubeModel.getNsPrefixMap());

        Resource observationType = cubeModel.createResource(CUBE_NS + "Observation");

        // Copy all statements where subject is not an Observation
        StmtIterator iter = cubeModel.listStatements();
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            Resource subject = stmt.getSubject();

            // Check if subject is an Observation
            if (!cubeModel.contains(subject, RDF.type, observationType)) {
                metadata.add(stmt);
            }
        }

        return metadata;
    }

    /**
     * Extract the cube:Constraint from the model.
     */
    private Model extractConstraintModel(Model cubeModel) {
        Model constraint = ModelFactory.createDefaultModel();
        constraint.setNsPrefixes(cubeModel.getNsPrefixMap());

        Resource constraintType = cubeModel.createResource(CUBE_NS + "Constraint");
        Resource nodeShapeType = cubeModel.createResource(SHACL_NS + "NodeShape");

        // Find all Constraint resources
        ResIterator constraints = cubeModel.listSubjectsWithProperty(RDF.type, constraintType);
        while (constraints.hasNext()) {
            Resource constraintRes = constraints.next();
            // Copy all statements about this constraint
            copyResourceWithProperties(cubeModel, constraint, constraintRes, new HashSet<>());
        }

        // Also check for SHACL NodeShape that targets cube:Observation
        ResIterator shapes = cubeModel.listSubjectsWithProperty(RDF.type, nodeShapeType);
        while (shapes.hasNext()) {
            Resource shape = shapes.next();
            Property targetClass = cubeModel.createProperty(SHACL_NS, "targetClass");
            if (cubeModel.contains(shape, targetClass, cubeModel.createResource(CUBE_NS + "Observation"))) {
                copyResourceWithProperties(cubeModel, constraint, shape, new HashSet<>());
            }
        }

        return constraint;
    }

    /**
     * Recursively copy a resource and all its properties.
     */
    private void copyResourceWithProperties(Model source, Model target, Resource res, Set<Resource> visited) {
        if (visited.contains(res)) return;
        visited.add(res);

        StmtIterator stmts = source.listStatements(res, null, (RDFNode) null);
        while (stmts.hasNext()) {
            Statement stmt = stmts.next();
            target.add(stmt);

            // If object is a blank node, copy its properties too
            if (stmt.getObject().isAnon()) {
                copyResourceWithProperties(source, target, stmt.getObject().asResource(), visited);
            }
        }
    }

    /**
     * Extract all observations from the model.
     */
    private List<Resource> extractObservations(Model model) {
        List<Resource> observations = new ArrayList<>();
        Resource observationType = model.createResource(CUBE_NS + "Observation");

        ResIterator iter = model.listSubjectsWithProperty(RDF.type, observationType);
        while (iter.hasNext()) {
            observations.add(iter.next());
        }

        return observations;
    }

    /**
     * Validate observations in batches.
     */
    private ValidationReport validateObservationsInBatches(
            Model cubeModel,
            List<Resource> observations,
            String constraintTurtle,
            int batchSize) {

        List<ValidationResult> allResults = new ArrayList<>();
        boolean conforms = true;
        int violationCount = 0;
        int warningCount = 0;

        // If batchSize is 0, validate all at once
        if (batchSize <= 0) {
            batchSize = observations.size();
        }

        for (int i = 0; i < observations.size(); i += batchSize) {
            List<Resource> batch = observations.subList(i,
                Math.min(i + batchSize, observations.size()));

            // Create a model with just this batch of observations
            Model batchModel = ModelFactory.createDefaultModel();
            batchModel.setNsPrefixes(cubeModel.getNsPrefixMap());

            for (Resource obs : batch) {
                StmtIterator stmts = cubeModel.listStatements(obs, null, (RDFNode) null);
                while (stmts.hasNext()) {
                    batchModel.add(stmts.next());
                }
            }

            // Validate batch
            ValidationReport batchReport = shaclValidator.validate(batchModel, constraintTurtle);

            if (!batchReport.isConforms()) {
                conforms = false;
            }

            violationCount += batchReport.getViolationCount();
            warningCount += batchReport.getWarningCount();

            if (batchReport.getResults() != null) {
                allResults.addAll(batchReport.getResults());
            }

            log.debug("Validated batch {}/{} ({} observations)",
                (i / batchSize) + 1,
                (observations.size() / batchSize) + 1,
                batch.size());
        }

        return ValidationReport.builder()
            .conforms(conforms)
            .violationCount(violationCount)
            .warningCount(warningCount)
            .results(allResults)
            .metadata(Map.of(
                "observationsValidated", observations.size(),
                "batchSize", batchSize
            ))
            .build();
    }

    /**
     * Analyze observations to extract property statistics.
     */
    private Map<String, PropertyStats> analyzeObservations(Model model, List<Resource> observations) {
        Map<String, PropertyStats> stats = new HashMap<>();

        for (Resource obs : observations) {
            StmtIterator stmts = model.listStatements(obs, null, (RDFNode) null);
            while (stmts.hasNext()) {
                Statement stmt = stmts.next();
                Property prop = stmt.getPredicate();

                // Skip rdf:type
                if (prop.equals(RDF.type)) continue;

                String propUri = prop.getURI();
                stats.computeIfAbsent(propUri, k -> new PropertyStats()).addValue(stmt.getObject());
            }
        }

        return stats;
    }

    /**
     * Build a SHACL constraint model from property statistics.
     */
    private Model buildConstraintModel(String cubeUri, String constraintUri, Map<String, PropertyStats> stats) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("sh", SHACL_NS);
        model.setNsPrefix("cube", CUBE_NS);
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        model.setNsPrefix("schema", "https://schema.org/");

        Resource constraint = model.createResource(constraintUri);
        model.add(constraint, RDF.type, model.createResource(CUBE_NS + "Constraint"));
        model.add(constraint, RDF.type, model.createResource(SHACL_NS + "NodeShape"));
        model.add(constraint, model.createProperty(SHACL_NS, "targetClass"),
            model.createResource(CUBE_NS + "Observation"));

        // Add property shapes
        for (Map.Entry<String, PropertyStats> entry : stats.entrySet()) {
            String propUri = entry.getKey();
            PropertyStats propStats = entry.getValue();

            Resource propShape = model.createResource();
            model.add(constraint, model.createProperty(SHACL_NS, "property"), propShape);
            model.add(propShape, model.createProperty(SHACL_NS, "path"), model.createResource(propUri));
            model.add(propShape, model.createProperty(SHACL_NS, "minCount"),
                model.createTypedLiteral(1));
            model.add(propShape, model.createProperty(SHACL_NS, "maxCount"),
                model.createTypedLiteral(1));

            // Add datatype if consistent
            if (propStats.hasConsistentDatatype()) {
                model.add(propShape, model.createProperty(SHACL_NS, "datatype"),
                    model.createResource(propStats.getDatatype()));
            }

            // Add sh:in for small enumerated value sets
            if (propStats.hasEnumeratedValues() && propStats.getUniqueCount() <= 20) {
                RDFList valueList = model.createList(
                    propStats.getValues().stream()
                        .limit(50)
                        .map(v -> (RDFNode) model.createLiteral(v))
                        .iterator()
                );
                model.add(propShape, model.createProperty(SHACL_NS, "in"), valueList);
            }

            // Add nodeKind if all values are IRIs
            if (propStats.allValuesAreIris()) {
                model.add(propShape, model.createProperty(SHACL_NS, "nodeKind"),
                    model.createResource(SHACL_NS + "IRI"));
            }
        }

        return model;
    }

    private String buildSummary(CubeValidationResult result) {
        StringBuilder sb = new StringBuilder();

        if (result.isConforms()) {
            sb.append("Cube validation PASSED. ");
        } else {
            sb.append("Cube validation FAILED. ");
        }

        if (result.getMetadataReport() != null) {
            sb.append("Metadata: ");
            sb.append(result.getMetadataReport().isConforms() ? "valid" : "invalid");
            if (result.getMetadataReport().getViolationCount() > 0) {
                sb.append(" (").append(result.getMetadataReport().getViolationCount()).append(" violations)");
            }
            sb.append(". ");
        }

        sb.append("Observations: ").append(result.getTotalObservations()).append(" total");
        if (result.getInvalidObservations() > 0) {
            sb.append(", ").append(result.getInvalidObservations()).append(" invalid");
        }
        sb.append(".");

        return sb.toString();
    }

    private ValidationReport createErrorReport(String message) {
        return ValidationReport.builder()
            .conforms(false)
            .violationCount(1)
            .results(List.of(
                ValidationResult.builder()
                    .severity(ValidationResult.Severity.VIOLATION)
                    .message(message)
                    .build()
            ))
            .build();
    }

    private ValidationReport createWarningReport(String message) {
        return ValidationReport.builder()
            .conforms(true)
            .warningCount(1)
            .results(List.of(
                ValidationResult.builder()
                    .severity(ValidationResult.Severity.WARNING)
                    .message(message)
                    .build()
            ))
            .build();
    }

    /**
     * Internal class to track property statistics.
     */
    private static class PropertyStats {
        private final Set<String> datatypes = new HashSet<>();
        private final Set<String> values = new HashSet<>();
        private int count = 0;
        private boolean hasIris = false;
        private boolean hasLiterals = false;

        void addValue(RDFNode value) {
            count++;
            if (value.isLiteral()) {
                hasLiterals = true;
                Literal lit = value.asLiteral();
                if (lit.getDatatypeURI() != null) {
                    datatypes.add(lit.getDatatypeURI());
                }
                values.add(lit.getString());
            } else if (value.isURIResource()) {
                hasIris = true;
                values.add(value.asResource().getURI());
            }
        }

        boolean hasConsistentDatatype() {
            return datatypes.size() == 1 && !hasIris;
        }

        String getDatatype() {
            return datatypes.iterator().next();
        }

        boolean hasEnumeratedValues() {
            return values.size() <= 50;
        }

        int getUniqueCount() {
            return values.size();
        }

        Set<String> getValues() {
            return values;
        }

        boolean allValuesAreIris() {
            return hasIris && !hasLiterals;
        }
    }
}
