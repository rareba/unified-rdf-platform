package io.rdfforge.shacl.validation.dto;

import io.rdfforge.shacl.validation.ValidationRunEntity;
import io.rdfforge.shacl.validation.ValidationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read DTO for a validation run, used by both the history endpoint and the
 * real-time "just finished" response from POST /{id}/run.
 */
public record ValidationRunDto(
    UUID id,
    UUID suiteId,
    UUID projectId,
    Instant ranAt,
    long durationMs,
    ValidationStatus status,
    int issueCount,
    int errorCount,
    int warningCount,
    int infoCount,
    int fatalCount,
    String summary,
    Map<String, Object> context,
    UUID ranBy
) {
    public static ValidationRunDto from(ValidationRunEntity e) {
        return new ValidationRunDto(
            e.getId(),
            e.getSuiteId(),
            e.getProjectId(),
            e.getRanAt(),
            e.getDurationMs(),
            e.getStatus(),
            e.getIssueCount(),
            e.getErrorCount(),
            e.getWarningCount(),
            e.getInfoCount(),
            e.getFatalCount(),
            e.getSummary(),
            e.getContext(),
            e.getRanBy()
        );
    }
}
