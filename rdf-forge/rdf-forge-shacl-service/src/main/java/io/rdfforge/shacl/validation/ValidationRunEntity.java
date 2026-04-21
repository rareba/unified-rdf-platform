package io.rdfforge.shacl.validation;

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
 * History record for a single execution of a {@link ValidationSuiteEntity}.
 * Aggregate counts are materialised on the row so the cockpit can render
 * summary badges without joining against {@link ValidationIssueEntity}.
 */
@Entity
@Table(
    name = "validation_runs",
    indexes = {
        @Index(name = "idx_validation_runs_project_ran_at",
            columnList = "project_id, ran_at DESC"),
        @Index(name = "idx_validation_runs_suite_ran_at",
            columnList = "suite_id, ran_at DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suite_id", nullable = false)
    private UUID suiteId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    @Column(name = "duration_ms")
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValidationStatus status;

    @Column(name = "issue_count")
    private int issueCount;

    @Column(name = "error_count")
    private int errorCount;

    @Column(name = "warning_count")
    private int warningCount;

    @Column(name = "info_count")
    private int infoCount;

    @Column(name = "fatal_count")
    private int fatalCount;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "ran_by")
    private UUID ranBy;

    @PrePersist
    protected void onCreate() {
        if (ranAt == null) ranAt = Instant.now();
        if (context == null) context = new HashMap<>();
    }
}
