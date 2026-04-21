package io.rdfforge.shacl.validation.dto;

import io.rdfforge.shacl.validation.ValidationSuiteEntity;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload accepted by PUT /api/v1/validation/suites/{id}.
 */
public record ValidationSuiteUpdateRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description,
    List<ValidationSuiteEntity.SuiteRule> rules,
    ReleaseGate gate
) {
}
