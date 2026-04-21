package io.rdfforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * SHACL validation failure. Maps to HTTP 422 UNPROCESSABLE_ENTITY so callers
 * can distinguish a data-shape violation from a malformed request (400).
 */
@Getter
public class ShaclValidationException extends RdfForgeException {

    private final Object validationReport;

    public ShaclValidationException(String message) {
        super("SHACL_VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
        this.validationReport = null;
    }

    public ShaclValidationException(String message, Throwable cause) {
        super("SHACL_VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY, cause);
        this.validationReport = null;
    }

    public ShaclValidationException(String message, Object validationReport) {
        super("SHACL_VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
        this.validationReport = validationReport;
    }

    public ShaclValidationException(String message, Object validationReport, Throwable cause) {
        super("SHACL_VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY, cause);
        this.validationReport = validationReport;
    }
}
