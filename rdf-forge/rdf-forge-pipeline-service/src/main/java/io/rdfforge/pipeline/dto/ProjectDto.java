package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.ProjectStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response-shape DTO for a Project. Never return the entity directly from
 * controllers — the entity is a JPA-managed mutable object and includes
 * Hibernate fields that must not leak through the API boundary.
 */
public record ProjectDto(
    UUID id,
    String name,
    String description,
    String baseUri,
    ProjectStatus status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {}
