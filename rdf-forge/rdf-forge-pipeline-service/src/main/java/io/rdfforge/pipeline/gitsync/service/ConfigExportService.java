package io.rdfforge.pipeline.gitsync.service;

import io.rdfforge.common.model.Pipeline;
import io.rdfforge.pipeline.gitsync.model.ConfigManifest;
import io.rdfforge.pipeline.service.PipelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Service for exporting configurations to file format for Git storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigExportService {

    private final PipelineService pipelineService;
    private final ObjectMapper jsonMapper;

    private final ObjectMapper yamlMapper = new ObjectMapper(
        new YAMLFactory()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );

    /**
     * Export all configurations for a project.
     *
     * @param projectId Project ID
     * @param configPath Base path for configs
     * @return Map of file path to content
     */
    public Map<String, String> exportAll(UUID projectId, String configPath) {
        Map<String, String> files = new HashMap<>();

        // Export pipelines
        List<ConfigManifest.ConfigEntry> pipelineEntries = new ArrayList<>();
        var pipelines = pipelineService.list(projectId, Pageable.unpaged()).getContent();
        for (Pipeline pipeline : pipelines) {
            String fileName = sanitizeFileName(pipeline.getName()) + ".yaml";
            String path = configPath + "/pipelines/" + fileName;
            String content = exportPipeline(pipeline);
            files.put(path, content);

            pipelineEntries.add(ConfigManifest.ConfigEntry.builder()
                .id(pipeline.getId().toString())
                .name(pipeline.getName())
                .path(path)
                .format("yaml")
                .version(pipeline.getVersion())
                .checksum(calculateChecksum(content))
                .modifiedAt(pipeline.getUpdatedAt() != null ? pipeline.getUpdatedAt() : pipeline.getCreatedAt())
                .build());
        }

        // Export shapes (TODO: inject ShapeService when available)
        List<ConfigManifest.ConfigEntry> shapeEntries = new ArrayList<>();
        // Placeholder for shape export

        // Create manifest
        ConfigManifest manifest = ConfigManifest.builder()
            .version("1.0")
            .projectId(projectId.toString())
            .exportedAt(Instant.now())
            .pipelines(pipelineEntries)
            .shapes(shapeEntries)
            .settings(new HashMap<>())
            .build();

        try {
            files.put(configPath + "/manifest.yaml", yamlMapper.writeValueAsString(manifest));
        } catch (Exception e) {
            log.error("Failed to serialize manifest", e);
        }

        return files;
    }

    /**
     * Export a single pipeline to YAML format.
     */
    public String exportPipeline(Pipeline pipeline) {
        try {
            Map<String, Object> pipelineData = new LinkedHashMap<>();
            pipelineData.put("name", pipeline.getName());
            pipelineData.put("description", pipeline.getDescription());
            pipelineData.put("version", pipeline.getVersion());

            // Parse the definition and include it
            if (pipeline.getDefinition() != null) {
                ObjectMapper defMapper = pipeline.getDefinitionFormat() == Pipeline.DefinitionFormat.YAML
                    ? yamlMapper : jsonMapper;
                Object definition = defMapper.readValue(pipeline.getDefinition(), Object.class);
                pipelineData.put("definition", definition);
            }

            if (pipeline.getVariables() != null && !pipeline.getVariables().isEmpty()) {
                pipelineData.put("variables", pipeline.getVariables());
            }

            pipelineData.put("template", pipeline.isTemplate());

            // Add metadata
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("id", pipeline.getId().toString());
            if (pipeline.getCreatedAt() != null) {
                metadata.put("createdAt", pipeline.getCreatedAt().toString());
            }
            if (pipeline.getUpdatedAt() != null) {
                metadata.put("updatedAt", pipeline.getUpdatedAt().toString());
            }
            pipelineData.put("metadata", metadata);

            return yamlMapper.writeValueAsString(pipelineData);
        } catch (Exception e) {
            log.error("Failed to export pipeline: {}", pipeline.getName(), e);
            throw new RuntimeException("Failed to export pipeline", e);
        }
    }

    /**
     * Import a pipeline from YAML content.
     */
    public Pipeline importPipeline(String content, UUID projectId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = yamlMapper.readValue(content, Map.class);

            String name = (String) data.get("name");
            String description = (String) data.get("description");
            Object definition = data.get("definition");
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) data.get("variables");
            Boolean template = (Boolean) data.get("template");

            // Extract original ID from metadata if present
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            UUID id = null;
            if (metadata != null && metadata.get("id") != null) {
                try {
                    id = UUID.fromString((String) metadata.get("id"));
                } catch (Exception e) {
                    // Ignore invalid UUID
                }
            }

            return Pipeline.builder()
                .id(id)
                .projectId(projectId)
                .name(name)
                .description(description)
                .definitionFormat(Pipeline.DefinitionFormat.YAML)
                .definition(yamlMapper.writeValueAsString(definition))
                .variables(variables != null ? variables : new HashMap<>())
                .template(template != null && template)
                .build();
        } catch (Exception e) {
            log.error("Failed to import pipeline from YAML", e);
            throw new RuntimeException("Failed to import pipeline", e);
        }
    }

    private String sanitizeFileName(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }
}
