package io.rdfforge.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Release is an immutable versioned snapshot of a Project's semantic
 * assets — data sources, mappings, shapes, ontologies, plus a manifest that
 * records validation gate status and any target triplestore. When built, the
 * release produces a zip bundle suitable for sharing or redeployment.
 *
 * <p>{@code (projectId, version)} is UNIQUE — a single version string cannot
 * be reused within a project. {@code version} is a SemVer string (validated
 * at service level) stored as-is so tools can parse it with their own logic.
 *
 * <p>{@code manifest} is stored as jsonb. Shape is deliberately flexible so
 * we can evolve provenance/lineage annotations without migrations.
 */
@Entity
@Table(
    name = "releases",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_releases_project_version",
        columnNames = {"project_id", "version"}
    ),
    indexes = {
        @Index(name = "idx_releases_project_id", columnList = "project_id"),
        @Index(name = "idx_releases_status", columnList = "status"),
        @Index(name = "idx_releases_published_at", columnList = "published_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Semantic version string (e.g. "1.2.3", "2.0.0-rc.1"). Service validates. */
    @Column(nullable = false, length = 64)
    private String version;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReleaseStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** URI/path of the built artifact zip. Null until build completes. */
    @Column(name = "artifact_uri", length = 2000)
    private String artifactUri;

    /** Size of the built artifact in bytes. 0 until build completes. */
    @Column(name = "artifact_size_bytes", nullable = false)
    private long artifactSizeBytes;

    /**
     * Computed asset manifest (jsonb). Contains the list of data source ids,
     * mapping ids, shape ids, ontology ids, triplestore target, validation
     * suite refs and gate result. See ReleaseService for exact keys.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> manifest;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = ReleaseStatus.DRAFT;
        if (manifest == null) manifest = new HashMap<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
