package io.rdfforge.dimension.dto;

import java.util.UUID;

/**
 * DTO for generated artifacts (SHACL shapes, pipelines) from cube definitions.
 */
public record GeneratedArtifact(
    UUID id,
    String name,
    String type
) {}
