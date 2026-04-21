package io.rdfforge.pipeline.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.pipeline.dto.ReleaseBuildResponse;
import io.rdfforge.pipeline.dto.ReleaseCreateRequest;
import io.rdfforge.pipeline.dto.ReleaseDto;
import io.rdfforge.pipeline.service.ReleaseService;
import io.rdfforge.pipeline.service.ReleaseService.ReleaseArtifact;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the Release Factory (Phase 6).
 *
 * <p>All endpoints require an authenticated user; the service enforces
 * owner/admin authorization against the owning project. CORS origin is
 * configurable via {@code app.cors.allowed-origins} so local dev on :4200
 * works without code changes.
 */
@RestController
@RequestMapping("/api/v1/releases")
@RequiredArgsConstructor
@Tag(name = "Releases", description = "Release Factory: versioned exports of project assets")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ReleaseController {

    private final ReleaseService releaseService;

    @GetMapping
    @Operation(summary = "List releases in a project")
    public ResponseEntity<List<ReleaseDto>> list(
            @RequestParam UUID projectId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(releaseService.list(projectId, user));
    }

    @PostMapping
    @Operation(summary = "Create a new release draft")
    public ResponseEntity<ReleaseDto> create(
            @RequestParam UUID projectId,
            @Valid @RequestBody ReleaseCreateRequest request,
            @CurrentUser AuthUser user) {
        ReleaseDto created = releaseService.create(projectId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a release by ID")
    public ResponseEntity<ReleaseDto> get(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(releaseService.get(id, user));
    }

    @GetMapping("/{id}/manifest")
    @Operation(summary = "Return the manifest JSON for a release")
    public ResponseEntity<Map<String, Object>> manifest(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(releaseService.getManifest(id, user));
    }

    @PostMapping("/{id}/build")
    @Operation(summary = "Build the release bundle (sync in v1)")
    public ResponseEntity<ReleaseBuildResponse> build(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(releaseService.build(id, user));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a release (hide without delete)")
    public ResponseEntity<ReleaseDto> archive(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(releaseService.archive(id, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a release and its artifact")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        releaseService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the release zip bundle")
    public ResponseEntity<?> download(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        ReleaseArtifact artifact = releaseService.download(id, user);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(artifact.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + artifact.filename() + "\"")
            .body(artifact.resource());
    }
}
