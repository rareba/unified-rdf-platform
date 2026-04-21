package io.rdfforge.shacl.ontology;

import io.rdfforge.common.exception.RdfForgeException;
import org.springframework.http.HttpStatus;

/**
 * Raised when Jena fails to parse ontology content (syntax error, unsupported
 * format, or security policy violation such as attempted external entity
 * resolution).
 */
public class OntologyParseException extends RdfForgeException {

    public OntologyParseException(String message) {
        super("ONTOLOGY_PARSE_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public OntologyParseException(String message, Throwable cause) {
        super("ONTOLOGY_PARSE_ERROR", message, HttpStatus.BAD_REQUEST, cause);
    }
}
