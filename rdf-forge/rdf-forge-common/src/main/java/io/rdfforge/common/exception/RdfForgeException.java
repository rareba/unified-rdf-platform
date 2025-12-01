package io.rdfforge.common.exception;

public class RdfForgeException extends RuntimeException {
    private final String errorCode;

    public RdfForgeException(String message) {
        super(message);
        this.errorCode = "RDF_FORGE_ERROR";
    }

    public RdfForgeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RdfForgeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
