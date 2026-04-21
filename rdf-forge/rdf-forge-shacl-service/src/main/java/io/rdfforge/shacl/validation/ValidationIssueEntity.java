package io.rdfforge.shacl.validation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single finding produced by a rule during a suite run. One row per finding
 * so the cockpit can drill down, filter by severity, and attach remediation
 * pointers ({@link #getSourcePath()} → mapping rule id).
 */
@Entity
@Table(
    name = "validation_issues",
    indexes = {
        @Index(name = "idx_validation_issues_run_severity",
            columnList = "run_id, severity"),
        @Index(name = "idx_validation_issues_run", columnList = "run_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    /** Matches the local {@code id} of the {@link ValidationSuiteEntity.SuiteRule}. */
    @Column(name = "rule_id", length = 255)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValidationSeverity severity;

    @Column(name = "resource_uri", length = 2000)
    private String resourceUri;

    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Optional drill-down pointer. Format is convention-based, e.g.
     * {@code mapping:<UUID>/rule:<ruleId>} or a SHACL sh:path string.
     */
    @Column(name = "source_path", length = 2000)
    private String sourcePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();
}
