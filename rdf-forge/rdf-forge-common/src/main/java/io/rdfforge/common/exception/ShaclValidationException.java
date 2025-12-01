package io.rdfforge.common.exception;

public class ShaclValidationException extends RdfForgeException {
    public ShaclValidationException(String message) {
        super("SHACL_VALIDATION_ERROR", message);
    }

    public ShaclValidationException(String message, Throwable cause) {
        super("SHACL_VALIDATION_ERROR", message, cause);
    }
}
