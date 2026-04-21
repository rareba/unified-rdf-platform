package io.rdfforge.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pipeline {
    private UUID id;
    private UUID projectId;

    @NotBlank(message = "Pipeline name is required")
    @Size(min = 1, max = 255, message = "Pipeline name must be between 1 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_\\.]+$", message = "Pipeline name contains invalid characters. Only alphanumeric, spaces, hyphens, underscores, and periods are allowed")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private DefinitionFormat definitionFormat;

    @NotBlank(message = "Pipeline definition is required")
    @Size(max = 100000, message = "Pipeline definition must not exceed 100KB")
    private String definition;

    private Map<String, Object> variables;
    private List<String> tags;
    private Integer version;
    private boolean template;
    private UUID createdBy;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public enum DefinitionFormat {
        YAML,
        TURTLE,
        JSON
    }
}
