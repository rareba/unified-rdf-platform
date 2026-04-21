package io.rdfforge.shacl.ontology.dto;

import lombok.Builder;

import java.util.List;

/** Summary record returned by /classes, /properties, /skos-concepts. */
@Builder
public record TermSearchResult(
    String uri,
    String type,
    String label,
    String comment,
    List<String> altLabels,
    List<String> broader,
    List<String> narrower
) {}
