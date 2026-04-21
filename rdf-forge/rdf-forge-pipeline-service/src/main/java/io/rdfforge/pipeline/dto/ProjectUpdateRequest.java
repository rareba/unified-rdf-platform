package io.rdfforge.pipeline.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request body for partially updating a Project. All fields are optional;
 * a {@code null} value means "leave the current value unchanged". Status,
 * {@code createdBy}, timestamps, and id are not mutable through this
 * endpoint — use the archive/unarchive endpoints for lifecycle changes.
 */
public record ProjectUpdateRequest(
    @Size(max = 255, message = "Project name must not exceed 255 characters")
    String name,

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    String description,

    @Size(max = 1000, message = "Base URI must not exceed 1000 characters")
    String baseUri,

    Map<String, Object> metadata
) {}
