package io.rdfforge.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for creating new job execution requests with validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating a new job execution")
public class JobRequest {

    @NotNull(message = "Pipeline ID is required")
    @Schema(description = "ID of the pipeline to execute", required = true, 
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID pipelineId;

    @Schema(description = "Variables to pass to the pipeline execution")
    private Map<String, Object> variables;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority cannot exceed 10")
    @Schema(description = "Job priority (1-10, higher = more priority)", example = "5", 
            minimum = "1", maximum = "10")
    private Integer priority;

    @Schema(description = "If true, performs a dry run without persisting changes", 
            example = "false", defaultValue = "false")
    private Boolean dryRun;

    @Schema(description = "Optional schedule expression for recurring execution", 
            example = "0 0 2 * * ?")
    private String schedule;

    /**
     * Returns effective priority with default value.
     */
    public int getEffectivePriority() {
        return priority != null ? priority : 5;
    }

    /**
     * Returns effective dry run flag.
     */
    public boolean isEffectiveDryRun() {
        return Boolean.TRUE.equals(dryRun);
    }
}