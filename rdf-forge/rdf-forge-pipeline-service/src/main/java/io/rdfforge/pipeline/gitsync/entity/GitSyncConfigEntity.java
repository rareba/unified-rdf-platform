package io.rdfforge.pipeline.gitsync.entity;

import io.rdfforge.pipeline.gitsync.model.GitProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for storing Git sync configurations.
 */
@Entity
@Table(name = "git_sync_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitSyncConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GitProvider provider;

    @Column(name = "repository_url", nullable = false)
    private String repositoryUrl;

    @Column(nullable = false)
    private String branch;

    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @Column(name = "config_path")
    private String configPath;

    @Column(name = "sync_pipelines")
    private Boolean syncPipelines;

    @Column(name = "sync_shapes")
    private Boolean syncShapes;

    @Column(name = "sync_settings")
    private Boolean syncSettings;

    @Column(name = "auto_sync")
    private Boolean autoSync;

    @Column(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_commit_sha")
    private String lastCommitSha;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (syncPipelines == null) syncPipelines = true;
        if (syncShapes == null) syncShapes = true;
        if (syncSettings == null) syncSettings = true;
        if (autoSync == null) autoSync = false;
        if (branch == null) branch = "main";
        if (configPath == null) configPath = "config";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
