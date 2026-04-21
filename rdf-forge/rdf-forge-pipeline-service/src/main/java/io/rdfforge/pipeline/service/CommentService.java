package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.CommentCreateRequest;
import io.rdfforge.pipeline.dto.CommentDto;
import io.rdfforge.pipeline.dto.CommentUpdateRequest;
import io.rdfforge.pipeline.entity.CommentEntity;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.repository.CommentRepository;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates creation, listing, editing, and soft-deletion of asset comments.
 *
 * <p>Authorization policy:
 * <ul>
 *   <li><b>Project access</b> — every read/write requires the caller to own the
 *       project (or to be an admin). Ownership is {@code ProjectEntity.createdBy
 *       == user.id()}; there is no project-membership model yet.</li>
 *   <li><b>Asset/project consistency</b> — on create, the declared
 *       {@code projectId} must match the asset's real project. We can only
 *       verify this for assets whose entities live in pipeline-service
 *       (PROJECT, MAPPING). See {@link #requireAssetBelongsToProject}.</li>
 *   <li><b>Author-only mutation</b> — only the author (or an admin) can edit or
 *       delete an existing comment.</li>
 * </ul>
 *
 * <p>Remaining gap (tracked TODO): cross-service asset existence for
 * ONTOLOGY / SHAPE / VALIDATION_SUITE / RELEASE / CUBE / DIMENSION. Those
 * entities live in other services (shacl-service, triplestore-service,
 * dimension-service) so pipeline-service cannot synchronously verify that
 * {@code assetId} exists or that it really belongs to {@code projectId}. For
 * those kinds we only enforce project ownership — asset existence is trusted
 * from the client until a central authz service or inter-service check is
 * introduced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;
    private final ProjectRepository projectRepository;
    private final MappingRepository mappingRepository;

    // -----------------------------------------------------------------
    // List
    // -----------------------------------------------------------------

    /**
     * List non-deleted comments for an asset, scoped to the caller-declared
     * project. The {@code projectId} is mandatory: it is the authorization
     * anchor — we verify the caller owns the project (or is admin) before
     * touching the comment table, and we filter the result set to that
     * project so cross-project leaks are impossible even if an attacker
     * guesses an assetId that happens to exist in a project they don't own.
     */
    @Transactional(readOnly = true)
    public List<CommentDto> list(UUID projectId, AssetKind kind, UUID assetId, AuthUser user) {
        requireAuthenticated(user);
        if (projectId == null || kind == null || assetId == null) {
            throw new RdfForgeException("BAD_REQUEST",
                    "projectId, assetKind and assetId are required",
                    HttpStatus.BAD_REQUEST);
        }
        requireProjectAccess(projectId, user);
        return repository.findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(kind, assetId)
                .stream()
                .filter(c -> projectId.equals(c.getProjectId()))
                .map(CommentDto::fromEntity)
                .toList();
    }

    // -----------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------

    @Transactional
    public CommentDto create(CommentCreateRequest req, AuthUser user) {
        requireAuthenticated(user);
        requireProjectAccess(req.projectId(), user);
        requireAssetBelongsToProject(req.assetKind(), req.assetId(), req.projectId());

        if (req.parentCommentId() != null) {
            // reply must live under the same asset AND project as its parent
            CommentEntity parent = repository.findById(req.parentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comment", req.parentCommentId().toString()));
            if (parent.getAssetKind() != req.assetKind()
                    || !parent.getAssetId().equals(req.assetId())
                    || !Objects.equals(parent.getProjectId(), req.projectId())) {
                throw new RdfForgeException("COMMENT_PARENT_MISMATCH",
                        "Parent comment is attached to a different asset or project",
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
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id.toString()));
        if (e.isDeleted()) {
            throw new ResourceNotFoundException("Comment", id.toString());
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
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id.toString()));
        if (e.isDeleted()) return;
        if (!user.isAdmin() && !user.id().equals(e.getAuthorId())) {
            throw new AccessDeniedException("Only the author or an admin can delete a comment");
        }
        e.setDeleted(true);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        log.info("Comment {} soft-deleted by {}", id, user.id());
    }

    // -----------------------------------------------------------------
    // Authz helpers
    // -----------------------------------------------------------------

    /**
     * Verify the caller can touch {@code projectId}. Throws
     * {@link AccessDeniedException} if the project does not exist or the user
     * is neither its owner nor an admin. Uses AccessDeniedException (not 404)
     * uniformly so we don't leak project existence to non-owners.
     */
    private void requireProjectAccess(UUID projectId, AuthUser user) {
        if (projectId == null) {
            throw new RdfForgeException("BAD_REQUEST", "projectId is required",
                    HttpStatus.BAD_REQUEST);
        }
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            throw new AccessDeniedException("Not authorized for this project");
        }
        if (!user.ownsOrIsAdmin(project.getCreatedBy())) {
            throw new AccessDeniedException("Not authorized for this project");
        }
    }

    /**
     * Verify the asset actually belongs to the declared project for the kinds
     * pipeline-service owns. For cross-service kinds (ONTOLOGY, SHAPE,
     * VALIDATION_SUITE, RELEASE, CUBE, DIMENSION) we cannot do a synchronous
     * lookup without pulling in other services, so we accept the client's
     * claim — project ownership was already enforced above, so the worst
     * case is a comment on a non-existent or wrong-project asset inside a
     * project the user legitimately owns (self-harm, not cross-tenant).
     *
     * TODO: when a central authz / asset-resolver service exists, replace the
     * "skip" branch with a real cross-service existence + project-binding
     * check.
     */
    private void requireAssetBelongsToProject(AssetKind kind, UUID assetId, UUID projectId) {
        if (kind == null || assetId == null || projectId == null) {
            throw new RdfForgeException("BAD_REQUEST",
                    "assetKind, assetId and projectId are required",
                    HttpStatus.BAD_REQUEST);
        }
        switch (kind) {
            case PROJECT -> {
                if (!assetId.equals(projectId)) {
                    throw new RdfForgeException("COMMENT_ASSET_PROJECT_MISMATCH",
                            "PROJECT comment assetId must equal projectId",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case MAPPING -> {
                MappingEntity mapping = mappingRepository.findById(assetId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Mapping", assetId.toString()));
                if (!projectId.equals(mapping.getProjectId())) {
                    throw new RdfForgeException("COMMENT_ASSET_PROJECT_MISMATCH",
                            "Mapping does not belong to the declared project",
                            HttpStatus.BAD_REQUEST);
                }
            }
            default -> {
                // ONTOLOGY / SHAPE / VALIDATION_SUITE / RELEASE / CUBE / DIMENSION.
                // Entity lives in another service — we already enforced project
                // ownership; cross-service asset existence is TODO.
                log.debug("Skipping cross-service asset existence check for kind={} assetId={} project={}",
                        kind, assetId, projectId);
            }
        }
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }
}
