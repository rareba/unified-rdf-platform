package io.rdfforge.pipeline.client;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal view of a ValidationRun used only by the release bundle's
 * validation-summary.json. The full DTO in shacl-service has more fields —
 * we only carry counts + the run id so we can fetch issues separately.
 */
public record ValidationRunSummary(
    UUID id,
    UUID suiteId,
    UUID projectId,
    Instant ranAt,
    String status,
    int issueCount,
    int errorCount,
    int warningCount,
    int infoCount,
    int fatalCount,
    String summary
) {}
