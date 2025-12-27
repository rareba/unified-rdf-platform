package io.rdfforge.dimension.controller;

import io.rdfforge.dimension.entity.CubeEntity;
import io.rdfforge.dimension.service.CubeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

import io.rdfforge.dimension.dto.GeneratedArtifact;

@RestController
@RequestMapping("/api/v1/cubes")
@Tag(name = "Cubes", description = "RDF Data Cube management API")
@CrossOrigin(origins = "*")
public class CubeController {

    private static final Logger log = LoggerFactory.getLogger(CubeController.class);

    private final CubeService cubeService;

    public CubeController(CubeService cubeService) {
        this.cubeService = cubeService;
    }

    @GetMapping
    @Operation(summary = "List cubes", description = "Get paginated list of cubes")
    public ResponseEntity<Page<CubeEntity>> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(cubeService.search(projectId, search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cube", description = "Get cube by ID")
    public ResponseEntity<CubeEntity> getById(@PathVariable UUID id) {
        return cubeService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create cube", description = "Create a new cube")
    public ResponseEntity<CubeEntity> create(@RequestBody CubeEntity cube) {
        try {
            CubeEntity created = cubeService.create(cube);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create cube: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update cube", description = "Update an existing cube")
    public ResponseEntity<CubeEntity> update(
            @PathVariable UUID id,
            @RequestBody CubeEntity updates) {
        try {
            CubeEntity updated = cubeService.update(id, updates);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete cube", description = "Delete a cube")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            cubeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete cube: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Mark cube published", description = "Mark a cube as published with optional observation count")
    public ResponseEntity<CubeEntity> markPublished(
            @PathVariable UUID id,
            @RequestBody(required = false) PublishRequest request) {
        try {
            Long count = request != null ? request.observationCount() : null;
            CubeEntity updated = cubeService.markPublished(id, count);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record PublishRequest(Long observationCount) {}

    // ===== New endpoints for cube definition architecture =====

    public record GenerateShapeRequest(String name, String targetClass) {}
    public record GeneratePipelineRequest(String name, UUID triplestoreId, String graphUri) {}

    @PostMapping("/{id}/generate-shape")
    @Operation(summary = "Generate SHACL shape", description = "Generate a SHACL shape from cube definition column mappings")
    public ResponseEntity<GeneratedArtifact> generateShape(
            @PathVariable UUID id,
            @RequestBody(required = false) GenerateShapeRequest request) {
        try {
            String shapeName = request != null && request.name() != null ? request.name() : null;
            String targetClass = request != null && request.targetClass() != null ? request.targetClass() : null;
            GeneratedArtifact result = cubeService.generateShape(id, shapeName, targetClass);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to generate shape for cube {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/generate-pipeline")
    @Operation(summary = "Generate draft pipeline", description = "Generate a draft ETL pipeline from cube definition")
    public ResponseEntity<GeneratedArtifact> generatePipeline(
            @PathVariable UUID id,
            @RequestBody(required = false) GeneratePipelineRequest request) {
        try {
            String pipelineName = request != null && request.name() != null ? request.name() : null;
            UUID triplestoreId = request != null ? request.triplestoreId() : null;
            String graphUri = request != null ? request.graphUri() : null;
            GeneratedArtifact result = cubeService.generatePipeline(id, pipelineName, triplestoreId, graphUri);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to generate pipeline for cube {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/shape/{shapeId}")
    @Operation(summary = "Link shape to cube", description = "Link an existing SHACL shape to the cube")
    public ResponseEntity<CubeEntity> linkShape(
            @PathVariable UUID id,
            @PathVariable UUID shapeId) {
        try {
            CubeEntity updated = cubeService.linkShape(id, shapeId);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/pipeline/{pipelineId}")
    @Operation(summary = "Link pipeline to cube", description = "Link an existing pipeline to the cube")
    public ResponseEntity<CubeEntity> linkPipeline(
            @PathVariable UUID id,
            @PathVariable UUID pipelineId) {
        try {
            CubeEntity updated = cubeService.linkPipeline(id, pipelineId);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/shape")
    @Operation(summary = "Unlink shape from cube", description = "Remove the link to the SHACL shape")
    public ResponseEntity<CubeEntity> unlinkShape(@PathVariable UUID id) {
        try {
            CubeEntity updated = cubeService.unlinkShape(id);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/pipeline")
    @Operation(summary = "Unlink pipeline from cube", description = "Remove the link to the pipeline")
    public ResponseEntity<CubeEntity> unlinkPipeline(@PathVariable UUID id) {
        try {
            CubeEntity updated = cubeService.unlinkPipeline(id);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
