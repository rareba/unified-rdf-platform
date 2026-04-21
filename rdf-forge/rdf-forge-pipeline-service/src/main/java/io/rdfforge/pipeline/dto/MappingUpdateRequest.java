package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.SourceType;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Partial update. Any field set to {@code null} is left untouched — clients
 * only send what they mean to change.
 */
public record MappingUpdateRequest(
    @Size(max = 255, message = "Mapping name must not exceed 255 characters")
    String name,

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    String description,

    SourceType sourceType,

    Map<String, Object> sourceConfig,

    @Size(max = 1000, message = "targetNamespace must not exceed 1000 characters")
    String targetNamespace,

    Map<String, Object> targetOntologies,

    List<MappingRule> rules
) {}
