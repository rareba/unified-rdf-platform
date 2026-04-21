package io.rdfforge.pipeline.dto;

import java.util.List;
import java.util.Map;

/**
 * Explain request: give the user a full trace of every triple that would be
 * emitted from the sample source. One of {@code sourceRowIndex} or
 * {@code sourceRows} must be provided:
 *
 * <ul>
 *   <li>{@code sourceRowIndex} — used when the UI previously sent sourceRows
 *       and now wants to explain row N only.</li>
 *   <li>{@code sourceRows} — inline data for the explain run.</li>
 * </ul>
 */
public record ExplainRequest(
    Integer sourceRowIndex,
    List<Map<String, Object>> sourceRows,
    Integer sampleLimit
) {}
