package io.rdfforge.triplestore.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryCreateRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryDto;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunResponse;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryUpdateRequest;
import io.rdfforge.triplestore.service.SavedQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sparql")
@Tag(name = "SPARQL Workbench", description = "Saved SPARQL queries & ad-hoc execution (Phase 7)")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class SavedQueryController {

    private final SavedQueryService service;

    public SavedQueryController(SavedQueryService service) {
        this.service = service;
    }

    // ==================== Saved Queries ====================

    @GetMapping("/queries")
    @Operation(summary = "List saved queries", description = "List saved SPARQL queries in a project, optionally filtered by tags")
    public ResponseEntity<List<SavedQueryDto>> list(
            @RequestParam UUID projectId,
            @RequestParam(required = false) String tags,
            @CurrentUser AuthUser user
    ) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(service.list(projectId, tagList, user));
    }

    @PostMapping("/queries")
    @Operation(summary = "Create saved query")
    public ResponseEntity<SavedQueryDto> create(
            @RequestBody SavedQueryCreateRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.create(request, user));
    }

    @GetMapping("/queries/{id}")
    @Operation(summary = "Get saved query")
    public ResponseEntity<SavedQueryDto> get(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(service.get(id, user));
    }

    @PutMapping("/queries/{id}")
    @Operation(summary = "Update saved query")
    public ResponseEntity<SavedQueryDto> update(
            @PathVariable UUID id,
            @RequestBody SavedQueryUpdateRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.update(id, request, user));
    }

    @DeleteMapping("/queries/{id}")
    @Operation(summary = "Delete saved query")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUser AuthUser user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/queries/{id}/run")
    @Operation(summary = "Run saved query", description = "Substitute parameters (safely, via Jena) and execute against the given triplestore")
    public ResponseEntity<SavedQueryRunResponse> run(
            @PathVariable UUID id,
            @RequestBody SavedQueryRunRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.run(id, request, user));
    }

    // ==================== Inline execution ====================

    @PostMapping("/run")
    @Operation(summary = "Run inline query", description = "Execute an ad-hoc (unsaved) SPARQL query from the Workbench")
    public ResponseEntity<SavedQueryRunResponse> runInline(
            @RequestBody SavedQueryRunRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.runInline(request, user));
    }
}
