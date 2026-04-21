package io.rdfforge.pipeline.client;

/**
 * Raised by {@link AuthenticatedShaclClient} when a downstream call to
 * shacl-service cannot be satisfied. Carries the HTTP status code so the
 * caller (ReleaseService) can map it to a concise {@code failureReason}
 * without leaking credentials or stack frames.
 *
 * <p>Intentionally a {@link RuntimeException} so it unwinds cleanly through
 * the release build try/catch; {@code ReleaseService} catches it and marks
 * the release FAILED.
 */
public class ShaclClientException extends RuntimeException {

    private final int statusCode;

    public ShaclClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ShaclClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status of the offending downstream call, or 0 if transport-level. */
    public int statusCode() {
        return statusCode;
    }
}
