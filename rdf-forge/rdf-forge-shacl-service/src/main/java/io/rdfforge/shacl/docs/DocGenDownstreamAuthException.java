package io.rdfforge.shacl.docs;

import io.rdfforge.common.exception.RdfForgeException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a downstream service (pipeline-service, triplestore-service)
 * rejects a DocGen-initiated call with 401/403. Surfaced to the API caller
 * as {@link HttpStatus#BAD_GATEWAY} (502): the inner auth failure is a
 * configuration / identity-propagation bug, not a client-auth failure against
 * this endpoint.
 *
 * <p>Message intentionally does not carry any downstream token or user-supplied
 * data — see {@code GlobalExceptionHandler} for logging conventions.
 */
public class DocGenDownstreamAuthException extends RdfForgeException {

    public DocGenDownstreamAuthException(String message) {
        super("DOC_GEN_DOWNSTREAM_AUTH", message, HttpStatus.BAD_GATEWAY);
    }

    public DocGenDownstreamAuthException(String message, Throwable cause) {
        super("DOC_GEN_DOWNSTREAM_AUTH", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
