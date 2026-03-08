package io.rdfforge.common.exception;

import lombok.Getter;

/**
 * Exception thrown when a requested resource is not found.
 */
@Getter
public class ResourceNotFoundException extends RdfForgeException {
    
    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.resourceId = identifier;
    }

    public ResourceNotFoundException(String resourceType, String identifier, String message) {
        super("RESOURCE_NOT_FOUND", message);
        this.resourceType = resourceType;
        this.resourceId = identifier;
    }
}
