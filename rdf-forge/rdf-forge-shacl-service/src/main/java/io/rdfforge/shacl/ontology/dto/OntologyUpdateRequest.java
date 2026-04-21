package io.rdfforge.shacl.ontology.dto;

/** Payload for PUT /api/v1/ontologies/{id}. Updates metadata only. */
public record OntologyUpdateRequest(
    String name,
    String description,
    String namespace,
    String prefix
) {}
