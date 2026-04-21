package io.rdfforge.shacl.validation.dto;

import io.rdfforge.shacl.validation.ValidationIssueEntity;
import io.rdfforge.shacl.validation.ValidationSeverity;

import java.util.Map;
import java.util.UUID;

/**
 * Read DTO for a single finding produced by a run.
 */
public record ValidationIssueDto(
    UUID id,
    UUID runId,
    String ruleId,
    ValidationSeverity severity,
    String resourceUri,
    String message,
    String sourcePath,
    Map<String, Object> details
) {
    public static ValidationIssueDto from(ValidationIssueEntity e) {
        return new ValidationIssueDto(
            e.getId(),
            e.getRunId(),
            e.getRuleId(),
            e.getSeverity(),
            e.getResourceUri(),
            e.getMessage(),
            e.getSourcePath(),
            e.getDetails()
        );
    }
}
