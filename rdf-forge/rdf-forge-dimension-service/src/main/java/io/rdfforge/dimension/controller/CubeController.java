package io.rdfforge.dimension.controller;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.dimension.entity.CubeEntity;
import io.rdfforge.dimension.service.CubeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import io.rdfforge.dimension.dto.GeneratedArtifact;
import io.rdfforge.dimension.dto.ObservationPage;

@RestController
@RequestMapping("/api/v1/cubes")
@Tag(name = "Cubes", description = "RDF Data Cube management API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class CubeController {

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
    public ResponseEntity<CubeEntity> getById(@PathVariable UUID id, @CurrentUser AuthUser user) {
        CubeEntity cube = requireReadableCube(id, user);
        return ResponseEntity.ok(cube);
    }

    @PostMapping
    @Operation(summary = "Create cube", description = "Create a new cube")
    public ResponseEntity<CubeEntity> create(@Valid @RequestBody CubeEntity cube, @CurrentUser AuthUser user) {
        cube.setCreatedBy(user.id()); // gateway-trusted identity
        CubeEntity created = cubeService.create(cube);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update cube", description = "Update an existing cube")
    public ResponseEntity<CubeEntity> update(
            @PathVariable UUID id,
            @Valid @RequestBody CubeEntity updates,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.update(id, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete cube", description = "Delete a cube")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        cubeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Mark cube published", description = "Mark a cube as published with optional observation count")
    public ResponseEntity<CubeEntity> markPublished(
            @PathVariable UUID id,
            @RequestBody(required = false) PublishRequest request,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        Long count = request != null ? request.observationCount() : null;
        CubeEntity updated = cubeService.markPublished(id, count);
        return ResponseEntity.ok(updated);
    }

    public record PublishRequest(Long observationCount) {}

    // ===== New endpoints for cube definition architecture =====

    public record GenerateShapeRequest(String name, String targetClass) {}
    public record GeneratePipelineRequest(String name, UUID triplestoreId, String graphUri) {}

    @PostMapping("/{id}/generate-shape")
    @Operation(summary = "Generate SHACL shape", description = "Generate a SHACL shape from cube definition column mappings")
    public ResponseEntity<GeneratedArtifact> generateShape(
            @PathVariable UUID id,
            @RequestBody(required = false) GenerateShapeRequest request,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        String shapeName = request != null && request.name() != null ? request.name() : null;
        String targetClass = request != null && request.targetClass() != null ? request.targetClass() : null;
        GeneratedArtifact result = cubeService.generateShape(id, shapeName, targetClass);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/generate-pipeline")
    @Operation(summary = "Generate draft pipeline", description = "Generate a draft ETL pipeline from cube definition")
    public ResponseEntity<GeneratedArtifact> generatePipeline(
            @PathVariable UUID id,
            @RequestBody(required = false) GeneratePipelineRequest request,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        String pipelineName = request != null && request.name() != null ? request.name() : null;
        UUID triplestoreId = request != null ? request.triplestoreId() : null;
        String graphUri = request != null ? request.graphUri() : null;
        GeneratedArtifact result = cubeService.generatePipeline(id, pipelineName, triplestoreId, graphUri);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}/shape/{shapeId}")
    @Operation(summary = "Link shape to cube", description = "Link an existing SHACL shape to the cube")
    public ResponseEntity<CubeEntity> linkShape(
            @PathVariable UUID id,
            @PathVariable UUID shapeId,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.linkShape(id, shapeId);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/pipeline/{pipelineId}")
    @Operation(summary = "Link pipeline to cube", description = "Link an existing pipeline to the cube")
    public ResponseEntity<CubeEntity> linkPipeline(
            @PathVariable UUID id,
            @PathVariable UUID pipelineId,
            @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.linkPipeline(id, pipelineId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/shape")
    @Operation(summary = "Unlink shape from cube", description = "Remove the link to the SHACL shape")
    public ResponseEntity<CubeEntity> unlinkShape(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.unlinkShape(id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/pipeline")
    @Operation(summary = "Unlink pipeline from cube", description = "Remove the link to the pipeline")
    public ResponseEntity<CubeEntity> unlinkPipeline(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.unlinkPipeline(id);
        return ResponseEntity.ok(updated);
    }

    private static final java.util.Set<String> VALID_EXPORT_FORMATS =
        java.util.Set.of("turtle", "ntriples", "jsonld", "trig");

    @GetMapping("/{id}/observations")
    @Operation(summary = "Preview observations", description = "Get paginated observation preview from cube's triplestore")
    public ResponseEntity<ObservationPage> getObservations(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser AuthUser user) {
        requireReadableCube(id, user);
        if (size > 100) size = 100;
        ObservationPage result = cubeService.getObservationPreview(id, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export cube RDF", description = "Export cube as RDF in specified format")
    public ResponseEntity<byte[]> exportCube(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "turtle") String format,
            @CurrentUser AuthUser user) {
        if (!VALID_EXPORT_FORMATS.contains(format)) {
            return ResponseEntity.badRequest().build();
        }
        requireReadableCube(id, user);

        byte[] data = cubeService.exportCube(id, format);

        String cubeName = cubeService.findById(id)
                .map(c -> c.getName().replaceAll("[^a-zA-Z0-9_-]", "_"))
                .orElse("cube-export");

        String contentType = switch (format) {
            case "ntriples" -> "application/n-triples";
            case "jsonld" -> "application/ld+json";
            case "trig" -> "application/trig";
            default -> "text/turtle";
        };

        String extension = switch (format) {
            case "ntriples" -> ".nt";
            case "jsonld" -> ".jsonld";
            case "trig" -> ".trig";
            default -> ".ttl";
        };

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + cubeName + extension + "\"")
                .body(data);
    }

    @PostMapping("/{id}/unlist")
    @Operation(summary = "Unlist cube", description = "Drop named graph from triplestore and set cube to draft status")
    public ResponseEntity<CubeEntity> unlistCube(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableCube(id, user);
        CubeEntity updated = cubeService.unlistCube(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * Cubes currently have no explicit "shared" flag — all cubes are treated as
     * owned. If a future product decision makes them tenant-public, relax this.
     */
    private CubeEntity requireReadableCube(UUID cubeId, AuthUser user) {
        CubeEntity cube = cubeService.findById(cubeId)
            .orElseThrow(() -> new ResourceNotFoundException("Cube", cubeId.toString()));
        if (user.isAdmin()) return cube;
        UUID owner = cube.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to read this cube");
        }
        return cube;
    }

    private void requireWritableCube(UUID cubeId, AuthUser user) {
        // Write == read access + ownership (admins bypass both).
        CubeEntity cube = cubeService.findById(cubeId)
            .orElseThrow(() -> new ResourceNotFoundException("Cube", cubeId.toString()));
        if (user.isAdmin()) return;
        UUID owner = cube.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to modify this cube");
        }
    }
}
