package io.rdfforge.triplestore.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent model for a Saved SPARQL Query. See Phase 7 — SPARQL Workbench 2.0.
 *
 * <p>Parameter substitution at execution time MUST go through
 * {@code org.apache.jena.query.ParameterizedSparqlString} — see
 * {@link io.rdfforge.triplestore.service.SavedQueryService#substituteParameters}.
 * We DO NOT string-concat user-supplied values into the query text.
 */
@Entity
@Table(
    name = "saved_queries",
    uniqueConstraints = @UniqueConstraint(name = "saved_queries_project_name_unique", columnNames = {"project_id", "name"})
)
public class SavedQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QueryType type;

    @Lob
    @Column(name = "query_text", nullable = false)
    private String queryText;

    /** Map of paramName -> { type: 'uri'|'literal'|'string'|'number', default: '...' }. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    /** Free-form tags for filtering in the Workbench. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "run_count", nullable = false)
    private Integer runCount = 0;

    @Column(name = "last_run")
    private Instant lastRun;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (runCount == null) runCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum QueryType { ASK, SELECT, CONSTRUCT, DESCRIBE, UPDATE }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public QueryType getType() { return type; }
    public void setType(QueryType type) { this.type = type; }

    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }

    public Instant getLastRun() { return lastRun; }
    public void setLastRun(Instant lastRun) { this.lastRun = lastRun; }
}
