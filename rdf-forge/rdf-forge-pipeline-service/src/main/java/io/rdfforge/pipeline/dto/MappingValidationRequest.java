package io.rdfforge.pipeline.dto;

import java.util.List;

/**
 * Validation request. {@code availableColumns} is optional — when provided,
 * the service also verifies that every {@code source} referenced by a rule
 * resolves to a real column in the source.
 */
public record MappingValidationRequest(
    List<String> availableColumns
) {}
