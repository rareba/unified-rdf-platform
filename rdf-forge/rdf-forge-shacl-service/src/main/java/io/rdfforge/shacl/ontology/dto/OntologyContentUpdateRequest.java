package io.rdfforge.shacl.ontology.dto;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Payload for PUT /api/v1/ontologies/{id}/content — bumps version. */
public record OntologyContentUpdateRequest(
    @NotBlank String content,
    @NotNull RdfFormat format
) {}
