package io.rdfforge.shacl.ontology.dto;

import lombok.Builder;

import java.util.List;

/** Outcome of POST /{id}/validate — syntax / parser check only. */
@Builder
public record OntologyValidationResult(
    boolean valid,
    List<String> errors,
    long tripleCount
) {}
