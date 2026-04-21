package io.rdfforge.pipeline.entity;

/**
 * Lifecycle state of a {@link ReleaseEntity}.
 *
 * <p>State transitions (guarded by ReleaseService):
 * <pre>
 *   DRAFT      -> BUILDING -> PUBLISHED
 *                    |           |
 *                    v           v
 *                  FAILED     ARCHIVED
 * </pre>
 */
public enum ReleaseStatus {
    /** Release created, manifest editable, artifact not yet built. */
    DRAFT,
    /** Build in progress — assets being gathered + zipped. */
    BUILDING,
    /** Build succeeded and an artifact is available for download. */
    PUBLISHED,
    /** Build failed; see manifest.buildError for cause. */
    FAILED,
    /** Soft-hidden release; artifact may still be downloadable. */
    ARCHIVED
}
