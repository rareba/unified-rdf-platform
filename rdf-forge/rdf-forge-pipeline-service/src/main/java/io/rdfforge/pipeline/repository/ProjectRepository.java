package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link ProjectEntity}.
 *
 * <p>All queries are scoped by {@code createdBy} because ownership is the
 * primary authorization boundary. Admin bypass is enforced at the service
 * layer, not here, so that the repository stays simple and reusable.
 */
@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    /**
     * Owner-scoped lookup. Returns {@link Optional#empty()} if the project
     * does not exist OR if it exists but belongs to another user. The caller
     * must distinguish 404 from 403 only when admin access is possible —
     * that logic lives in the service.
     */
    Optional<ProjectEntity> findByIdAndCreatedBy(UUID id, UUID createdBy);

    /** List all of a user's projects, most recently updated first. */
    List<ProjectEntity> findByCreatedByOrderByUpdatedAtDesc(UUID createdBy);

    /** List a user's projects filtered by lifecycle status, newest activity first. */
    List<ProjectEntity> findByCreatedByAndStatusOrderByUpdatedAtDesc(UUID createdBy, ProjectStatus status);

    /** Used for unique-name enforcement before insert. */
    boolean existsByCreatedByAndName(UUID createdBy, String name);
}
