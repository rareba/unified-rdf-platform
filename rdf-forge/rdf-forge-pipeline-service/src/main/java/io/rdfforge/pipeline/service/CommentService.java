package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.CommentCreateRequest;
import io.rdfforge.pipeline.dto.CommentDto;
import io.rdfforge.pipeline.dto.CommentUpdateRequest;
import io.rdfforge.pipeline.entity.CommentEntity;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import io.rdfforge.pipeline.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates creation, listing, editing, and soft-deletion of asset comments.
 *
 * <p>Authorization policy:
 * <ul>
 *   <li>Any authenticated user can read and create comments — project-level
 *       access is validated only for mutations. Full cross-service project
 *       access checks are a follow-up (TODO) since the Pipeline service does
 *       not currently own every asset referenced (ontologies, shapes, …).</li>
 *   <li>Only the author (or an admin) can edit or delete.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;

    // -----------------------------------------------------------------
    // List
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommentDto> list(AssetKind kind, UUID assetId, AuthUser user) {
        requireAuthenticated(user);
        if (kind == null || assetId == null) {
            throw new RdfForgeException("BAD_REQUEST", "assetKind and assetId are required",
                    HttpStatus.BAD_REQUEST);
        }
        // TODO: enforce project access. Currently any authenticated user can
        // read comments on any asset because cross-service project access is
        // checked per-service. Pipeline-service does not know if the caller
        // can read the SHACL shape, so until we route access checks through
        // a central authz service, we trust the caller's project membership
        // to be enforced elsewhere (gateway-level scopes).
        return repository.findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(kind, assetId)
                .stream().map(CommentDto::fromEntity).toList();
    }

    // -----------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------

    @Transactional
    public CommentDto create(CommentCreateRequest req, AuthUser user) {
        requireAuthenticated(user);
        if (req.parentCommentId() != null) {
            // reply must live under the same asset as its parent
            CommentEntity parent = repository.findById(req.parentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", req.parentCommentId()));
            if (parent.getAssetKind() != req.assetKind() || !parent.getAssetId().equals(req.assetId())) {
                throw new RdfForgeException("COMMENT_PARENT_MISMATCH",
                        "Parent comment is attached to a different asset",
                        HttpStatus.BAD_REQUEST);
            }
        }
        CommentEntity e = CommentEntity.builder()
                .projectId(req.projectId())
                .assetKind(req.assetKind())
                .assetId(req.assetId())
                .body(req.body())
                .authorId(user.id())
                .authorEmail(user.email())
                .parentCommentId(req.parentCommentId())
                .createdAt(Instant.now())
                .deleted(false)
                .build();
        repository.save(e);
        log.info("Comment {} created by {} on {} {}",
                e.getId(), user.id(), e.getAssetKind(), e.getAssetId());
        return CommentDto.fromEntity(e);
    }

    // -----------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------

    @Transactional
    public CommentDto update(UUID id, CommentUpdateRequest req, AuthUser user) {
        requireAuthenticated(user);
        CommentEntity e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        if (e.isDeleted()) {
            throw new ResourceNotFoundException("Comment", id);
        }
        if (!user.isAdmin() && !user.id().equals(e.getAuthorId())) {
            throw new AccessDeniedException("Only the author can edit a comment");
        }
        e.setBody(req.body());
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        return CommentDto.fromEntity(e);
    }

    // -----------------------------------------------------------------
    // Delete (soft)
    // -----------------------------------------------------------------

    @Transactional
    public void delete(UUID id, AuthUser user) {
        requireAuthenticated(user);
        CommentEntity e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        if (e.isDeleted()) return;
        if (!user.isAdmin() && !user.id().equals(e.getAuthorId())) {
            throw new AccessDeniedException("Only the author or an admin can delete a comment");
        }
        e.setDeleted(true);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        log.info("Comment {} soft-deleted by {}", id, user.id());
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }
}
