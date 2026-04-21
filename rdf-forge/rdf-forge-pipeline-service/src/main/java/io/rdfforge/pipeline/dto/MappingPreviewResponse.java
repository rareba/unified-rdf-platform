package io.rdfforge.pipeline.dto;

import java.util.List;

/**
 * Result of a preview call. {@code sampleSize} is the number of rows we
 * actually executed (after server-side clamping); {@code totalSourceRows}
 * reports how many rows the request carried so the UI can show
 * "showing X of Y".
 */
public record MappingPreviewResponse(
    List<TripleDto> triples,
    int sampleSize,
    int totalSourceRows
) {}
