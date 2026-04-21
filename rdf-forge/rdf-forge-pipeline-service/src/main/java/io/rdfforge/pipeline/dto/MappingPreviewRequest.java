package io.rdfforge.pipeline.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preview request. One of {@code sourceDataBase64}, {@code sourceDataRef},
 * or {@code sourceRows} must be provided. Rationale:
 *
 * <ul>
 *   <li>{@code sourceRows} — inline material the UI already has in memory
 *       (e.g. the first page of a CSV the user just uploaded). Fastest path.</li>
 *   <li>{@code sourceDataBase64} — for serialized file content when the UI
 *       does not want to POST large multipart bodies.</li>
 *   <li>{@code sourceDataRef} — reference to a data-service source id;
 *       currently unused in the preview path (v1). The service logs and falls
 *       through to {@code sourceRows} / {@code sourceDataBase64}.</li>
 * </ul>
 *
 * {@code sampleLimit} is clamped server-side to
 * {@code rdf-forge.mapping.preview.max-rows} (default 50).
 */
public record MappingPreviewRequest(
    List<Map<String, Object>> sourceRows,
    String sourceDataBase64,
    UUID sourceDataRef,
    Integer sampleLimit
) {}
