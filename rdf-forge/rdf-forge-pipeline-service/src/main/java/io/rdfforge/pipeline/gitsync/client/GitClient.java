package io.rdfforge.pipeline.gitsync.client;

import io.rdfforge.pipeline.gitsync.model.GitSyncConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for Git provider clients.
 * Supports both GitHub and GitLab APIs.
 */
public interface GitClient {

    /**
     * Get a file's content from the repository.
     *
     * @param path File path relative to repository root
     * @return File content if exists, empty otherwise
     */
    Optional<FileContent> getFile(String path);

    /**
     * List files in a directory.
     *
     * @param path Directory path
     * @return List of file entries
     */
    List<FileEntry> listFiles(String path);

    /**
     * Create or update a file in the repository.
     *
     * @param path File path
     * @param content File content
     * @param message Commit message
     * @return Commit SHA
     */
    String createOrUpdateFile(String path, String content, String message);

    /**
     * Create or update multiple files in a single commit.
     *
     * @param files Map of path to content
     * @param message Commit message
     * @return Commit SHA
     */
    String commitFiles(Map<String, String> files, String message);

    /**
     * Delete a file from the repository.
     *
     * @param path File path
     * @param message Commit message
     * @return Commit SHA
     */
    String deleteFile(String path, String message);

    /**
     * Get the latest commit SHA for the configured branch.
     *
     * @return Commit SHA
     */
    String getLatestCommit();

    /**
     * Check if the repository is accessible.
     *
     * @return true if accessible
     */
    boolean isAccessible();

    /**
     * Get repository information.
     *
     * @return Repository info
     */
    RepositoryInfo getRepositoryInfo();

    /**
     * File content with metadata.
     */
    record FileContent(String path, String content, String sha, String encoding) {}

    /**
     * File entry in directory listing.
     */
    record FileEntry(String name, String path, String type, String sha) {}

    /**
     * Repository information.
     */
    record RepositoryInfo(String name, String fullName, String defaultBranch, String url) {}

    /**
     * Factory method to create a GitClient for the given configuration.
     */
    static GitClient create(GitSyncConfig config) {
        return switch (config.getProvider()) {
            case GITHUB -> new GitHubClient(config);
            case GITLAB -> new GitLabClient(config);
        };
    }
}
