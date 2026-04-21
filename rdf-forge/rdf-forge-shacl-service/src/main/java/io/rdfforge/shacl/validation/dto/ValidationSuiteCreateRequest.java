package io.rdfforge.shacl.validation.dto;

import io.rdfforge.shacl.validation.ValidationSuiteEntity;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload accepted by POST /api/v1/validation/suites.
 */
public record ValidationSuiteCreateRequest(
    @NotNull UUID projectId,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description,
    List<ValidationSuiteEntity.SuiteRule> rules,
    ReleaseGate gate
) {
}
