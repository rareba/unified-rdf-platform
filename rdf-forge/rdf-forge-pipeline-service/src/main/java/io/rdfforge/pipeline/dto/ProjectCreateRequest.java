package io.rdfforge.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request body for creating a new Project. The {@code createdBy} field is
 * deliberately absent — the service stamps it from the gateway-trusted
 * {@code AuthUser} so that ownership cannot be spoofed from the client.
 */
public record ProjectCreateRequest(
    @NotBlank(message = "Project name is required")
    @Size(max = 255, message = "Project name must not exceed 255 characters")
    String name,

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    String description,

    @NotBlank(message = "Base URI is required")
    @Size(max = 1000, message = "Base URI must not exceed 1000 characters")
    String baseUri,

    Map<String, Object> metadata
) {}
