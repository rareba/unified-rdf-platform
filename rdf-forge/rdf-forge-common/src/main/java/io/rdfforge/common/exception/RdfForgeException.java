package io.rdfforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all RDF Forge application exceptions.
 * Provides support for error codes and HTTP status codes.
 */
@Getter
public class RdfForgeException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;

    public RdfForgeException(String message) {
        super(message);
        this.errorCode = "RDF_FORGE_ERROR";
        this.httpStatus = null;
    }

    public RdfForgeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = null;
    }

    public RdfForgeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = null;
    }

    public RdfForgeException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public RdfForgeException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
