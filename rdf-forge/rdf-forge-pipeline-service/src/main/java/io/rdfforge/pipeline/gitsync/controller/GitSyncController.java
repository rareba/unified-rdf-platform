package io.rdfforge.pipeline.gitsync.controller;

import io.rdfforge.pipeline.gitsync.model.GitProvider;
import io.rdfforge.pipeline.gitsync.model.GitSyncConfig;
import io.rdfforge.pipeline.gitsync.model.GitSyncStatus;
import io.rdfforge.pipeline.gitsync.service.GitSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Git sync operations.
 * Provides endpoints for managing Git repository connections and syncing configurations.
 */
@RestController
@RequestMapping("/api/v1/git-sync")
@RequiredArgsConstructor
public class GitSyncController {

    private final GitSyncService gitSyncService;

    // === Configuration Management ===

    /**
     * Create a new Git sync configuration.
     */
    @PostMapping("/configs")
    public ResponseEntity<GitSyncConfig> createConfig(@RequestBody GitSyncConfigRequest request) {
        GitSyncConfig config = GitSyncConfig.builder()
            .name(request.name())
            .projectId(request.projectId())
            .provider(request.provider())
            .repositoryUrl(request.repositoryUrl())
            .branch(request.branch() != null ? request.branch() : "main")
            .accessToken(request.accessToken())
            .configPath(request.configPath() != null ? request.configPath() : "config")
            .syncPipelines(request.syncPipelines() != null ? request.syncPipelines() : true)
            .syncShapes(request.syncShapes() != null ? request.syncShapes() : true)
            .syncSettings(request.syncSettings() != null ? request.syncSettings() : true)
            .autoSync(request.autoSync() != null ? request.autoSync() : false)
            .syncIntervalMinutes(request.syncIntervalMinutes())
            .build();

        GitSyncConfig created = gitSyncService.createConfig(config);
        return ResponseEntity.ok(created);
    }

    /**
     * Get a Git sync configuration by ID.
     */
    @GetMapping("/configs/{id}")
    public ResponseEntity<GitSyncConfig> getConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(gitSyncService.getConfig(id));
    }

    /**
     * List all Git sync configurations for a project.
     */
    @GetMapping("/configs")
    public ResponseEntity<List<GitSyncConfig>> listConfigs(
            @RequestParam(required = false) UUID projectId) {
        return ResponseEntity.ok(gitSyncService.listConfigs(projectId));
    }

    /**
     * Update a Git sync configuration.
     */
    @PutMapping("/configs/{id}")
    public ResponseEntity<GitSyncConfig> updateConfig(
            @PathVariable UUID id,
            @RequestBody GitSyncConfigRequest request) {
        GitSyncConfig config = GitSyncConfig.builder()
            .name(request.name())
            .provider(request.provider())
            .repositoryUrl(request.repositoryUrl())
            .branch(request.branch())
            .accessToken(request.accessToken())
            .configPath(request.configPath())
            .syncPipelines(request.syncPipelines())
            .syncShapes(request.syncShapes())
            .syncSettings(request.syncSettings())
            .autoSync(request.autoSync())
            .syncIntervalMinutes(request.syncIntervalMinutes())
            .build();

        return ResponseEntity.ok(gitSyncService.updateConfig(id, config));
    }

    /**
     * Delete a Git sync configuration.
     */
    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID id) {
        gitSyncService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test connection to a Git repository.
     */
    @PostMapping("/test-connection")
    public ResponseEntity<ConnectionTestResult> testConnection(@RequestBody GitSyncConfigRequest request) {
        GitSyncConfig config = GitSyncConfig.builder()
            .provider(request.provider())
            .repositoryUrl(request.repositoryUrl())
            .branch(request.branch() != null ? request.branch() : "main")
            .accessToken(request.accessToken())
            .build();

        boolean connected = gitSyncService.testConnection(config);
        return ResponseEntity.ok(new ConnectionTestResult(connected,
            connected ? "Connection successful" : "Connection failed"));
    }

    // === Sync Operations ===

    /**
     * Push configurations to Git repository.
     */
    @PostMapping("/configs/{id}/push")
    public ResponseEntity<GitSyncStatus> pushToGit(
            @PathVariable UUID id,
            @RequestBody(required = false) PushRequest request) {
        String message = request != null ? request.commitMessage() : null;
        GitSyncStatus status = gitSyncService.pushToGit(id, message);
        return ResponseEntity.ok(status);
    }

    /**
     * Pull configurations from Git repository.
     */
    @PostMapping("/configs/{id}/pull")
    public ResponseEntity<GitSyncStatus> pullFromGit(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        GitSyncStatus status = gitSyncService.pullFromGit(id, dryRun);
        return ResponseEntity.ok(status);
    }

    /**
     * Get status of a sync operation.
     */
    @GetMapping("/status/{operationId}")
    public ResponseEntity<GitSyncStatus> getSyncStatus(@PathVariable String operationId) {
        return gitSyncService.getSyncStatus(operationId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // === DTOs ===

    public record GitSyncConfigRequest(
        String name,
        UUID projectId,
        GitProvider provider,
        String repositoryUrl,
        String branch,
        String accessToken,
        String configPath,
        Boolean syncPipelines,
        Boolean syncShapes,
        Boolean syncSettings,
        Boolean autoSync,
        Integer syncIntervalMinutes
    ) {}

    public record PushRequest(String commitMessage) {}

    public record ConnectionTestResult(boolean connected, String message) {}
}
