package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.ReleaseStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire shape of a {@link io.rdfforge.pipeline.entity.ReleaseEntity}.
 *
 * <p>Returned by {@code ReleaseController} — never return the entity
 * itself because it includes Hibernate-managed state that must not leak.
 */
public record ReleaseDto(
    UUID id,
    UUID projectId,
    String version,
    String name,
    String notes,
    ReleaseStatus status,
    Map<String, Object> manifest,
    String artifactUri,
    long artifactSizeBytes,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {}
