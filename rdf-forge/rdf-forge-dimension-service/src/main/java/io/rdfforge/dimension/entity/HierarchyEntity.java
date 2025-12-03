package io.rdfforge.dimension.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "hierarchies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"dimension_id", "uri"})
})
public class HierarchyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dimension_id", nullable = false)
    private UUID dimensionId;

    @Column(nullable = false, length = 500)
    private String uri;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "hierarchy_type", nullable = false)
    private HierarchyScheme hierarchyType = HierarchyScheme.SKOS_CONCEPT_SCHEME;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @Column(name = "root_concept_uri", length = 500)
    private String rootConceptUri;

    @Column(name = "skos_content", columnDefinition = "TEXT")
    private String skosContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;
    
    @Column(name = "is_default")
    private Boolean isDefault = false;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public enum HierarchyScheme {
        SKOS_CONCEPT_SCHEME,
        SKOS_COLLECTION,
        XKOS_CLASSIFICATION,
        CUSTOM
    }
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getDimensionId() { return dimensionId; }
    public void setDimensionId(UUID dimensionId) { this.dimensionId = dimensionId; }
    
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public HierarchyScheme getHierarchyType() { return hierarchyType; }
    public void setHierarchyType(HierarchyScheme hierarchyType) { this.hierarchyType = hierarchyType; }
    
    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }
    
    public String getRootConceptUri() { return rootConceptUri; }
    public void setRootConceptUri(String rootConceptUri) { this.rootConceptUri = rootConceptUri; }
    
    public String getSkosContent() { return skosContent; }
    public void setSkosContent(String skosContent) { this.skosContent = skosContent; }
    
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    
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
