package io.rdfforge.common.exception;

import lombok.Getter;

/**
 * Exception thrown when connection to a triplestore fails.
 */
@Getter
public class TriplestoreConnectionException extends RdfForgeException {
    
    private final String connectionInfo;

    public TriplestoreConnectionException(String message) {
        super("TRIPLESTORE_CONNECTION_ERROR", message);
        this.connectionInfo = null;
    }

    public TriplestoreConnectionException(String message, Throwable cause) {
        super("TRIPLESTORE_CONNECTION_ERROR", message, cause);
        this.connectionInfo = null;
    }

    public TriplestoreConnectionException(String message, String connectionInfo) {
        super("TRIPLESTORE_CONNECTION_ERROR", message);
        this.connectionInfo = connectionInfo;
    }

    public TriplestoreConnectionException(String message, String connectionInfo, Throwable cause) {
        super("TRIPLESTORE_CONNECTION_ERROR", message, cause);
        this.connectionInfo = connectionInfo;
    }
}
