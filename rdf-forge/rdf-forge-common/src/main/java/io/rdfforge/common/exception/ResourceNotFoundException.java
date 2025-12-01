package io.rdfforge.common.exception;

public class ResourceNotFoundException extends RdfForgeException {
    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found: %s", resourceType, identifier));
    }
}
