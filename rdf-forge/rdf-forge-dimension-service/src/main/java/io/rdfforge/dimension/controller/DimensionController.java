package io.rdfforge.dimension.controller;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.entity.DimensionEntity.DimensionType;
import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.service.DimensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dimensions")
@Tag(name = "Dimensions", description = "Shared dimension management API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class DimensionController {

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
    public ResponseEntity<DimensionEntity> getById(@PathVariable UUID id, @CurrentUser AuthUser user) {
        DimensionEntity dim = dimensionService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dimension", id.toString()));
        // Shared (isShared=true) dimensions are globally readable by any authenticated
        // user. Owned (project-scoped) dimensions require ownership or admin.
        if (!Boolean.TRUE.equals(dim.getIsShared())) {
            requireOwnerOrAdmin(dim, user, "read");
        }
        return ResponseEntity.ok(dim);
    }

    @PostMapping
    @Operation(summary = "Create dimension", description = "Create a new shared dimension")
    public ResponseEntity<DimensionEntity> create(@Valid @RequestBody DimensionEntity dimension, @CurrentUser AuthUser user) {
        dimension.setCreatedBy(user.id()); // gateway-trusted identity wins
        DimensionEntity created = dimensionService.create(dimension);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update dimension", description = "Update an existing dimension")
    public ResponseEntity<DimensionEntity> update(
            @PathVariable UUID id,
            @Valid @RequestBody DimensionEntity updates,
            @CurrentUser AuthUser user) {
        DimensionEntity existing = dimensionService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dimension", id.toString()));
        requireOwnerOrAdmin(existing, user, "update");
        DimensionEntity updated = dimensionService.update(id, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete dimension", description = "Delete a dimension and all its values")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUser AuthUser user) {
        DimensionEntity existing = dimensionService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dimension", id.toString()));
        requireOwnerOrAdmin(existing, user, "delete");
        dimensionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/values")
    @Operation(summary = "List dimension values", description = "Get paginated list of values for a dimension")
    public ResponseEntity<Page<DimensionValueEntity>> getValues(
            @PathVariable UUID id,
            @RequestParam(required = false) String search,
            Pageable pageable,
            @CurrentUser AuthUser user) {
        requireReadable(id, user);
        return ResponseEntity.ok(dimensionService.getValues(id, search, pageable));
    }

    @PostMapping("/{id}/values")
    @Operation(summary = "Add dimension value", description = "Add a single value to the dimension")
    public ResponseEntity<DimensionValueEntity> addValue(
            @PathVariable UUID id,
            @Valid @RequestBody DimensionValueEntity value,
            @CurrentUser AuthUser user) {
        requireWritable(id, user);
        DimensionValueEntity created = dimensionService.addValue(id, value);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/values/bulk")
    @Operation(summary = "Add multiple values", description = "Add multiple values to the dimension in bulk")
    public ResponseEntity<List<DimensionValueEntity>> addValues(
            @PathVariable UUID id,
            @Valid @RequestBody List<DimensionValueEntity> values,
            @CurrentUser AuthUser user) {
        requireWritable(id, user);
        List<DimensionValueEntity> created = dimensionService.addValues(id, values);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/values/{valueId}")
    @Operation(summary = "Update value", description = "Update an existing dimension value")
    public ResponseEntity<DimensionValueEntity> updateValue(
            @PathVariable UUID valueId,
            @Valid @RequestBody DimensionValueEntity updates,
            @CurrentUser AuthUser user) {
        // Value ownership tracks parent dimension ownership. Look up the value's
        // dimension and enforce write access on it.
        DimensionValueEntity existingValue = dimensionService.findValueById(valueId)
            .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", valueId.toString()));
        requireWritable(existingValue.getDimensionId(), user);
        DimensionValueEntity updated = dimensionService.updateValue(valueId, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/values/{valueId}")
    @Operation(summary = "Delete value", description = "Delete a dimension value")
    public ResponseEntity<Void> deleteValue(@PathVariable UUID valueId, @CurrentUser AuthUser user) {
        DimensionValueEntity existingValue = dimensionService.findValueById(valueId)
            .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", valueId.toString()));
        requireWritable(existingValue.getDimensionId(), user);
        dimensionService.deleteValue(valueId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/import/csv")
    @Operation(summary = "Import from CSV", description = "Import dimension values from a CSV file")
    public ResponseEntity<List<DimensionValueEntity>> importCsv(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthUser user) throws Exception {
        requireWritable(id, user);
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<DimensionValueEntity> imported = dimensionService.importFromCsv(id, content);
        return ResponseEntity.ok(imported);
    }

    @GetMapping("/{id}/export/turtle")
    @Operation(summary = "Export to Turtle", description = "Export dimension and values as SKOS in Turtle format")
    public ResponseEntity<String> exportTurtle(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireReadable(id, user);
        String turtle = dimensionService.exportToTurtle(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/turtle"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dimension.ttl\"")
            .body(turtle);
    }

    @GetMapping("/{id}/export/defined-term-set")
    @Operation(summary = "Export as DefinedTermSet", description = "Export dimension as schema:DefinedTermSet in Turtle format (cube.link compatible)")
    public ResponseEntity<String> exportDefinedTermSet(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireReadable(id, user);
        String turtle = dimensionService.exportToDefinedTermSet(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/turtle"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dimension-defined-term-set.ttl\"")
            .body(turtle);
    }

    @GetMapping("/{id}/tree")
    @Operation(summary = "Get hierarchy tree", description = "Get dimension values as a hierarchical tree")
    public ResponseEntity<List<DimensionValueEntity>> getTree(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireReadable(id, user);
        return ResponseEntity.ok(dimensionService.getHierarchyTree(id));
    }

    @GetMapping("/{id}/lookup")
    @Operation(summary = "Lookup value", description = "Find a value by code or URI")
    public ResponseEntity<DimensionValueEntity> lookup(
            @PathVariable UUID id,
            @RequestParam String q,
            @CurrentUser AuthUser user) {
        requireReadable(id, user);
        return dimensionService.lookupValue(id, q)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get statistics", description = "Get dimension statistics for a project")
    public ResponseEntity<Map<String, Object>> getStats(@RequestParam UUID projectId) {
        // Project-scoped aggregate; no per-dimension leakage.
        return ResponseEntity.ok(dimensionService.getStats(projectId));
    }

    /**
     * Shared dimensions are readable by any authenticated user.
     * Non-shared dimensions require ownership or admin.
     */
    private void requireReadable(UUID dimensionId, AuthUser user) {
        DimensionEntity dim = dimensionService.findById(dimensionId)
            .orElseThrow(() -> new ResourceNotFoundException("Dimension", dimensionId.toString()));
        if (!Boolean.TRUE.equals(dim.getIsShared())) {
            requireOwnerOrAdmin(dim, user, "read");
        }
    }

    /**
     * Write operations (add/update/delete values, import) always require ownership
     * or admin — even on shared dimensions. Shared reads are public, writes are not.
     */
    private void requireWritable(UUID dimensionId, AuthUser user) {
        DimensionEntity dim = dimensionService.findById(dimensionId)
            .orElseThrow(() -> new ResourceNotFoundException("Dimension", dimensionId.toString()));
        requireOwnerOrAdmin(dim, user, "modify");
    }

    private static void requireOwnerOrAdmin(DimensionEntity dim, AuthUser user, String action) {
        if (user.isAdmin()) return;
        UUID owner = dim.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to " + action + " this dimension");
        }
    }
}
