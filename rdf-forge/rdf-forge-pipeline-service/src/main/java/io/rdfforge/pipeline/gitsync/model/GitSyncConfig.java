package io.rdfforge.pipeline.gitsync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Configuration for Git repository synchronization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitSyncConfig {
    private UUID id;
    private String name;
    private GitProvider provider;
    private String repositoryUrl;
    private String branch;
    private String accessToken;
    private String configPath;
    private boolean syncPipelines;
    private boolean syncShapes;
    private boolean syncSettings;
    private boolean autoSync;
    private Integer syncIntervalMinutes;
    private UUID projectId;
}
