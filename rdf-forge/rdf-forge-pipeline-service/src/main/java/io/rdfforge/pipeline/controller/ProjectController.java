package io.rdfforge.pipeline.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.pipeline.dto.ProjectCreateRequest;
import io.rdfforge.pipeline.dto.ProjectDto;
import io.rdfforge.pipeline.dto.ProjectSummaryDto;
import io.rdfforge.pipeline.dto.ProjectUpdateRequest;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.service.ProjectService;
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
 * REST API for the Project workspace.
 *
 * <p>All endpoints require an authenticated user; the service layer enforces
 * strict owner/admin authorization. CORS origin is configurable via
 * {@code app.cors.allowed-origins} so local dev on :4200 works without
 * code changes.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project workspace management API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List the caller's projects")
    public ResponseEntity<List<ProjectDto>> list(
            @RequestParam(required = false) ProjectStatus status,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.list(user, status));
    }

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ProjectDto> create(
            @Valid @RequestBody ProjectCreateRequest request,
            @CurrentUser AuthUser user) {
        ProjectDto created = projectService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by ID")
    public ResponseEntity<ProjectDto> get(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.findById(id, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing project")
    public ResponseEntity<ProjectDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.update(id, request, user));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a project (soft hide)")
    public ResponseEntity<ProjectDto> archive(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.archive(id, user));
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Unarchive a previously archived project")
    public ResponseEntity<ProjectDto> unarchive(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.unarchive(id, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        projectService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Dashboard summary with counts and activity")
    public ResponseEntity<ProjectSummaryDto> summary(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(projectService.summary(id, user));
    }
}
