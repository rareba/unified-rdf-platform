package io.rdfforge.shacl.ontology.dto;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Ontology metadata response (no content payload). */
@Builder
public record OntologyDto(
    UUID id,
    UUID projectId,
    String name,
    String description,
    String namespace,
    String prefix,
    RdfFormat format,
    Integer version,
    Map<String, Object> metadata,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
