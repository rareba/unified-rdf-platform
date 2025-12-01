package io.rdfforge.common.exception;

public class PipelineValidationException extends RdfForgeException {
    public PipelineValidationException(String message) {
        super("PIPELINE_VALIDATION_ERROR", message);
    }

    public PipelineValidationException(String message, Throwable cause) {
        super("PIPELINE_VALIDATION_ERROR", message, cause);
    }
}
