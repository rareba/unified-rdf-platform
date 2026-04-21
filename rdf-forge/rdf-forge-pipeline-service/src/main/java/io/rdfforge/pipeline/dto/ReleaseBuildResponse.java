package io.rdfforge.pipeline.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Response from {@code POST /releases/{id}/build}.
 *
 * <p>When the build succeeds, {@code releaseDto.status == PUBLISHED}. When
 * it fails (including gate failure), {@code releaseDto.status == FAILED}
 * and {@code validationGateResult} contains structured detail the UI can
 * render without re-fetching.
 */
public record ReleaseBuildResponse(
    UUID releaseId,
    String artifactUri,
    long artifactSizeBytes,
    Map<String, Object> manifest,
    Map<String, Object> validationGateResult
) {}
