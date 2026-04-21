package io.rdfforge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Pipeline definition / payload validation error. Maps to HTTP 400.
 */
public class PipelineValidationException extends RdfForgeException {

    public PipelineValidationException(String message) {
        super("PIPELINE_VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public PipelineValidationException(String message, Throwable cause) {
        super("PIPELINE_VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, cause);
    }
}
