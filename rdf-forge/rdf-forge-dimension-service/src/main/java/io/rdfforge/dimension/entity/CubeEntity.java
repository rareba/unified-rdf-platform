package io.rdfforge.dimension.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cubes")
public class CubeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 500)
    private String uri;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_data_id")
    private UUID sourceDataId;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(name = "shape_id")
    private UUID shapeId;

    @Column(name = "triplestore_id")
    private UUID triplestoreId;

    @Column(name = "graph_uri", length = 500)
    private String graphUri;

    @Column(name = "observation_count")
    private Long observationCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "last_published")
    private Instant lastPublished;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getSourceDataId() { return sourceDataId; }
    public void setSourceDataId(UUID sourceDataId) { this.sourceDataId = sourceDataId; }

    public UUID getPipelineId() { return pipelineId; }
    public void setPipelineId(UUID pipelineId) { this.pipelineId = pipelineId; }

    public UUID getShapeId() { return shapeId; }
    public void setShapeId(UUID shapeId) { this.shapeId = shapeId; }

    public UUID getTriplestoreId() { return triplestoreId; }
    public void setTriplestoreId(UUID triplestoreId) { this.triplestoreId = triplestoreId; }

    public String getGraphUri() { return graphUri; }
    public void setGraphUri(String graphUri) { this.graphUri = graphUri; }

    public Long getObservationCount() { return observationCount; }
    public void setObservationCount(Long observationCount) { this.observationCount = observationCount; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Instant getLastPublished() { return lastPublished; }
    public void setLastPublished(Instant lastPublished) { this.lastPublished = lastPublished; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
