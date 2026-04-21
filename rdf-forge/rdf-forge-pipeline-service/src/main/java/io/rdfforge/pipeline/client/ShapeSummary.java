package io.rdfforge.pipeline.client;

import java.util.UUID;

/** Minimal metadata view of a SHACL shape for bundle assembly. */
public record ShapeSummary(
    UUID id,
    String name,
    String content,
    String contentFormat
) {}
