package io.rdfforge.shacl.validation.dto;

import io.rdfforge.shacl.validation.ValidationSuiteEntity;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read DTO for a validation suite.
 */
public record ValidationSuiteDto(
    UUID id,
    UUID projectId,
    String name,
    String description,
    List<ValidationSuiteEntity.SuiteRule> rules,
    ReleaseGate gate,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static ValidationSuiteDto from(ValidationSuiteEntity e) {
        return new ValidationSuiteDto(
            e.getId(),
            e.getProjectId(),
            e.getName(),
            e.getDescription(),
            e.getRules(),
            e.getGate(),
            e.getCreatedBy(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
