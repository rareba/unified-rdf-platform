package io.rdfforge.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "pipelines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "definition_format")
    private String definitionFormat;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> variables;

    // @JdbcTypeCode(SqlTypes.JSON)
    // @Column(columnDefinition = "jsonb")
    // private List<String> tags;

    @Column
    private Integer version;

    @Column(name = "is_template")
    private Boolean isTemplate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (version == null) version = 1;
        if (isTemplate == null) isTemplate = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
