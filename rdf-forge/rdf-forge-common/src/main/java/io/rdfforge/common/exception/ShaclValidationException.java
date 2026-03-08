package io.rdfforge.common.exception;

import lombok.Getter;

/**
 * Exception thrown when SHACL validation fails.
 */
@Getter
public class ShaclValidationException extends RdfForgeException {
    
    private final Object validationReport;

    public ShaclValidationException(String message) {
        super("SHACL_VALIDATION_ERROR", message);
        this.validationReport = null;
    }

    public ShaclValidationException(String message, Throwable cause) {
        super("SHACL_VALIDATION_ERROR", message, cause);
        this.validationReport = null;
    }

    public ShaclValidationException(String message, Object validationReport) {
        super("SHACL_VALIDATION_ERROR", message);
        this.validationReport = validationReport;
    }

    public ShaclValidationException(String message, Object validationReport, Throwable cause) {
        super("SHACL_VALIDATION_ERROR", message, cause);
        this.validationReport = validationReport;
    }
}
