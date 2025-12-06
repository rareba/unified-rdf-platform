package io.rdfforge.pipeline.gitsync.client;

import io.rdfforge.pipeline.gitsync.model.GitSyncConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GitLab API client for Git sync operations.
 */
@Slf4j
public class GitLabClient implements GitClient {

    private static final String DEFAULT_GITLAB_API = "https://gitlab.com/api/v4";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GitSyncConfig config;
    private final String apiBase;
    private final String projectPath;

    public GitLabClient(GitSyncConfig config) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.config = config;

        // Parse GitLab URL and project path
        String[] parsed = parseGitLabUrl(config.getRepositoryUrl());
        this.apiBase = parsed[0];
        this.projectPath = parsed[1];
    }

    private String[] parseGitLabUrl(String url) {
        // Handle various GitLab URL formats
        // https://gitlab.com/group/project
        // https://gitlab.company.com/group/subgroup/project
        // git@gitlab.com:group/project.git

        String apiBase = DEFAULT_GITLAB_API;
        String projectPath;

        if (url.startsWith("git@")) {
            // SSH format
            url = url.replace("git@", "https://")
                     .replace(":", "/")
                     .replace(".git", "");
        }

        url = url.replace(".git", "");

        if (url.contains("gitlab.com")) {
            projectPath = url.replace("https://gitlab.com/", "");
        } else {
            // Custom GitLab instance
            int startIdx = url.indexOf("://") + 3;
            int pathIdx = url.indexOf("/", startIdx);
            String host = url.substring(0, pathIdx);
            apiBase = host + "/api/v4";
            projectPath = url.substring(pathIdx + 1);
        }

        return new String[]{apiBase, projectPath};
    }

    private String encodeProjectPath() {
        return UriUtils.encode(projectPath, StandardCharsets.UTF_8);
    }

    @Override
    public Optional<FileContent> getFile(String path) {
        try {
            String encodedPath = UriUtils.encode(path, StandardCharsets.UTF_8);
            String url = String.format("%s/projects/%s/repository/files/%s?ref=%s",
                apiBase, encodeProjectPath(), encodedPath, config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            if (node != null) {
                String content = new String(Base64.getDecoder().decode(
                    node.get("content").asText()), StandardCharsets.UTF_8);
                return Optional.of(new FileContent(
                    node.get("file_path").asText(),
                    content,
                    node.get("blob_id").asText(),
                    node.get("encoding").asText()
                ));
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get file from GitLab: {}", path, e);
            throw new GitClientException("Failed to get file: " + path, e);
        }
    }

    @Override
    public List<FileEntry> listFiles(String path) {
        try {
            String url = String.format("%s/projects/%s/repository/tree?path=%s&ref=%s",
                apiBase, encodeProjectPath(),
                UriUtils.encode(path, StandardCharsets.UTF_8),
                config.getBranch());

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
                        item.get("id").asText()
                    ));
                }
            }
            return entries;
        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list files from GitLab: {}", path, e);
            throw new GitClientException("Failed to list files: " + path, e);
        }
    }

    @Override
    public String createOrUpdateFile(String path, String content, String message) {
        try {
            String encodedPath = UriUtils.encode(path, StandardCharsets.UTF_8);
            String url = String.format("%s/projects/%s/repository/files/%s",
                apiBase, encodeProjectPath(), encodedPath);

            Map<String, Object> body = new HashMap<>();
            body.put("branch", config.getBranch());
            body.put("commit_message", message);
            body.put("content", content);

            // Check if file exists
            Optional<FileContent> existing = getFile(path);
            HttpMethod method = existing.isPresent() ? HttpMethod.PUT : HttpMethod.POST;

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, method, createEntity(body), JsonNode.class);

            JsonNode node = response.getBody();
            // GitLab returns file_path on success, get commit info separately
            return getLatestCommit();
        } catch (Exception e) {
            log.error("Failed to create/update file on GitLab: {}", path, e);
            throw new GitClientException("Failed to create/update file: " + path, e);
        }
    }

    @Override
    public String commitFiles(Map<String, String> files, String message) {
        try {
            String url = String.format("%s/projects/%s/repository/commits",
                apiBase, encodeProjectPath());

            List<Map<String, String>> actions = new ArrayList<>();
            for (Map.Entry<String, String> file : files.entrySet()) {
                Map<String, String> action = new HashMap<>();

                // Check if file exists to determine action type
                Optional<FileContent> existing = getFile(file.getKey());
                action.put("action", existing.isPresent() ? "update" : "create");
                action.put("file_path", file.getKey());
                action.put("content", file.getValue());
                actions.add(action);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("branch", config.getBranch());
            body.put("commit_message", message);
            body.put("actions", actions);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, createEntity(body), JsonNode.class);

            JsonNode node = response.getBody();
            return node != null ? node.get("id").asText() : null;
        } catch (Exception e) {
            log.error("Failed to commit files to GitLab", e);
            throw new GitClientException("Failed to commit files", e);
        }
    }

    @Override
    public String deleteFile(String path, String message) {
        try {
            String encodedPath = UriUtils.encode(path, StandardCharsets.UTF_8);
            String url = String.format("%s/projects/%s/repository/files/%s",
                apiBase, encodeProjectPath(), encodedPath);

            Map<String, Object> body = new HashMap<>();
            body.put("branch", config.getBranch());
            body.put("commit_message", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("PRIVATE-TOKEN", config.getAccessToken());

            HttpEntity<?> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, JsonNode.class);

            return getLatestCommit();
        } catch (Exception e) {
            log.error("Failed to delete file from GitLab: {}", path, e);
            throw new GitClientException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public String getLatestCommit() {
        try {
            String url = String.format("%s/projects/%s/repository/commits?ref_name=%s&per_page=1",
                apiBase, encodeProjectPath(), config.getBranch());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            if (node != null && node.isArray() && node.size() > 0) {
                return node.get(0).get("id").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get latest commit from GitLab", e);
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
            String url = String.format("%s/projects/%s", apiBase, encodeProjectPath());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, createEntity(null), JsonNode.class);

            JsonNode node = response.getBody();
            if (node != null) {
                return new RepositoryInfo(
                    node.get("name").asText(),
                    node.get("path_with_namespace").asText(),
                    node.get("default_branch").asText(),
                    node.get("web_url").asText()
                );
            }
            throw new GitClientException("Failed to get repository info", null);
        } catch (Exception e) {
            log.error("Failed to get repository info from GitLab", e);
            throw new GitClientException("Failed to get repository info", e);
        }
    }

    private HttpEntity<?> createEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("PRIVATE-TOKEN", config.getAccessToken());
        return new HttpEntity<>(body, headers);
    }

    public static class GitClientException extends RuntimeException {
        public GitClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
