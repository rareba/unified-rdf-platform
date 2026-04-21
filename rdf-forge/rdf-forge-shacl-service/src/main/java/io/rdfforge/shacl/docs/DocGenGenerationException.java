package io.rdfforge.shacl.docs;

import io.rdfforge.common.exception.RdfForgeException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when DocGen cannot produce a {@link SemanticApiDoc} because a
 * downstream service failed with a transient (5xx) error or a connection
 * error. Surfaced as {@link HttpStatus#BAD_GATEWAY} (502) so callers can
 * distinguish DocGen-upstream failures from missing resources (404) and
 * auth (401/502 via {@link DocGenDownstreamAuthException}).
 *
 * <p>Do NOT catch this in the service to return a partial doc — we must
 * fail loudly rather than silently degrade.
 */
public class DocGenGenerationException extends RdfForgeException {

    public DocGenGenerationException(String message) {
        super("DOC_GEN_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public DocGenGenerationException(String message, Throwable cause) {
        super("DOC_GEN_FAILED", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
