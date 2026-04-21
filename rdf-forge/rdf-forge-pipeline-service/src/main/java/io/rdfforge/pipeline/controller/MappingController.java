package io.rdfforge.pipeline.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.pipeline.dto.ExplainRequest;
import io.rdfforge.pipeline.dto.ExplainResponse;
import io.rdfforge.pipeline.dto.MappingCreateRequest;
import io.rdfforge.pipeline.dto.MappingDto;
import io.rdfforge.pipeline.dto.MappingPreviewRequest;
import io.rdfforge.pipeline.dto.MappingPreviewResponse;
import io.rdfforge.pipeline.dto.MappingUpdateRequest;
import io.rdfforge.pipeline.dto.MappingValidationRequest;
import io.rdfforge.pipeline.dto.MappingValidationResponse;
import io.rdfforge.pipeline.service.MappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for the Universal Mapping Studio. All endpoints require an
 * authenticated user; authorization is project-scoped via
 * {@link MappingService} against the owning project.
 *
 * <p>CORS origin is configurable through {@code app.cors.allowed-origins}
 * to match {@link ProjectController}.
 */
@RestController
@RequestMapping("/api/v1/mappings")
@RequiredArgsConstructor
@Tag(name = "Mappings", description = "Universal Mapping Studio API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class MappingController {

    private final MappingService mappingService;

    @GetMapping
    @Operation(summary = "List mappings for a project")
    public ResponseEntity<List<MappingDto>> list(
            @RequestParam UUID projectId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.listByProject(projectId, user));
    }

    @PostMapping
    @Operation(summary = "Create a new mapping")
    public ResponseEntity<MappingDto> create(
            @Valid @RequestBody MappingCreateRequest request,
            @CurrentUser AuthUser user) {
        MappingDto created = mappingService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a mapping by id")
    public ResponseEntity<MappingDto> get(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.findById(id, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a mapping")
    public ResponseEntity<MappingDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody MappingUpdateRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a mapping")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        mappingService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate the rule set of a mapping")
    public ResponseEntity<MappingValidationResponse> validate(
            @PathVariable UUID id,
            @RequestBody(required = false) MappingValidationRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.validate(id, request, user));
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Preview the first N triples produced by the mapping")
    public ResponseEntity<MappingPreviewResponse> preview(
            @PathVariable UUID id,
            @RequestBody(required = false) MappingPreviewRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.preview(id, request, user));
    }

    @PostMapping("/{id}/explain")
    @Operation(summary = "Explain which rule produced each triple in a row")
    public ResponseEntity<ExplainResponse> explain(
            @PathVariable UUID id,
            @RequestBody(required = false) ExplainRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(mappingService.explain(id, request, user));
    }
}
