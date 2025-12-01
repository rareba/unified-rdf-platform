package io.rdfforge.engine.operation;

import io.rdfforge.common.exception.RdfForgeException;

public class OperationException extends RdfForgeException {
    private final String operationId;
    private final String stepId;

    public OperationException(String operationId, String message) {
        super("OPERATION_ERROR", message);
        this.operationId = operationId;
        this.stepId = null;
    }

    public OperationException(String operationId, String stepId, String message) {
        super("OPERATION_ERROR", message);
        this.operationId = operationId;
        this.stepId = stepId;
    }

    public OperationException(String operationId, String stepId, String message, Throwable cause) {
        super("OPERATION_ERROR", message, cause);
        this.operationId = operationId;
        this.stepId = stepId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getStepId() {
        return stepId;
    }
}
