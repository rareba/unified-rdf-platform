package io.rdfforge.engine.mapping;

import java.util.Map;

/**
 * Engine-local mirror of the mapping rule shape. Mirrors
 * {@code io.rdfforge.pipeline.entity.MappingRule} but lives here so the
 * engine module has no reverse dependency on the pipeline-service module.
 * Services adapt to this type before calling the executor.
 *
 * <p>This is a record with a single nested enum — the executor treats it as
 * immutable data and never mutates it during execution.
 */
public record MappingRuleSpec(
    String id,
    RuleType type,
    String source,
    String target,
    String uriTemplate,
    String datatype,
    String language,
    Map<String, Object> transform
) {

    public enum RuleType {
        COLUMN_TO_URI,
        COLUMN_TO_LITERAL,
        FIXED_URI,
        NESTED,
        CONSTANT
    }
}
