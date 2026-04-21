package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.ReleaseEntity;
import io.rdfforge.pipeline.entity.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link ReleaseEntity}. All queries are project-scoped
 * and authorization is enforced at the service layer via the owner of the
 * parent {@code ProjectEntity}.
 */
@Repository
public interface ReleaseRepository extends JpaRepository<ReleaseEntity, UUID> {

    /** Releases belonging to a project, newest first. */
    List<ReleaseEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /** Filter by status, newest first. */
    List<ReleaseEntity> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, ReleaseStatus status);

    /** Enforced at the service layer; mirrors the DB unique constraint. */
    boolean existsByProjectIdAndVersion(UUID projectId, String version);

    /** Lookup by (projectId, version) — useful for idempotent builders. */
    Optional<ReleaseEntity> findByProjectIdAndVersion(UUID projectId, String version);

    /** Total releases per project — used by dashboard counts. */
    long countByProjectId(UUID projectId);
}
