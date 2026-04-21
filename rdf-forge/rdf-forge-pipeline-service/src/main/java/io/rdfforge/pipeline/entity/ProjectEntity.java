package io.rdfforge.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A Project is a user's workspace. It groups the data sources, pipelines,
 * shapes, dimensions, cubes and triplestore connections that belong to a
 * single initiative. Other entities reference the project by opaque
 * {@code projectId} UUID — there are no cross-schema FK constraints so each
 * service remains free to manage its own schema lifecycle.
 *
 * <p>Ownership is enforced via {@code createdBy} paired with the
 * {@code AuthUser} resolved from gateway headers. The
 * {@code (createdBy, name)} unique constraint prevents a single user
 * from creating two projects with the same name, but different users may
 * reuse the same name.
 */
@Entity
@Table(
    name = "projects",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_projects_created_by_name",
        columnNames = {"created_by", "name"}
    ),
    indexes = {
        @Index(name = "idx_projects_created_by", columnList = "created_by"),
        @Index(name = "idx_projects_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_uri", nullable = false, length = 1000)
    private String baseUri;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = ProjectStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
