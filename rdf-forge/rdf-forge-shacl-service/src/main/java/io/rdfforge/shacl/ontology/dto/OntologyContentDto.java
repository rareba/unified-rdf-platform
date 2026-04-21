package io.rdfforge.shacl.ontology.dto;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import lombok.Builder;

import java.util.UUID;

/** Content-bearing response for download / export endpoints. */
@Builder
public record OntologyContentDto(
    UUID id,
    String name,
    RdfFormat format,
    String content
) {}
