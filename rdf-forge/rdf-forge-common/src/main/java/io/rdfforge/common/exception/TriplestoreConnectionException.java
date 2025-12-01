package io.rdfforge.common.exception;

public class TriplestoreConnectionException extends RdfForgeException {
    public TriplestoreConnectionException(String message) {
        super("TRIPLESTORE_CONNECTION_ERROR", message);
    }

    public TriplestoreConnectionException(String message, Throwable cause) {
        super("TRIPLESTORE_CONNECTION_ERROR", message, cause);
    }
}
