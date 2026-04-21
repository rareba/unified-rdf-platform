package io.rdfforge.pipeline.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.pipeline.dto.CommentCreateRequest;
import io.rdfforge.pipeline.dto.CommentDto;
import io.rdfforge.pipeline.dto.CommentUpdateRequest;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import io.rdfforge.pipeline.service.CommentService;
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
 * REST API for inline comments on semantic assets.
 * Hosted exclusively in pipeline-service — other services that need to show
 * comments (shacl-service, triplestore-service, …) call through the gateway.
 *
 * <p>Gateway route: add {@code /api/v1/comments/**} to pipeline-service predicate.
 */
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Inline comments on semantic assets")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "List comments for an asset")
    public ResponseEntity<List<CommentDto>> list(
            @RequestParam("assetKind") AssetKind assetKind,
            @RequestParam("assetId") UUID assetId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(commentService.list(assetKind, assetId, user));
    }

    @PostMapping
    @Operation(summary = "Create a new comment or reply")
    public ResponseEntity<CommentDto> create(
            @Valid @RequestBody CommentCreateRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(request, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a comment (author only)")
    public ResponseEntity<CommentDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody CommentUpdateRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(commentService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a comment")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        commentService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
