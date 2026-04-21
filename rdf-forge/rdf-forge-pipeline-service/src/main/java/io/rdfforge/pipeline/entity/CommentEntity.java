package io.rdfforge.pipeline.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Threaded comment on a semantic asset (ontology, shape, mapping, cube, …).
 *
 * <p>One comment table for the whole platform (see V9 migration rationale).
 * Each row denormalises {@code projectId} so project-scoped authz can be
 * enforced without contacting the owning service.
 */
@Entity
@Table(name = "comments", schema = "pipeline",
       indexes = {
           @Index(name = "idx_comments_asset", columnList = "asset_kind,asset_id"),
           @Index(name = "idx_comments_project", columnList = "project_id"),
           @Index(name = "idx_comments_parent", columnList = "parent_comment_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "asset_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private AssetKind assetKind;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_email", length = 255)
    private String authorEmail;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /**
     * The kind of asset a comment is attached to. New kinds extend the enum;
     * existing rows stay valid because the DB column is free-text VARCHAR(32).
     */
    public enum AssetKind {
        ONTOLOGY,
        SHAPE,
        MAPPING,
        CUBE,
        DIMENSION,
        VALIDATION_SUITE,
        RELEASE,
        PROJECT
    }
}
