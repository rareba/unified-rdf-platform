package io.rdfforge.common.exception;

public class PipelineExecutionException extends RdfForgeException {
    public PipelineExecutionException(String message) {
        super("PIPELINE_EXECUTION_ERROR", message);
    }

    public PipelineExecutionException(String message, Throwable cause) {
        super("PIPELINE_EXECUTION_ERROR", message, cause);
    }
}
