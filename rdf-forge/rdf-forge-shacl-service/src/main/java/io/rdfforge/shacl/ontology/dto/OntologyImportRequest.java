package io.rdfforge.shacl.ontology.dto;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for POST /api/v1/ontologies/import.
 *
 * <p>{@code content} may be either raw text (Turtle, JSON-LD string, etc.) or
 * a base64-encoded byte blob — the service auto-detects which of the two
 * was sent. {@code namespace} is optional; if omitted, the parser will try
 * to infer it from the serialized content's prefix map.
 */
public record OntologyImportRequest(
    @NotNull UUID projectId,
    @NotBlank String name,
    String description,
    @NotNull RdfFormat format,
    @NotBlank String content,
    String namespace,
    String prefix
) {}
