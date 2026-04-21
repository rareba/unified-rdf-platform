package io.rdfforge.shacl.validation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named, project-scoped collection of validation rules that combines
 * SHACL shapes, saved SPARQL ASK/SELECT rules and cube-link profile checks
 * into a single reusable "suite" that can be executed on demand (the user
 * triggers it manually) or automatically (as part of a release gate).
 *
 * <p>Rules are stored inline as a jsonb list so the shape of the suite can
 * evolve without DB migrations. The {@link ReleaseGate} controls how a suite
 * run is interpreted by the future release-factory (phase 6).
 */
@Entity
@Table(
    name = "validation_suites",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_validation_suites_project_name", columnNames = {"project_id", "name"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSuiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<SuiteRule> rules = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "release_gate", nullable = false, length = 32)
    @Builder.Default
    private ReleaseGate gate = ReleaseGate.FAIL_ON_ERROR;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (gate == null) gate = ReleaseGate.FAIL_ON_ERROR;
        if (rules == null) rules = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Rules are ordered; execution follows list order. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuiteRule {
        /** Local id within the suite (user-supplied or generated). */
        private String id;
        /** Human-readable label. */
        private String name;
        /** Kind of rule executed. */
        private RuleType type;
        /**
         * For SHACL_SHAPE: UUID (as string) of an existing Shape entity.
         * For SPARQL_ASK / SPARQL_SELECT: the inline query text.
         * For CUBE_PROFILE: the profile id (e.g. "standalone-cube-constraint").
         */
        private String resourceRef;
        /** Severity assigned to issues produced by this rule. */
        private ValidationSeverity severity;
    }

    public enum RuleType {
        SHACL_SHAPE,
        SPARQL_ASK,
        SPARQL_SELECT,
        CUBE_PROFILE
    }

    public enum ReleaseGate {
        DISABLED,
        WARN_ONLY,
        FAIL_ON_WARNING,
        FAIL_ON_ERROR,
        FAIL_ON_FATAL
    }
}
