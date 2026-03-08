package io.rdfforge.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared utility for parsing pipeline definition strings (JSON or YAML)
 * into a list of {@link PipelineStep} objects.
 *
 * Supports both "params" (UI format) and "parameters" (API format) keys
 * for step parameters, ensuring consistent parsing across all services.
 */
public final class PipelineDefinitionParser {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private PipelineDefinitionParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parse a pipeline definition string into a list of pipeline steps.
     *
     * @param definition the pipeline definition content (JSON or YAML)
     * @param format     the format of the definition
     * @return a list of parsed pipeline steps, or an empty list if no steps are defined
     * @throws PipelineParseException if the definition cannot be parsed
     */
    public static List<PipelineStep> parse(String definition, Pipeline.DefinitionFormat format) {
        if (definition == null || definition.isBlank()) {
            return Collections.emptyList();
        }

        try {
            ObjectMapper mapper = format == Pipeline.DefinitionFormat.YAML ? YAML_MAPPER : JSON_MAPPER;
            Map<String, Object> parsed = mapper.readValue(definition, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsData = (List<Map<String, Object>>) parsed.get("steps");

            if (stepsData == null) {
                return Collections.emptyList();
            }

            List<PipelineStep> steps = new ArrayList<>();
            for (Map<String, Object> stepData : stepsData) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = resolveParameters(stepData);

                @SuppressWarnings("unchecked")
                PipelineStep step = PipelineStep.builder()
                    .id((String) stepData.get("id"))
                    .operationType((String) stepData.get("operation"))
                    .name((String) stepData.get("name"))
                    .parameters(params)
                    .inputConnections((List<String>) stepData.get("inputs"))
                    .outputConnections((List<String>) stepData.get("outputs"))
                    .build();
                steps.add(step);
            }
            return steps;
        } catch (Exception e) {
            throw new PipelineParseException("Failed to parse pipeline definition: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the parameters map from a step data map.
     * Supports both "params" (UI format) and "parameters" (API format) keys.
     * If both are present, "params" takes precedence.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParameters(Map<String, Object> stepData) {
        Map<String, Object> params = (Map<String, Object>) stepData.get("params");
        if (params == null) {
            params = (Map<String, Object>) stepData.get("parameters");
        }
        return params;
    }

    /**
     * Exception thrown when pipeline definition parsing fails.
     */
    public static class PipelineParseException extends RuntimeException {
        public PipelineParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
