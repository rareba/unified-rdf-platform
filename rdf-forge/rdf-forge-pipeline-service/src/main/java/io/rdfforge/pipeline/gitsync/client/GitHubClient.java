package io.rdfforge.pipeline.gitsync.client;

import io.rdfforge.pipeline.gitsync.model.GitSyncConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GitHub API client for Git sync operations.
 */
@Slf4j
public class GitHubClient implements GitClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GitSyncConfig config;
    private final String owner;
    private final String repo;

    public GitHubClient(GitSyncConfig config) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.config = config;

        // Parse repository URL to get owner and repo
        String[] parts = parseRepoUrl(config.getRepositoryUrl());
        this.owner = parts[0];
        this.repo = parts[1];
    }

    private String[] parseRepoUrl(String url) {
        // Handle various GitHub URL formats
        url = url.replace("https://github.com/", "")
                 .replace("git@github.com:", "")
                 .replace(".git", "");
        String[] parts = url.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + url);
        }
        return new String[]{parts[0], parts[1]};
    }

    @Override
    public Optional<FileContent> getFile(String path) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
                GITHUB_API_BASE, owner, repo, path, config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            if (node != null && "file".equals(node.get("type").asText())) {
                String content = new String(Base64.getDecoder().decode(
                    node.get("content").asText().replace("\n", "")), StandardCharsets.UTF_8);
                return Optional.of(new FileContent(
                    node.get("path").asText(),
                    content,
                    node.get("sha").asText(),
                    node.get("encoding").asText()
                ));
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get file from GitHub: {}", path, e);
            throw new GitClientException("Failed to get file: " + path, e);
        }
    }

    @Override
    public List<FileEntry> listFiles(String path) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
                GITHUB_API_BASE, owner, repo, path, config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            List<FileEntry> entries = new ArrayList<>();
            JsonNode node = response.getBody();
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    entries.add(new FileEntry(
                        item.get("name").asText(),
                        item.get("path").asText(),
                        item.get("type").asText(),
                        item.get("sha").asText()
                    ));
                }
            }
            return entries;
        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list files from GitHub: {}", path, e);
            throw new GitClientException("Failed to list files: " + path, e);
        }
    }

    @Override
    public String createOrUpdateFile(String path, String content, String message) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/%s",
                GITHUB_API_BASE, owner, repo, path);

            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
            body.put("branch", config.getBranch());

            // Check if file exists to get SHA for update
            Optional<FileContent> existing = getFile(path);
            if (existing.isPresent()) {
                body.put("sha", existing.get().sha());
            }

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, createEntity(body), JsonNode.class);

            JsonNode node = response.getBody();
            return node != null ? node.get("commit").get("sha").asText() : null;
        } catch (Exception e) {
            log.error("Failed to create/update file on GitHub: {}", path, e);
            throw new GitClientException("Failed to create/update file: " + path, e);
        }
    }

    @Override
    public String commitFiles(Map<String, String> files, String message) {
        // For GitHub, we need to use the Git Data API for atomic commits
        try {
            // Get the latest commit SHA
            String latestCommit = getLatestCommit();

            // Get the tree SHA of the latest commit
            String treeUrl = String.format("%s/repos/%s/%s/git/commits/%s",
                GITHUB_API_BASE, owner, repo, latestCommit);
            ResponseEntity<JsonNode> commitResponse = restTemplate.exchange(
                treeUrl, HttpMethod.GET, createEntity(null), JsonNode.class);
            String baseTreeSha = commitResponse.getBody().get("tree").get("sha").asText();

            // Create blobs for each file
            List<Map<String, String>> treeEntries = new ArrayList<>();
            for (Map.Entry<String, String> file : files.entrySet()) {
                // Create blob
                Map<String, Object> blobBody = new HashMap<>();
                blobBody.put("content", file.getValue());
                blobBody.put("encoding", "utf-8");

                String blobUrl = String.format("%s/repos/%s/%s/git/blobs", GITHUB_API_BASE, owner, repo);
                ResponseEntity<JsonNode> blobResponse = restTemplate.exchange(
                    blobUrl, HttpMethod.POST, createEntity(blobBody), JsonNode.class);
                String blobSha = blobResponse.getBody().get("sha").asText();

                Map<String, String> entry = new HashMap<>();
                entry.put("path", file.getKey());
                entry.put("mode", "100644");
                entry.put("type", "blob");
                entry.put("sha", blobSha);
                treeEntries.add(entry);
            }

            // Create new tree
            Map<String, Object> treeBody = new HashMap<>();
            treeBody.put("base_tree", baseTreeSha);
            treeBody.put("tree", treeEntries);

            String createTreeUrl = String.format("%s/repos/%s/%s/git/trees", GITHUB_API_BASE, owner, repo);
            ResponseEntity<JsonNode> treeResponse = restTemplate.exchange(
                createTreeUrl, HttpMethod.POST, createEntity(treeBody), JsonNode.class);
            String newTreeSha = treeResponse.getBody().get("sha").asText();

            // Create commit
            Map<String, Object> commitBody = new HashMap<>();
            commitBody.put("message", message);
            commitBody.put("tree", newTreeSha);
            commitBody.put("parents", List.of(latestCommit));

            String createCommitUrl = String.format("%s/repos/%s/%s/git/commits", GITHUB_API_BASE, owner, repo);
            ResponseEntity<JsonNode> newCommitResponse = restTemplate.exchange(
                createCommitUrl, HttpMethod.POST, createEntity(commitBody), JsonNode.class);
            String newCommitSha = newCommitResponse.getBody().get("sha").asText();

            // Update branch reference
            Map<String, Object> refBody = new HashMap<>();
            refBody.put("sha", newCommitSha);
            refBody.put("force", false);

            String refUrl = String.format("%s/repos/%s/%s/git/refs/heads/%s",
                GITHUB_API_BASE, owner, repo, config.getBranch());
            restTemplate.exchange(refUrl, HttpMethod.PATCH, createEntity(refBody), JsonNode.class);

            return newCommitSha;
        } catch (Exception e) {
            log.error("Failed to commit files to GitHub", e);
            throw new GitClientException("Failed to commit files", e);
        }
    }

    @Override
    public String deleteFile(String path, String message) {
        try {
            Optional<FileContent> existing = getFile(path);
            if (existing.isEmpty()) {
                throw new GitClientException("File not found: " + path, null);
            }

            String url = String.format("%s/repos/%s/%s/contents/%s",
                GITHUB_API_BASE, owner, repo, path);

            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("sha", existing.get().sha());
            body.put("branch", config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.DELETE, createEntity(body), JsonNode.class);

            JsonNode node = response.getBody();
            return node != null ? node.get("commit").get("sha").asText() : null;
        } catch (Exception e) {
            log.error("Failed to delete file from GitHub: {}", path, e);
            throw new GitClientException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public String getLatestCommit() {
        try {
            String url = String.format("%s/repos/%s/%s/commits/%s",
                GITHUB_API_BASE, owner, repo, config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            return node != null ? node.get("sha").asText() : null;
        } catch (Exception e) {
            log.error("Failed to get latest commit from GitHub", e);
            throw new GitClientException("Failed to get latest commit", e);
        }
    }

    @Override
    public boolean isAccessible() {
        try {
            getRepositoryInfo();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public RepositoryInfo getRepositoryInfo() {
        try {
            String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, owner, repo);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            if (node != null) {
                return new RepositoryInfo(
                    node.get("name").asText(),
                    node.get("full_name").asText(),
                    node.get("default_branch").asText(),
                    node.get("html_url").asText()
                );
            }
            throw new GitClientException("Failed to get repository info", null);
        } catch (Exception e) {
            log.error("Failed to get repository info from GitHub", e);
            throw new GitClientException("Failed to get repository info", e);
        }
    }

    private HttpEntity<?> createEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("Authorization", "Bearer " + config.getAccessToken());
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return new HttpEntity<>(body, headers);
    }

    public static class GitClientException extends RuntimeException {
        public GitClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
