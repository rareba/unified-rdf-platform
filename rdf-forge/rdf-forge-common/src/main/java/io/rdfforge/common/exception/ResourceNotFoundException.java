package io.rdfforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found. Always maps to HTTP 404.
 */
@Getter
public class ResourceNotFoundException extends RdfForgeException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND",
              String.format("%s not found: %s", resourceType, identifier),
              HttpStatus.NOT_FOUND);
        this.resourceType = resourceType;
        this.resourceId = identifier;
    }

    public ResourceNotFoundException(String resourceType, String identifier, String message) {
        super("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
        this.resourceType = resourceType;
        this.resourceId = identifier;
    }
}
