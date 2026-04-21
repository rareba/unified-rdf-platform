package io.rdfforge.shacl.ontology.dto;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Payload for creating an ontology when content is supplied separately. */
public record OntologyCreateRequest(
    @NotNull UUID projectId,
    @NotBlank String name,
    String description,
    @NotBlank String namespace,
    String prefix,
    @NotNull RdfFormat format
) {}
