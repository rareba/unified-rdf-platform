package io.rdfforge.dimension.controller;

import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.entity.DimensionEntity.DimensionType;
import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.service.DimensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dimensions")
@Tag(name = "Dimensions", description = "Shared dimension management API")
@CrossOrigin(origins = "*")
public class DimensionController {
    
    private static final Logger log = LoggerFactory.getLogger(DimensionController.class);
    
    private final DimensionService dimensionService;
    
    public DimensionController(DimensionService dimensionService) {
        this.dimensionService = dimensionService;
    }
    
    @GetMapping
    @Operation(summary = "List dimensions", description = "Get paginated list of dimensions for a project")
    public ResponseEntity<Page<DimensionEntity>> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) DimensionType type,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        
        if (projectId != null) {
            return ResponseEntity.ok(dimensionService.search(projectId, type, search, pageable));
        } else {
            return ResponseEntity.ok(dimensionService.findShared(type, search, pageable));
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get dimension", description = "Get dimension by ID")
    public ResponseEntity<DimensionEntity> getById(@PathVariable UUID id) {
        return dimensionService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Operation(summary = "Create dimension", description = "Create a new shared dimension")
    public ResponseEntity<DimensionEntity> create(@RequestBody DimensionEntity dimension) {
        try {
            DimensionEntity created = dimensionService.create(dimension);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create dimension: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update dimension", description = "Update an existing dimension")
    public ResponseEntity<DimensionEntity> update(
            @PathVariable UUID id,
            @RequestBody DimensionEntity updates) {
        try {
            DimensionEntity updated = dimensionService.update(id, updates);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete dimension", description = "Delete a dimension and all its values")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            dimensionService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete dimension: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{id}/values")
    @Operation(summary = "List dimension values", description = "Get paginated list of values for a dimension")
    public ResponseEntity<Page<DimensionValueEntity>> getValues(
            @PathVariable UUID id,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(dimensionService.getValues(id, search, pageable));
    }
    
    @PostMapping("/{id}/values")
    @Operation(summary = "Add dimension value", description = "Add a single value to the dimension")
    public ResponseEntity<DimensionValueEntity> addValue(
            @PathVariable UUID id,
            @RequestBody DimensionValueEntity value) {
        try {
            DimensionValueEntity created = dimensionService.addValue(id, value);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/{id}/values/bulk")
    @Operation(summary = "Add multiple values", description = "Add multiple values to the dimension in bulk")
    public ResponseEntity<List<DimensionValueEntity>> addValues(
            @PathVariable UUID id,
            @RequestBody List<DimensionValueEntity> values) {
        List<DimensionValueEntity> created = dimensionService.addValues(id, values);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/values/{valueId}")
    @Operation(summary = "Update value", description = "Update an existing dimension value")
    public ResponseEntity<DimensionValueEntity> updateValue(
            @PathVariable UUID valueId,
            @RequestBody DimensionValueEntity updates) {
        try {
            DimensionValueEntity updated = dimensionService.updateValue(valueId, updates);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/values/{valueId}")
    @Operation(summary = "Delete value", description = "Delete a dimension value")
    public ResponseEntity<Void> deleteValue(@PathVariable UUID valueId) {
        try {
            dimensionService.deleteValue(valueId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{id}/import/csv")
    @Operation(summary = "Import from CSV", description = "Import dimension values from a CSV file")
    public ResponseEntity<List<DimensionValueEntity>> importCsv(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<DimensionValueEntity> imported = dimensionService.importFromCsv(id, content);
            return ResponseEntity.ok(imported);
        } catch (Exception e) {
            log.error("CSV import failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/{id}/export/turtle")
    @Operation(summary = "Export to Turtle", description = "Export dimension and values as SKOS in Turtle format")
    public ResponseEntity<String> exportTurtle(@PathVariable UUID id) {
        try {
            String turtle = dimensionService.exportToTurtle(id);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/turtle"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dimension.ttl\"")
                .body(turtle);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{id}/tree")
    @Operation(summary = "Get hierarchy tree", description = "Get dimension values as a hierarchical tree")
    public ResponseEntity<List<DimensionValueEntity>> getTree(@PathVariable UUID id) {
        return ResponseEntity.ok(dimensionService.getHierarchyTree(id));
    }
    
    @GetMapping("/{id}/lookup")
    @Operation(summary = "Lookup value", description = "Find a value by code or URI")
    public ResponseEntity<DimensionValueEntity> lookup(
            @PathVariable UUID id,
            @RequestParam String q) {
        return dimensionService.lookupValue(id, q)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Get statistics", description = "Get dimension statistics for a project")
    public ResponseEntity<Map<String, Object>> getStats(@RequestParam UUID projectId) {
        return ResponseEntity.ok(dimensionService.getStats(projectId));
    }
}
