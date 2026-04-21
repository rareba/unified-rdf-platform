package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for creating a new mapping. {@code createdBy} is deliberately
 * absent — the service stamps ownership from the gateway-trusted principal.
 *
 * <p>Both {@code mappingType} and {@code rules} default sensibly when absent
 * so cube-template and empty-rules flows both work off the same endpoint.
 */
public record MappingCreateRequest(
    @NotNull(message = "projectId is required")
    UUID projectId,

    @NotBlank(message = "Mapping name is required")
    @Size(max = 255, message = "Mapping name must not exceed 255 characters")
    String name,

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    String description,

    @NotNull(message = "sourceType is required")
    SourceType sourceType,

    Map<String, Object> sourceConfig,

    @Size(max = 1000, message = "targetNamespace must not exceed 1000 characters")
    String targetNamespace,

    Map<String, Object> targetOntologies,

    List<MappingRule> rules,

    MappingType mappingType
) {}
