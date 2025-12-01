package io.rdfforge.dimension.controller;

import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.entity.HierarchyEntity;
import io.rdfforge.dimension.service.HierarchyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hierarchies")
@Tag(name = "Hierarchies", description = "SKOS hierarchy management API")
@CrossOrigin(origins = "*")
public class HierarchyController {
    
    private static final Logger log = LoggerFactory.getLogger(HierarchyController.class);
    
    private final HierarchyService hierarchyService;
    
    public HierarchyController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }
    
    @GetMapping
    @Operation(summary = "List hierarchies", description = "Get hierarchies for a dimension")
    public ResponseEntity<List<HierarchyEntity>> list(@RequestParam UUID dimensionId) {
        return ResponseEntity.ok(hierarchyService.findByDimension(dimensionId));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get hierarchy", description = "Get hierarchy by ID")
    public ResponseEntity<HierarchyEntity> getById(@PathVariable UUID id) {
        return hierarchyService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Operation(summary = "Create hierarchy", description = "Create a new SKOS hierarchy")
    public ResponseEntity<HierarchyEntity> create(@RequestBody HierarchyEntity hierarchy) {
        try {
            HierarchyEntity created = hierarchyService.create(hierarchy);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create hierarchy: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update hierarchy", description = "Update an existing hierarchy")
    public ResponseEntity<HierarchyEntity> update(
            @PathVariable UUID id,
            @RequestBody HierarchyEntity updates) {
        try {
            HierarchyEntity updated = hierarchyService.update(id, updates);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete hierarchy", description = "Delete a hierarchy")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            hierarchyService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete hierarchy: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/default")
    @Operation(summary = "Get default hierarchy", description = "Get the default hierarchy for a dimension")
    public ResponseEntity<HierarchyEntity> getDefault(@RequestParam UUID dimensionId) {
        return hierarchyService.findDefault(dimensionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/values/{valueId}/parent")
    @Operation(summary = "Set parent", description = "Set the parent of a dimension value")
    public ResponseEntity<Void> setParent(
            @PathVariable UUID valueId,
            @RequestParam(required = false) UUID parentId) {
        try {
            hierarchyService.setParent(valueId, parentId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/values/{valueId}/ancestors")
    @Operation(summary = "Get ancestors", description = "Get all ancestors of a value")
    public ResponseEntity<List<DimensionValueEntity>> getAncestors(@PathVariable UUID valueId) {
        return ResponseEntity.ok(hierarchyService.getAncestors(valueId));
    }
    
    @GetMapping("/values/{valueId}/descendants")
    @Operation(summary = "Get descendants", description = "Get all descendants of a value")
    public ResponseEntity<List<DimensionValueEntity>> getDescendants(@PathVariable UUID valueId) {
        return ResponseEntity.ok(hierarchyService.getDescendants(valueId));
    }
    
    @GetMapping("/roots")
    @Operation(summary = "Get root values", description = "Get root values (no parent) for a dimension")
    public ResponseEntity<List<DimensionValueEntity>> getRoots(@RequestParam UUID dimensionId) {
        return ResponseEntity.ok(hierarchyService.getRoots(dimensionId));
    }
    
    @GetMapping("/children")
    @Operation(summary = "Get children", description = "Get children of a parent value")
    public ResponseEntity<List<DimensionValueEntity>> getChildren(@RequestParam UUID parentId) {
        return ResponseEntity.ok(hierarchyService.getChildren(parentId));
    }
    
    @GetMapping("/{id}/export/skos")
    @Operation(summary = "Export to SKOS", description = "Export hierarchy as SKOS Turtle")
    public ResponseEntity<String> exportSkos(
            @PathVariable UUID id,
            @RequestParam UUID dimensionId) {
        try {
            String skos = hierarchyService.exportHierarchyToSkos(dimensionId, id);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/turtle"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hierarchy.ttl\"")
                .body(skos);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PutMapping("/reorder")
    @Operation(summary = "Reorder children", description = "Set the order of child values")
    public ResponseEntity<Void> reorder(
            @RequestParam UUID parentId,
            @RequestBody List<UUID> orderedIds) {
        hierarchyService.reorderChildren(parentId, orderedIds);
        return ResponseEntity.ok().build();
    }
}
