package io.rdfforge.dimension.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dimensions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "uri"})
})
public class DimensionEntity {
    
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private DimensionType type = DimensionType.KEY;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "hierarchy_type")
    private HierarchyType hierarchyType = HierarchyType.FLAT;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "base_uri", length = 500)
    private String baseUri;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> metadata;
    
    @Column(nullable = false)
    private Integer version = 1;
    
    @Column(name = "value_count")
    private Long valueCount = 0L;
    
    @Column(name = "is_shared")
    private Boolean isShared = false;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public enum DimensionType {
        TEMPORAL, GEO, KEY, MEASURE, ATTRIBUTE, CODED
    }
    
    public enum HierarchyType {
        SKOS, CUSTOM, FLAT
    }
    
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
    
    public DimensionType getType() { return type; }
    public void setType(DimensionType type) { this.type = type; }
    
    public HierarchyType getHierarchyType() { return hierarchyType; }
    public void setHierarchyType(HierarchyType hierarchyType) { this.hierarchyType = hierarchyType; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getBaseUri() { return baseUri; }
    public void setBaseUri(String baseUri) { this.baseUri = baseUri; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    
    public Long getValueCount() { return valueCount; }
    public void setValueCount(Long valueCount) { this.valueCount = valueCount; }
    
    public Boolean getIsShared() { return isShared; }
    public void setIsShared(Boolean isShared) { this.isShared = isShared; }
    
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
