package io.rdfforge.shacl.controller;

import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.shacl.service.CubeValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for cube-link validation functionality.
 *
 * Provides endpoints for validating RDF Data Cubes against cube-link profiles:
 * - Metadata validation (cube structure, dimensions, measures)
 * - Observation validation (individual data points against constraints)
 * - Full cube validation (metadata + observations)
 *
 * This mimics the functionality of barnard59's cube validation commands:
 * - b59 cube check-metadata
 * - b59 cube check-observations
 */
@RestController
@RequestMapping("/api/v1/cubes/validate")
@RequiredArgsConstructor
@Tag(name = "Cube Validator", description = "Cube-link compliant RDF Data Cube validation API")
@CrossOrigin(origins = "*")
@Slf4j
public class CubeValidatorController {

    private final CubeValidationService cubeValidationService;

    /**
     * Validate cube metadata against a profile.
     * Extracts metadata from the cube data and validates it against the specified profile.
     * Observations are excluded from validation.
     */
    @PostMapping("/metadata")
    @Operation(
        summary = "Validate cube metadata",
        description = "Validates cube metadata (excluding observations) against a cube-link profile. " +
                     "Equivalent to: b59 cube check-metadata --profile <profile>"
    )
    public ResponseEntity<ValidationReport> validateMetadata(
            @RequestBody CubeMetadataValidationRequest request) {

        log.info("Validating cube metadata against profile: {}", request.getProfile());

        ValidationReport report = cubeValidationService.validateCubeMetadata(
            request.getCubeData(),
            request.getDataFormat(),
            request.getProfile()
        );

        return ResponseEntity.ok(report);
    }

    /**
     * Validate cube observations against the cube's constraint.
     * Extracts observations and validates them against the constraint defined in the cube.
     */
    @PostMapping("/observations")
    @Operation(
        summary = "Validate cube observations",
        description = "Validates observations against the cube's constraint shape. " +
                     "Supports batch validation for large cubes. " +
                     "Equivalent to: b59 cube check-observations --constraint <constraint>"
    )
    public ResponseEntity<ValidationReport> validateObservations(
            @RequestBody CubeObservationsValidationRequest request) {

        log.info("Validating cube observations with batch size: {}", request.getBatchSize());

        ValidationReport report = cubeValidationService.validateCubeObservations(
            request.getCubeData(),
            request.getConstraintData(),
            request.getDataFormat(),
            request.getBatchSize() != null ? request.getBatchSize() : 50
        );

        return ResponseEntity.ok(report);
    }

    /**
     * Full cube validation (metadata + observations).
     * First validates metadata against the profile, then observations against the constraint.
     */
    @PostMapping("/full")
    @Operation(
        summary = "Full cube validation",
        description = "Performs complete cube validation: metadata against profile and observations against constraint."
    )
    public ResponseEntity<CubeValidationResult> validateFull(
            @RequestBody CubeFullValidationRequest request) {

        log.info("Performing full cube validation against profile: {}", request.getProfile());

        CubeValidationResult result = cubeValidationService.validateFullCube(
            request.getCubeData(),
            request.getDataFormat(),
            request.getProfile(),
            request.getBatchSize() != null ? request.getBatchSize() : 50
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Extract cube constraint from cube data.
     * Returns the SHACL constraint that describes the cube's structure.
     */
    @PostMapping("/extract-constraint")
    @Operation(
        summary = "Extract cube constraint",
        description = "Extracts the SHACL constraint shape from cube data."
    )
    public ResponseEntity<Map<String, String>> extractConstraint(
            @RequestBody ExtractConstraintRequest request) {

        String constraint = cubeValidationService.extractConstraint(
            request.getCubeData(),
            request.getDataFormat()
        );

        return ResponseEntity.ok(Map.of(
            "constraint", constraint,
            "format", "turtle"
        ));
    }

    /**
     * Extract cube metadata (excluding observations).
     */
    @PostMapping("/extract-metadata")
    @Operation(
        summary = "Extract cube metadata",
        description = "Extracts cube metadata, excluding observations, for inspection or separate validation."
    )
    public ResponseEntity<Map<String, String>> extractMetadata(
            @RequestBody ExtractMetadataRequest request) {

        String metadata = cubeValidationService.extractMetadata(
            request.getCubeData(),
            request.getDataFormat()
        );

        return ResponseEntity.ok(Map.of(
            "metadata", metadata,
            "format", "turtle"
        ));
    }

    /**
     * Build a SHACL constraint from observation stream.
     * Analyzes observations and generates a cube:Constraint that describes their structure.
     */
    @PostMapping("/build-constraint")
    @Operation(
        summary = "Build constraint from observations",
        description = "Analyzes a stream of observations and builds a SHACL cube:Constraint describing their structure. " +
                     "Equivalent to barnard59's buildCubeShape operation."
    )
    public ResponseEntity<Map<String, Object>> buildConstraint(
            @RequestBody BuildConstraintRequest request) {

        log.info("Building cube constraint from {} observations", request.getCubeUri());

        var result = cubeValidationService.buildConstraintFromObservations(
            request.getObservationsData(),
            request.getDataFormat(),
            request.getCubeUri(),
            request.getConstraintUri()
        );

        return ResponseEntity.ok(result);
    }

    // ===== Request DTOs =====

    @Data
    public static class CubeMetadataValidationRequest {
        private String cubeData;
        private String dataFormat;
        @Parameter(description = "Profile to validate against (standalone-cube-constraint, profile-visualize, profile-opendataswiss)")
        private String profile = "standalone-cube-constraint";
    }

    @Data
    public static class CubeObservationsValidationRequest {
        private String cubeData;
        private String constraintData;
        private String dataFormat;
        @Parameter(description = "Batch size for processing observations (default: 50, use 0 for all in memory)")
        private Integer batchSize;
    }

    @Data
    public static class CubeFullValidationRequest {
        private String cubeData;
        private String dataFormat;
        private String profile = "standalone-cube-constraint";
        private Integer batchSize;
    }

    @Data
    public static class ExtractConstraintRequest {
        private String cubeData;
        private String dataFormat;
    }

    @Data
    public static class ExtractMetadataRequest {
        private String cubeData;
        private String dataFormat;
    }

    @Data
    public static class BuildConstraintRequest {
        private String observationsData;
        private String dataFormat;
        private String cubeUri;
        private String constraintUri;
    }

    // ===== Response DTOs =====

    @Data
    public static class CubeValidationResult {
        private boolean conforms;
        private ValidationReport metadataReport;
        private ValidationReport observationsReport;
        private int totalObservations;
        private int validObservations;
        private int invalidObservations;
        private String summary;
    }
}
