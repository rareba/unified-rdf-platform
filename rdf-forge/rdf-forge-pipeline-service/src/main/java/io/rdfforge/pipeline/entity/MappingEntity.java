package io.rdfforge.pipeline.entity;

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
import java.util.Map;
import java.util.UUID;

/**
 * A mapping describes how rows from a source (CSV/TSV/JSON/XML/XLSX) are
 * translated into RDF triples. A mapping always belongs to a single project.
 *
 * <p>{@code (projectId, name)} is UNIQUE — within a project, names must be
 * distinct so the UI can address mappings by name where convenient.
 *
 * <p>{@code sourceConfig}, {@code targetOntologies}, and {@code rules} are all
 * stored as jsonb. This keeps the mapping shape evolving cheaply without new
 * Flyway migrations for each tweak to the rule model.
 *
 * <p>The {@link MappingType} field distinguishes generic mappings from the
 * pre-populated cube template — the CUBE mapping is a convenience layer on
 * top of the generic executor so the existing cube flow continues to work
 * unchanged.
 */
@Entity
@Table(
    name = "mappings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_mappings_project_name",
        columnNames = {"project_id", "name"}
    ),
    indexes = {
        @Index(name = "idx_mappings_project_id", columnList = "project_id"),
        @Index(name = "idx_mappings_type", columnList = "mapping_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_config", columnDefinition = "jsonb")
    private Map<String, Object> sourceConfig;

    @Column(name = "target_namespace", length = 1000)
    private String targetNamespace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_ontologies", columnDefinition = "jsonb")
    private Map<String, Object> targetOntologies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_rules", columnDefinition = "jsonb")
    private List<MappingRule> rules;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 16)
    private MappingType mappingType;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (mappingType == null) mappingType = MappingType.GENERIC;
        if (version <= 0) version = 1;
        if (rules == null) rules = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
