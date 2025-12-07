package io.rdfforge.pipeline.destination.providers;

import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationInfo.ConfigField;
import io.rdfforge.pipeline.destination.DestinationProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Destination provider for GitLab CI/CD pipelines.
 *
 * This provider allows triggering GitLab CI/CD pipelines with RDF data as an artifact.
 * It pushes the RDF output to a GitLab repository and triggers a pipeline.
 */
@Slf4j
@Component
public class GitLabDestinationProvider implements DestinationProvider {

    private static final String CATEGORY_CICD = "ci-cd";
    private static final String CAPABILITY_TRIGGER_PIPELINE = "trigger-pipeline";
    private static final String CAPABILITY_WAIT_FOR_COMPLETION = "wait-for-completion";
    private static final String DEFAULT_GITLAB_API = "https://gitlab.com/api/v4";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DestinationInfo getDestinationInfo() {
        Map<String, ConfigField> configFields = new LinkedHashMap<>();

        configFields.put("gitlabUrl", new ConfigField(
            "gitlabUrl", "GitLab URL", "string",
            "GitLab instance URL (e.g., https://gitlab.com)", false, "https://gitlab.com"
        ));

        configFields.put("projectPath", new ConfigField(
            "projectPath", "Project Path", "string",
            "Full project path (e.g., group/project)", true
        ));

        configFields.put("accessToken", new ConfigField(
            "accessToken", "Access Token", "string",
            "GitLab Personal Access Token or Project Access Token", true, true
        ));

        configFields.put("branch", new ConfigField(
            "branch", "Branch", "string",
            "Target branch for the pipeline", false, "main"
        ));

        configFields.put("filePath", new ConfigField(
            "filePath", "File Path", "string",
            "Path in repository for the RDF file", false, "data/output.ttl"
        ));

        configFields.put("format", new ConfigField(
            "format", "RDF Format", "select",
            "Output format for RDF data", false, "turtle",
            List.of("turtle", "ntriples", "rdfxml", "jsonld"), false
        ));

        configFields.put("commitMessage", new ConfigField(
            "commitMessage", "Commit Message", "string",
            "Commit message for the data push", false, "Update RDF data from pipeline"
        ));

        configFields.put("triggerPipeline", new ConfigField(
            "triggerPipeline", "Trigger Pipeline", "boolean",
            "Whether to trigger a CI/CD pipeline after pushing", false, true
        ));

        configFields.put("waitForCompletion", new ConfigField(
            "waitForCompletion", "Wait for Completion", "boolean",
            "Wait for the GitLab pipeline to complete", false, false
        ));

        configFields.put("pipelineVariables", new ConfigField(
            "pipelineVariables", "Pipeline Variables", "object",
            "CI/CD variables to pass to the pipeline", false, Map.of()
        ));

        return new DestinationInfo(
            "gitlab",
            "GitLab CI/CD",
            "Push RDF data to GitLab and trigger CI/CD pipelines",
            CATEGORY_CICD,
            configFields,
            List.of(CAPABILITY_TRIGGER_PIPELINE, CAPABILITY_WAIT_FOR_COMPLETION),
            List.of("turtle", "ntriples", "rdfxml", "jsonld")
        );
    }

    @Override
    public PublishResult publish(Model model, Map<String, Object> config) throws IOException {
        String gitlabUrl = getConfig(config, "gitlabUrl", "https://gitlab.com");
        String projectPath = getConfig(config, "projectPath", null);
        String accessToken = getConfig(config, "accessToken", null);
        String branch = getConfig(config, "branch", "main");
        String filePath = getConfig(config, "filePath", "data/output.ttl");
        String format = getConfig(config, "format", "turtle");
        String commitMessage = getConfig(config, "commitMessage", "Update RDF data from pipeline");
        boolean triggerPipeline = Boolean.parseBoolean(String.valueOf(config.getOrDefault("triggerPipeline", true)));
        boolean waitForCompletion = Boolean.parseBoolean(String.valueOf(config.getOrDefault("waitForCompletion", false)));

        String apiBase = gitlabUrl.endsWith("/api/v4") ? gitlabUrl : gitlabUrl + "/api/v4";
        String encodedProject = UriUtils.encode(projectPath, StandardCharsets.UTF_8);

        // Serialize RDF model
        StringWriter writer = new StringWriter();
        String jenaFormat = mapFormat(format);
        model.write(writer, jenaFormat);
        String rdfContent = writer.toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gitlabUrl", gitlabUrl);
        metadata.put("projectPath", projectPath);
        metadata.put("branch", branch);
        metadata.put("filePath", filePath);

        try {
            // Push file to GitLab
            String commitSha = pushFileToGitLab(apiBase, encodedProject, accessToken, branch, filePath, rdfContent, commitMessage);
            metadata.put("commitSha", commitSha);

            // Trigger pipeline if requested
            if (triggerPipeline) {
                @SuppressWarnings("unchecked")
                Map<String, String> pipelineVars = (Map<String, String>) config.getOrDefault("pipelineVariables", Map.of());
                String pipelineId = triggerGitLabPipeline(apiBase, encodedProject, accessToken, branch, pipelineVars);
                metadata.put("pipelineId", pipelineId);
                metadata.put("pipelineUrl", gitlabUrl + "/" + projectPath + "/-/pipelines/" + pipelineId);

                if (waitForCompletion) {
                    String status = waitForPipelineCompletion(apiBase, encodedProject, accessToken, pipelineId);
                    metadata.put("pipelineStatus", status);

                    if (!"success".equals(status)) {
                        return PublishResult.failure("Pipeline failed with status: " + status);
                    }
                }
            }

            return PublishResult.success(model.size(), null, metadata);
        } catch (Exception e) {
            log.error("Failed to publish to GitLab: {}", e.getMessage(), e);
            return PublishResult.failure(e.getMessage());
        }
    }

    private String pushFileToGitLab(String apiBase, String encodedProject, String accessToken,
                                    String branch, String filePath, String content, String commitMessage) {
        String encodedPath = UriUtils.encode(filePath, StandardCharsets.UTF_8);

        // Check if file exists
        String checkUrl = String.format("%s/projects/%s/repository/files/%s?ref=%s",
            apiBase, encodedProject, encodedPath, branch);

        boolean fileExists = false;
        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<Void> checkEntity = new HttpEntity<>(headers);
            restTemplate.exchange(checkUrl, HttpMethod.HEAD, checkEntity, Void.class);
            fileExists = true;
        } catch (Exception e) {
            // File doesn't exist
        }

        // Create or update file
        String fileUrl = String.format("%s/projects/%s/repository/files/%s",
            apiBase, encodedProject, encodedPath);

        Map<String, Object> body = new HashMap<>();
        body.put("branch", branch);
        body.put("commit_message", commitMessage);
        body.put("content", content);

        HttpHeaders headers = createHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        HttpMethod method = fileExists ? HttpMethod.PUT : HttpMethod.POST;
        ResponseEntity<JsonNode> response = restTemplate.exchange(fileUrl, method, entity, JsonNode.class);

        // Get commit SHA
        String commitsUrl = String.format("%s/projects/%s/repository/commits?ref_name=%s&per_page=1",
            apiBase, encodedProject, branch);
        ResponseEntity<JsonNode> commitsResponse = restTemplate.exchange(
            commitsUrl, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

        JsonNode commits = commitsResponse.getBody();
        if (commits != null && commits.isArray() && commits.size() > 0) {
            return commits.get(0).get("id").asText();
        }

        return "unknown";
    }

    private String triggerGitLabPipeline(String apiBase, String encodedProject, String accessToken,
                                         String branch, Map<String, String> variables) {
        String url = String.format("%s/projects/%s/pipeline", apiBase, encodedProject);

        Map<String, Object> body = new HashMap<>();
        body.put("ref", branch);

        if (variables != null && !variables.isEmpty()) {
            List<Map<String, String>> varList = new ArrayList<>();
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                varList.add(Map.of("key", entry.getKey(), "value", entry.getValue()));
            }
            body.put("variables", varList);
        }

        HttpHeaders headers = createHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);

        JsonNode pipeline = response.getBody();
        if (pipeline != null) {
            return pipeline.get("id").asText();
        }

        throw new RuntimeException("Failed to trigger pipeline");
    }

    private String waitForPipelineCompletion(String apiBase, String encodedProject,
                                              String accessToken, String pipelineId) {
        String url = String.format("%s/projects/%s/pipelines/%s", apiBase, encodedProject, pipelineId);
        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        int maxAttempts = 120; // 10 minutes with 5 second intervals
        int attempts = 0;

        while (attempts < maxAttempts) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
                JsonNode pipeline = response.getBody();

                if (pipeline != null) {
                    String status = pipeline.get("status").asText();

                    if ("success".equals(status) || "failed".equals(status) ||
                        "canceled".equals(status) || "skipped".equals(status)) {
                        return status;
                    }
                }

                Thread.sleep(5000);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Pipeline wait interrupted", e);
            }
        }

        return "timeout";
    }

    @Override
    public void clearGraph(String graphUri, Map<String, Object> config) throws IOException {
        // Not applicable for GitLab
        throw new UnsupportedOperationException("clearGraph not supported for GitLab destination");
    }

    @Override
    public boolean isAvailable(Map<String, Object> config) {
        String gitlabUrl = getConfig(config, "gitlabUrl", "https://gitlab.com");
        String projectPath = getConfig(config, "projectPath", null);
        String accessToken = getConfig(config, "accessToken", null);

        if (projectPath == null || accessToken == null) {
            return false;
        }

        String apiBase = gitlabUrl.endsWith("/api/v4") ? gitlabUrl : gitlabUrl + "/api/v4";
        String encodedProject = UriUtils.encode(projectPath, StandardCharsets.UTF_8);
        String url = String.format("%s/projects/%s", apiBase, encodedProject);

        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();

        if (!config.containsKey("projectPath") || config.get("projectPath") == null ||
            config.get("projectPath").toString().isEmpty()) {
            errors.add("Project path is required");
        }

        if (!config.containsKey("accessToken") || config.get("accessToken") == null ||
            config.get("accessToken").toString().isEmpty()) {
            errors.add("Access token is required");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }

        return ValidationResult.success();
    }

    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        return headers;
    }

    private String getConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    private String mapFormat(String format) {
        return switch (format.toLowerCase()) {
            case "turtle", "ttl" -> "TURTLE";
            case "ntriples", "nt" -> "N-TRIPLES";
            case "rdfxml", "rdf", "xml" -> "RDF/XML";
            case "jsonld", "json-ld" -> "JSON-LD";
            default -> "TURTLE";
        };
    }
}
