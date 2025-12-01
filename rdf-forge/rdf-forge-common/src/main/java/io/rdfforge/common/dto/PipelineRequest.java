package io.rdfforge.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for creating and updating pipelines with validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating or updating a pipeline")
public class PipelineRequest {

    @Schema(description = "Project ID the pipeline belongs to", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;

    @NotBlank(message = "Pipeline name is required")
    @Size(min = 1, max = 255, message = "Pipeline name must be between 1 and 255 characters")
    @Schema(description = "Name of the pipeline", example = "CSV to RDF Converter", required = true)
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Schema(description = "Description of the pipeline", example = "Converts CSV data to RDF using CSVW mappings")
    private String description;

    @NotNull(message = "Definition format is required")
    @Schema(description = "Format of the pipeline definition", example = "YAML", required = true)
    private DefinitionFormat definitionFormat;

    @NotBlank(message = "Pipeline definition is required")
    @Schema(description = "The pipeline definition content in the specified format", required = true)
    private String definition;

    @Schema(description = "Variables that can be referenced in the pipeline definition")
    private Map<String, Object> variables;

    @Schema(description = "Tags for categorizing the pipeline")
    private List<String> tags;

    @Schema(description = "Whether this pipeline should be saved as a template")
    private boolean template;

    public enum DefinitionFormat {
        @Schema(description = "YAML format")
        YAML,
        @Schema(description = "Turtle RDF format")
        TURTLE,
        @Schema(description = "JSON format")
        JSON
    }
}