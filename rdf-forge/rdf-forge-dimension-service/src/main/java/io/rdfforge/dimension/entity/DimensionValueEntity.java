package io.rdfforge.dimension.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dimension_values", indexes = {
    @Index(name = "idx_dim_values_dimension", columnList = "dimension_id"),
    @Index(name = "idx_dim_values_code", columnList = "code"),
    @Index(name = "idx_dim_values_uri", columnList = "uri")
})
public class DimensionValueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dimension_id", nullable = false)
    private UUID dimensionId;

    @Column(nullable = false, length = 500)
    private String uri;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "label_lang", length = 10)
    private String labelLang = "en";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "hierarchy_level")
    private Integer hierarchyLevel = 0;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alt_labels", columnDefinition = "jsonb")
    private Map<String, String> altLabels;
    
    @Column(name = "skos_notation", length = 100)
    private String skosNotation;
    
    @Column(name = "is_deprecated")
    private Boolean isDeprecated = false;
    
    @Column(name = "replaced_by", length = 500)
    private String replacedBy;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getDimensionId() { return dimensionId; }
    public void setDimensionId(UUID dimensionId) { this.dimensionId = dimensionId; }
    
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public String getLabelLang() { return labelLang; }
    public void setLabelLang(String labelLang) { this.labelLang = labelLang; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    
    public Integer getHierarchyLevel() { return hierarchyLevel; }
    public void setHierarchyLevel(Integer hierarchyLevel) { this.hierarchyLevel = hierarchyLevel; }
    
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Map<String, String> getAltLabels() { return altLabels; }
    public void setAltLabels(Map<String, String> altLabels) { this.altLabels = altLabels; }
    
    public String getSkosNotation() { return skosNotation; }
    public void setSkosNotation(String skosNotation) { this.skosNotation = skosNotation; }
    
    public Boolean getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(Boolean isDeprecated) { this.isDeprecated = isDeprecated; }
    
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
