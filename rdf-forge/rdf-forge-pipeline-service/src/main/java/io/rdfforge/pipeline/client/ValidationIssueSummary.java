package io.rdfforge.pipeline.client;

import java.util.UUID;

/** Minimal per-issue view for validation-summary.json. */
public record ValidationIssueSummary(
    UUID id,
    String ruleId,
    String severity,
    String resourceUri,
    String message,
    String sourcePath
) {}
