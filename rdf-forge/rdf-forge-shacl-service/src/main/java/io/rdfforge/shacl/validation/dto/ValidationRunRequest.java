package io.rdfforge.shacl.validation.dto;

import java.util.UUID;

/**
 * Runtime parameters passed to POST /api/v1/validation/suites/{id}/run.
 *
 * <p>At least one of {@code targetGraph} + {@code targetTriplestoreId}
 * must be supplied unless the suite is purely SHACL-based and will be
 * executed against the stored shapes on a future publish — that path is
 * reserved for phase 6 (release gate).
 */
public record ValidationRunRequest(
    /** Named graph in the target triplestore that contains the data to validate. */
    String targetGraph,
    /** Triplestore connection id (see triplestore-service). */
    UUID targetTriplestoreId,
    /**
     * What triggered this run. Defaults to "manual" when omitted.
     * Used to tag the run's context map for auditing and release gating.
     */
    String triggeredBy
) {
    public ValidationRunRequest {
        if (triggeredBy == null || triggeredBy.isBlank()) {
            triggeredBy = "manual";
        }
    }
}
