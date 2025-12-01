package io.rdfforge.shacl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shapes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShapeEntity {
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

    @Column(name = "target_class", length = 500)
    private String targetClass;

    @Column(name = "content_format")
    private String contentFormat;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_template")
    private Boolean isTemplate;

    @Column
    private String category;

    @Column(name = "tags")
    private String[] tags;

    @Column
    private Integer version;

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
        if (contentFormat == null) contentFormat = "turtle";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
