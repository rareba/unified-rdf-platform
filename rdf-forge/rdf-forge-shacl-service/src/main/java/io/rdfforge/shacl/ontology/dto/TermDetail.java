package io.rdfforge.shacl.ontology.dto;

import lombok.Builder;

import java.util.List;

/** Full detail returned by GET /{id}/term?uri=... */
@Builder
public record TermDetail(
    String uri,
    List<String> types,
    String label,
    String comment,
    List<String> altLabels,
    List<String> domain,
    List<String> range,
    List<String> broader,
    List<String> narrower,
    List<String> exactMatch,
    List<String> closeMatch
) {}
