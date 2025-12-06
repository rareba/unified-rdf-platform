package io.rdfforge.pipeline.gitsync.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.pipeline.gitsync.client.GitClient;
import io.rdfforge.pipeline.gitsync.entity.GitSyncConfigEntity;
import io.rdfforge.pipeline.gitsync.model.*;
import io.rdfforge.pipeline.gitsync.repository.GitSyncConfigRepository;
import io.rdfforge.pipeline.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for synchronizing configurations with Git repositories.
 * Supports both push (export to Git) and pull (import from Git) operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitSyncService {

    private final GitSyncConfigRepository configRepository;
    private final ConfigExportService exportService;
    private final PipelineService pipelineService;

    private final Map<String, GitSyncStatus> syncStatuses = new ConcurrentHashMap<>();

    // === Configuration Management ===

    @Transactional
    public GitSyncConfig createConfig(GitSyncConfig config) {
        GitSyncConfigEntity entity = toEntity(config);
        entity = configRepository.save(entity);
        log.info("Created Git sync config: {} -> {}", entity.getName(), entity.getRepositoryUrl());
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public GitSyncConfig getConfig(UUID id) {
        return configRepository.findById(id)
            .map(this::toModel)
            .orElseThrow(() -> new ResourceNotFoundException("GitSyncConfig", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<GitSyncConfig> listConfigs(UUID projectId) {
        return configRepository.findByProjectId(projectId).stream()
            .map(this::toModel)
            .toList();
    }

    @Transactional
    public GitSyncConfig updateConfig(UUID id, GitSyncConfig config) {
        GitSyncConfigEntity entity = configRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GitSyncConfig", id.toString()));

        entity.setName(config.getName());
        entity.setProvider(config.getProvider());
        entity.setRepositoryUrl(config.getRepositoryUrl());
        entity.setBranch(config.getBranch());
        if (config.getAccessToken() != null && !config.getAccessToken().isEmpty()) {
            entity.setAccessToken(config.getAccessToken());
        }
        entity.setConfigPath(config.getConfigPath());
        entity.setSyncPipelines(config.isSyncPipelines());
        entity.setSyncShapes(config.isSyncShapes());
        entity.setSyncSettings(config.isSyncSettings());
        entity.setAutoSync(config.isAutoSync());
        entity.setSyncIntervalMinutes(config.getSyncIntervalMinutes());

        entity = configRepository.save(entity);
        return toModel(entity);
    }

    @Transactional
    public void deleteConfig(UUID id) {
        if (!configRepository.existsById(id)) {
            throw new ResourceNotFoundException("GitSyncConfig", id.toString());
        }
        configRepository.deleteById(id);
        log.info("Deleted Git sync config: {}", id);
    }

    // === Sync Operations ===

    /**
     * Test connection to Git repository.
     */
    public boolean testConnection(GitSyncConfig config) {
        try {
            GitClient client = GitClient.create(config);
            return client.isAccessible();
        } catch (Exception e) {
            log.error("Connection test failed for {}", config.getRepositoryUrl(), e);
            return false;
        }
    }

    /**
     * Push configurations to Git repository.
     */
    @Transactional
    public GitSyncStatus pushToGit(UUID configId, String commitMessage) {
        GitSyncConfigEntity configEntity = configRepository.findById(configId)
            .orElseThrow(() -> new ResourceNotFoundException("GitSyncConfig", configId.toString()));

        GitSyncConfig config = toModel(configEntity);
        String operationId = UUID.randomUUID().toString();

        GitSyncStatus status = GitSyncStatus.builder()
            .operationId(operationId)
            .state(GitSyncStatus.SyncState.IN_PROGRESS)
            .direction(GitSyncStatus.SyncDirection.PUSH)
            .startedAt(Instant.now())
            .syncedFiles(new ArrayList<>())
            .errors(new ArrayList<>())
            .warnings(new ArrayList<>())
            .build();

        syncStatuses.put(operationId, status);

        try {
            GitClient client = GitClient.create(config);

            // Export configurations
            Map<String, String> files = exportService.exportAll(config.getProjectId(), config.getConfigPath());

            // Determine which files are new/updated
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Optional<GitClient.FileContent> existing = client.getFile(entry.getKey());
                GitSyncStatus.SyncedFile.FileAction action = existing.isPresent()
                    ? GitSyncStatus.SyncedFile.FileAction.UPDATED
                    : GitSyncStatus.SyncedFile.FileAction.CREATED;

                status.getSyncedFiles().add(GitSyncStatus.SyncedFile.builder()
                    .path(entry.getKey())
                    .type(determineFileType(entry.getKey()))
                    .action(action)
                    .build());
            }

            // Commit all files
            if (commitMessage == null || commitMessage.isEmpty()) {
                commitMessage = "Sync configurations from RDF Forge";
            }

            String commitSha = client.commitFiles(files, commitMessage);

            // Update config with last sync info
            configEntity.setLastSyncAt(Instant.now());
            configEntity.setLastCommitSha(commitSha);
            configRepository.save(configEntity);

            status.setState(GitSyncStatus.SyncState.COMPLETED);
            status.setCommitSha(commitSha);
            status.setCommitMessage(commitMessage);
            status.setCompletedAt(Instant.now());

            log.info("Pushed {} files to Git repository {}", files.size(), config.getRepositoryUrl());

        } catch (Exception e) {
            log.error("Failed to push to Git", e);
            status.setState(GitSyncStatus.SyncState.FAILED);
            status.getErrors().add(e.getMessage());
            status.setCompletedAt(Instant.now());
        }

        syncStatuses.put(operationId, status);
        return status;
    }

    /**
     * Pull configurations from Git repository.
     */
    @Transactional
    public GitSyncStatus pullFromGit(UUID configId, boolean dryRun) {
        GitSyncConfigEntity configEntity = configRepository.findById(configId)
            .orElseThrow(() -> new ResourceNotFoundException("GitSyncConfig", configId.toString()));

        GitSyncConfig config = toModel(configEntity);
        String operationId = UUID.randomUUID().toString();

        GitSyncStatus status = GitSyncStatus.builder()
            .operationId(operationId)
            .state(GitSyncStatus.SyncState.IN_PROGRESS)
            .direction(GitSyncStatus.SyncDirection.PULL)
            .startedAt(Instant.now())
            .syncedFiles(new ArrayList<>())
            .errors(new ArrayList<>())
            .warnings(new ArrayList<>())
            .build();

        syncStatuses.put(operationId, status);

        try {
            GitClient client = GitClient.create(config);

            // Get list of files from repository
            String pipelinesPath = config.getConfigPath() + "/pipelines";
            List<GitClient.FileEntry> pipelineFiles = client.listFiles(pipelinesPath);

            for (GitClient.FileEntry file : pipelineFiles) {
                if (!"file".equals(file.type()) || !file.name().endsWith(".yaml")) {
                    continue;
                }

                try {
                    Optional<GitClient.FileContent> content = client.getFile(file.path());
                    if (content.isPresent()) {
                        Pipeline imported = exportService.importPipeline(content.get().content(), config.getProjectId());

                        GitSyncStatus.SyncedFile.FileAction action;
                        if (imported.getId() != null) {
                            // Try to find existing pipeline
                            try {
                                Pipeline existing = pipelineService.getById(imported.getId());
                                if (!dryRun) {
                                    pipelineService.update(imported.getId(), imported);
                                }
                                action = GitSyncStatus.SyncedFile.FileAction.UPDATED;
                            } catch (ResourceNotFoundException e) {
                                // Pipeline with this ID doesn't exist, create new
                                if (!dryRun) {
                                    pipelineService.create(imported);
                                }
                                action = GitSyncStatus.SyncedFile.FileAction.CREATED;
                            }
                        } else {
                            if (!dryRun) {
                                pipelineService.create(imported);
                            }
                            action = GitSyncStatus.SyncedFile.FileAction.CREATED;
                        }

                        status.getSyncedFiles().add(GitSyncStatus.SyncedFile.builder()
                            .path(file.path())
                            .type("pipeline")
                            .action(action)
                            .resourceName(imported.getName())
                            .resourceId(imported.getId() != null ? imported.getId().toString() : null)
                            .build());
                    }
                } catch (Exception e) {
                    log.error("Failed to import pipeline from {}", file.path(), e);
                    status.getErrors().add("Failed to import " + file.path() + ": " + e.getMessage());
                }
            }

            // Update config with last sync info
            if (!dryRun) {
                configEntity.setLastSyncAt(Instant.now());
                configEntity.setLastCommitSha(client.getLatestCommit());
                configRepository.save(configEntity);
            }

            status.setState(GitSyncStatus.SyncState.COMPLETED);
            status.setCommitSha(client.getLatestCommit());
            status.setCompletedAt(Instant.now());

            log.info("Pulled {} files from Git repository {}", status.getSyncedFiles().size(), config.getRepositoryUrl());

        } catch (Exception e) {
            log.error("Failed to pull from Git", e);
            status.setState(GitSyncStatus.SyncState.FAILED);
            status.getErrors().add(e.getMessage());
            status.setCompletedAt(Instant.now());
        }

        syncStatuses.put(operationId, status);
        return status;
    }

    /**
     * Get status of a sync operation.
     */
    public Optional<GitSyncStatus> getSyncStatus(String operationId) {
        return Optional.ofNullable(syncStatuses.get(operationId));
    }

    /**
     * Scheduled task to sync configs with autoSync enabled.
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void autoSync() {
        List<GitSyncConfigEntity> autoSyncConfigs = configRepository.findAutoSyncEnabled();

        for (GitSyncConfigEntity config : autoSyncConfigs) {
            if (config.getSyncIntervalMinutes() == null || config.getSyncIntervalMinutes() <= 0) {
                continue;
            }

            Instant lastSync = config.getLastSyncAt();
            if (lastSync == null || Instant.now().isAfter(
                lastSync.plusSeconds(config.getSyncIntervalMinutes() * 60L))) {
                try {
                    log.info("Auto-syncing config: {}", config.getName());
                    pullFromGit(config.getId(), false);
                } catch (Exception e) {
                    log.error("Auto-sync failed for config: {}", config.getName(), e);
                }
            }
        }
    }

    // === Helper Methods ===

    private String determineFileType(String path) {
        if (path.contains("/pipelines/")) return "pipeline";
        if (path.contains("/shapes/")) return "shape";
        if (path.contains("/settings")) return "settings";
        if (path.endsWith("manifest.yaml")) return "manifest";
        return "unknown";
    }

    private GitSyncConfigEntity toEntity(GitSyncConfig model) {
        return GitSyncConfigEntity.builder()
            .id(model.getId())
            .name(model.getName())
            .projectId(model.getProjectId())
            .provider(model.getProvider())
            .repositoryUrl(model.getRepositoryUrl())
            .branch(model.getBranch())
            .accessToken(model.getAccessToken())
            .configPath(model.getConfigPath())
            .syncPipelines(model.isSyncPipelines())
            .syncShapes(model.isSyncShapes())
            .syncSettings(model.isSyncSettings())
            .autoSync(model.isAutoSync())
            .syncIntervalMinutes(model.getSyncIntervalMinutes())
            .build();
    }

    private GitSyncConfig toModel(GitSyncConfigEntity entity) {
        return GitSyncConfig.builder()
            .id(entity.getId())
            .name(entity.getName())
            .projectId(entity.getProjectId())
            .provider(entity.getProvider())
            .repositoryUrl(entity.getRepositoryUrl())
            .branch(entity.getBranch())
            .accessToken(null) // Don't expose token in responses
            .configPath(entity.getConfigPath())
            .syncPipelines(entity.getSyncPipelines())
            .syncShapes(entity.getSyncShapes())
            .syncSettings(entity.getSyncSettings())
            .autoSync(entity.getAutoSync())
            .syncIntervalMinutes(entity.getSyncIntervalMinutes())
            .build();
    }
}
