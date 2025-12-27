package io.rdfforge.shacl.controller;

import io.rdfforge.common.model.Shape;
import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.service.ProfileValidationService;
import io.rdfforge.shacl.service.ShapeBuilderService;
import io.rdfforge.shacl.service.ShapeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shapes")
@RequiredArgsConstructor
@Tag(name = "SHACL Shapes", description = "SHACL shape management and validation API")
@CrossOrigin(origins = "*")
public class ShapeController {
    private final ShapeService shapeService;
    private final ShapeBuilderService shapeBuilderService;
    private final ShaclValidator shaclValidator;
    private final ProfileValidationService profileValidationService;

    @PostMapping
    @Operation(summary = "Create a new shape")
    public ResponseEntity<Shape> create(@Valid @RequestBody Shape shape) {
        Shape created = shapeService.create(shape);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List shapes")
    public ResponseEntity<Page<Shape>> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        Page<Shape> shapes;
        if (search != null && !search.isBlank()) {
            shapes = shapeService.search(projectId, search, pageable);
        } else {
            shapes = shapeService.list(projectId, pageable);
        }
        return ResponseEntity.ok(shapes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get shape by ID")
    public ResponseEntity<Shape> getById(@PathVariable UUID id) {
        Shape shape = shapeService.getById(id);
        return ResponseEntity.ok(shape);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update shape")
    public ResponseEntity<Shape> update(@PathVariable UUID id, @Valid @RequestBody Shape shape) {
        Shape updated = shapeService.update(id, shape);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete shape")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        shapeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    @Operation(summary = "List shape templates")
    public ResponseEntity<List<Shape>> getTemplates() {
        List<Shape> templates = shapeService.getTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/categories")
    @Operation(summary = "List shape categories")
    public ResponseEntity<List<String>> getCategories(@RequestParam(required = false) UUID projectId) {
        List<String> categories = shapeService.getCategories(projectId);
        return ResponseEntity.ok(categories);
    }

    @PostMapping("/build")
    @Operation(summary = "Build SHACL shape from definition")
    public ResponseEntity<Map<String, String>> buildShape(
            @RequestBody ShapeBuilderService.ShapeDefinition definition) {
        String turtle = shapeBuilderService.buildShape(definition);
        return ResponseEntity.ok(Map.of("content", turtle));
    }

    @PostMapping("/validate-syntax")
    @Operation(summary = "Validate SHACL syntax")
    public ResponseEntity<Map<String, Object>> validateSyntax(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        boolean valid = shaclValidator.validateSyntax(content);
        return ResponseEntity.ok(Map.of(
            "valid", valid,
            "message", valid ? "SHACL syntax is valid" : "Invalid SHACL syntax"
        ));
    }

    @PostMapping("/validate")
    @Operation(summary = "Run SHACL validation")
    public ResponseEntity<ValidationReport> validate(@RequestBody ValidationRequest request) {
        Model dataModel = ModelFactory.createDefaultModel();
        dataModel.read(new StringReader(request.getDataContent()), null, 
            request.getDataFormat() != null ? request.getDataFormat() : "TURTLE");

        ValidationReport report;
        if (request.getShapeId() != null) {
            Shape shape = shapeService.getById(request.getShapeId());
            report = shaclValidator.validate(dataModel, shape.getContent());
        } else if (request.getShapeContent() != null) {
            report = shaclValidator.validate(dataModel, request.getShapeContent());
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(report);
    }

    // ===== Profile-based validation endpoints =====

    @GetMapping("/profiles")
    @Operation(summary = "List available validation profiles", 
               description = "Returns list of cube-link validation profiles (standalone, visualize, opendataswiss)")
    public ResponseEntity<List<ProfileValidationService.ProfileInfo>> getProfiles() {
        return ResponseEntity.ok(profileValidationService.getAvailableProfiles());
    }

    @PostMapping("/validate-profile")
    @Operation(summary = "Validate against a specific profile",
               description = "Validate RDF data against a cube-link profile (standalone-cube-constraint, profile-visualize, profile-opendataswiss)")
    public ResponseEntity<ValidationReport> validateAgainstProfile(@RequestBody ProfileValidationRequest request) {
        if (request.getProfile() == null || request.getDataContent() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        if (!profileValidationService.isProfileAvailable(request.getProfile())) {
            return ResponseEntity.badRequest().build();
        }

        ValidationReport report = profileValidationService.validateAgainstProfile(
            request.getDataContent(),
            request.getDataFormat(),
            request.getProfile()
        );
        return ResponseEntity.ok(report);
    }

    @PostMapping("/validate-all-profiles")
    @Operation(summary = "Validate against all profiles",
               description = "Validate RDF data against all available cube-link profiles and return combined results")
    public ResponseEntity<Map<String, ValidationReport>> validateAgainstAllProfiles(
            @RequestBody ProfileValidationRequest request) {
        if (request.getDataContent() == null) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, ValidationReport> results = profileValidationService.validateAgainstAllProfiles(
            request.getDataContent(),
            request.getDataFormat()
        );
        return ResponseEntity.ok(results);
    }

    @lombok.Data
    public static class ValidationRequest {
        private UUID shapeId;
        private String shapeContent;
        private String dataContent;
        private String dataFormat;
    }

    @lombok.Data
    public static class ProfileValidationRequest {
        private String profile;
        private String dataContent;
        private String dataFormat;
    }
}

