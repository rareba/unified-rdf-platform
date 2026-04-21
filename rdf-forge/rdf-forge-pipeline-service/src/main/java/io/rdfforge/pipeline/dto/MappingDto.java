package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.SourceType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire shape of a {@link io.rdfforge.pipeline.entity.MappingEntity}.
 * Never return the entity itself — the entity is a managed JPA object and
 * includes Hibernate-specific state that must not leak across the API.
 */
public record MappingDto(
    UUID id,
    UUID projectId,
    String name,
    String description,
    SourceType sourceType,
    Map<String, Object> sourceConfig,
    String targetNamespace,
    Map<String, Object> targetOntologies,
    List<MappingRule> rules,
    MappingType mappingType,
    int version,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
