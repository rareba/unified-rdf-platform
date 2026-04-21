package io.rdfforge.pipeline.client;

import java.util.UUID;

/**
 * Minimal metadata view of an ontology returned by shacl-service's
 * {@code GET /api/v1/ontologies/{id}/content}. We keep only the fields the
 * release bundle cares about so the client surface doesn't drag in the full
 * shacl-service DTOs.
 */
public record OntologySummary(
    UUID id,
    String name,
    String prefix,
    String content
) {}
