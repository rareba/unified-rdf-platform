package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.ProjectCreateRequest;
import io.rdfforge.pipeline.dto.ProjectDto;
import io.rdfforge.pipeline.dto.ProjectSummaryDto;
import io.rdfforge.pipeline.dto.ProjectUpdateRequest;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.repository.PipelineRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lifecycle management for {@link ProjectEntity}. All mutating operations
 * consume a gateway-resolved {@link AuthUser} so that the service itself
 * holds the ownership predicate and can be reused by future controllers
 * (e.g. a CLI or batch importer) without re-implementing authz.
 *
 * <p>Deletion is currently a straight DELETE. Cascading cleanup of
 * cross-service dependents (pipelines, shapes, dimensions, cubes, jobs,
 * triplestores) is Phase 7 work — see TODO blocks in
 * {@link #delete(UUID, AuthUser)} and {@link #summary(UUID, AuthUser)}.
 */
@Slf4j
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final PipelineRepository pipelineRepository;

    public ProjectService(ProjectRepository projectRepository,
                          PipelineRepository pipelineRepository) {
        this.projectRepository = projectRepository;
        this.pipelineRepository = pipelineRepository;
    }

    /**
     * Create a new project owned by {@code user}. The {@code createdBy}
     * column is populated from the authenticated user — never from the
     * request body — to prevent ownership spoofing.
     */
    @Transactional
    public ProjectDto create(ProjectCreateRequest request, AuthUser user) {
        requireAuthenticated(user);
        validateName(request.name());
        String normalizedBaseUri = normalizeBaseUri(request.baseUri());

        if (projectRepository.existsByCreatedByAndName(user.id(), request.name())) {
            // Duplicate name is a 409 conflict — surface as validation error so the
            // GlobalExceptionHandler emits a 400-family ProblemDetail. If a stricter
            // 409 is required, introduce a dedicated ConflictException in Phase 1.1.
            throw new PipelineValidationException(
                "A project named '" + request.name() + "' already exists for this user");
        }

        ProjectEntity entity = ProjectEntity.builder()
            .name(request.name())
            .description(request.description())
            .baseUri(normalizedBaseUri)
            .status(ProjectStatus.ACTIVE)
            .createdBy(user.id())
            .metadata(request.metadata())
            .build();

        try {
            entity = projectRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Race with another transaction inserting the same (createdBy, name).
            log.warn("Duplicate project insert for user={} name={}", user.id(), request.name());
            throw new PipelineValidationException(
                "A project named '" + request.name() + "' already exists for this user");
        }

        log.info("Created project: id={} name={} owner={}", entity.getId(), entity.getName(), user.id());
        return toDto(entity);
    }

    /**
     * Mutate a project. Only the owner (or an admin) may update. All fields
     * in the request are optional — {@code null} means "leave unchanged" so
     * partial updates do not require the client to re-send the full state.
     */
    @Transactional
    public ProjectDto update(UUID id, ProjectUpdateRequest request, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity existing = findOrThrow(id);
        requireOwnerOrAdmin(existing, user, "update");

        if (request.name() != null) {
            validateName(request.name());
            // Name changes must re-check uniqueness within the original owner's namespace.
            if (!existing.getName().equals(request.name())
                && projectRepository.existsByCreatedByAndName(existing.getCreatedBy(), request.name())) {
                throw new PipelineValidationException(
                    "A project named '" + request.name() + "' already exists for this user");
            }
            existing.setName(request.name());
        }
        if (request.description() != null) {
            existing.setDescription(request.description());
        }
        if (request.baseUri() != null) {
            existing.setBaseUri(normalizeBaseUri(request.baseUri()));
        }
        if (request.metadata() != null) {
            existing.setMetadata(request.metadata());
        }

        try {
            existing = projectRepository.save(existing);
        } catch (DataIntegrityViolationException e) {
            throw new PipelineValidationException(
                "A project with that name already exists for this user");
        }
        log.info("Updated project: id={} name={} by={}", existing.getId(), existing.getName(), user.id());
        return toDto(existing);
    }

    @Transactional
    public ProjectDto archive(UUID id, AuthUser user) {
        return setStatus(id, user, ProjectStatus.ARCHIVED, "archive");
    }

    @Transactional
    public ProjectDto unarchive(UUID id, AuthUser user) {
        return setStatus(id, user, ProjectStatus.ACTIVE, "unarchive");
    }

    /**
     * Hard-delete the project row. Does NOT cascade to dependents in other
     * services — they will be left with dangling {@code projectId} UUIDs.
     *
     * <p>TODO(Phase 7): before delete, fan out to dimension-service,
     * shacl-service, data-service, dimension-service/cubes, job-service,
     * triplestore-service and either refuse with 409 if any owned
     * resources reference this projectId, or cascade the delete.
     */
    @Transactional
    public void delete(UUID id, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity existing = findOrThrow(id);
        requireOwnerOrAdmin(existing, user, "delete");
        projectRepository.delete(existing);
        log.info("Deleted project: id={} name={} by={}", id, existing.getName(), user.id());
    }

    /** Read a single project. Strict — no public access, owner/admin only. */
    @Transactional(readOnly = true)
    public ProjectDto findById(UUID id, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity existing = findOrThrow(id);
        requireOwnerOrAdmin(existing, user, "read");
        return toDto(existing);
    }

    /**
     * List the caller's projects. Admins get their own list here — there is
     * no "all projects" endpoint because it would leak cross-tenant data.
     * A dedicated admin API can be added later if required.
     */
    @Transactional(readOnly = true)
    public List<ProjectDto> list(AuthUser user, ProjectStatus statusFilter) {
        requireAuthenticated(user);
        List<ProjectEntity> entities = statusFilter == null
            ? projectRepository.findByCreatedByOrderByUpdatedAtDesc(user.id())
            : projectRepository.findByCreatedByAndStatusOrderByUpdatedAtDesc(user.id(), statusFilter);
        return entities.stream().map(this::toDto).toList();
    }

    /**
     * Build a dashboard summary. Only the {@code pipelines} count is
     * populated in this pass because aggregating shapes, dimensions, cubes,
     * jobs and triplestores would require WebClient fan-out against
     * sibling services that do not yet expose a count-by-project endpoint.
     *
     * <p>TODO(Phase 1.1): add {@code GET /api/v1/<resource>/count?projectId=}
     * to shacl-service, data-service, dimension-service, job-service,
     * triplestore-service, then aggregate here via WebClient. Include
     * {@code lastActivity} from the job-service and {@code lastRelease}
     * from a future release-service once that ships.
     */
    @Transactional(readOnly = true)
    public ProjectSummaryDto summary(UUID id, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity existing = findOrThrow(id);
        requireOwnerOrAdmin(existing, user, "read");

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pipelines", pipelineRepository.countByProjectId(id));
        // counts.put("shapes", ...);        // TODO Phase 1.1 — cross-service WebClient fan-out
        // counts.put("dataSources", ...);
        // counts.put("dimensions", ...);
        // counts.put("cubes", ...);
        // counts.put("jobs", ...);
        // counts.put("triplestores", ...);

        return new ProjectSummaryDto(
            existing.getId(),
            existing.getName(),
            existing.getDescription(),
            existing.getStatus(),
            existing.getBaseUri(),
            existing.getCreatedAt(),
            existing.getUpdatedAt(),
            counts,
            existing.getUpdatedAt(), // best-effort lastActivity until Phase 1.1 wires cross-service
            null                     // lastRelease — Phase 7 release-service
        );
    }

    // ────────────────────────── internal helpers ──────────────────────────

    private ProjectDto setStatus(UUID id, AuthUser user, ProjectStatus status, String action) {
        requireAuthenticated(user);
        ProjectEntity existing = findOrThrow(id);
        requireOwnerOrAdmin(existing, user, action);
        if (existing.getStatus() == status) {
            // No-op, but log for traceability so repeated calls are observable.
            log.debug("Project {} already in status {}, skipping {}", id, status, action);
            return toDto(existing);
        }
        existing.setStatus(status);
        existing = projectRepository.save(existing);
        log.info("Project {} {} by {}", id, action, user.id());
        return toDto(existing);
    }

    private ProjectEntity findOrThrow(UUID id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
    }

    /**
     * Ownership predicate: the project creator or an admin may act. We do
     * NOT fall back to allowing anyone with knowledge of the UUID because
     * the projects surface is private by design in this phase.
     */
    private static void requireOwnerOrAdmin(ProjectEntity project, AuthUser user, String action) {
        if (user.isAdmin()) return;
        if (!Objects.equals(project.getCreatedBy(), user.id())) {
            throw new AccessDeniedException("Not authorized to " + action + " this project");
        }
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            // @CurrentUser normally raises 401 before we see this, but defend in depth
            // in case the service is invoked directly (CLI, future batch runner).
            throw new AccessDeniedException("Authentication required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new PipelineValidationException("Project name is required");
        }
        if (name.length() > 255) {
            throw new PipelineValidationException("Project name must not exceed 255 characters");
        }
    }

    /**
     * Ensure {@code baseUri} parses as a URI with scheme + authority and
     * terminates with a slash so downstream URI minting can concatenate
     * path segments without duplicating the separator.
     */
    private static String normalizeBaseUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PipelineValidationException("Base URI is required");
        }
        String trimmed = raw.trim();
        try {
            URI uri = new URI(trimmed);
            if (uri.getScheme() == null || uri.getScheme().isBlank()) {
                throw new PipelineValidationException("Base URI must include a scheme (e.g. https://)");
            }
            if (uri.getHost() == null && uri.getAuthority() == null) {
                throw new PipelineValidationException("Base URI must include an authority");
            }
        } catch (URISyntaxException e) {
            throw new PipelineValidationException("Invalid Base URI: " + e.getMessage());
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private ProjectDto toDto(ProjectEntity entity) {
        Map<String, Object> metadata = entity.getMetadata() == null
            ? null
            : new HashMap<>(entity.getMetadata());
        return new ProjectDto(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getBaseUri(),
            entity.getStatus(),
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            metadata
        );
    }
}
