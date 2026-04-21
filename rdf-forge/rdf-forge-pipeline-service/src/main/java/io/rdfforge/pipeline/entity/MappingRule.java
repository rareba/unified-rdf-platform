package io.rdfforge.pipeline.entity;

import java.util.Map;

/**
 * Value object describing a single rule within a {@link MappingEntity}. Rules
 * are stored as a jsonb list inside the mapping row (no dedicated table)
 * because:
 * <ul>
 *   <li>Rules are cheap, always-loaded-with-parent, and never queried
 *       individually.</li>
 *   <li>Flyway evolution of a relational sub-table would bloat the schema for
 *       zero gain; jsonb lets the shape evolve without migrations.</li>
 * </ul>
 *
 * <p>The rule is intentionally a Java record — immutable, serialized via the
 * shared Jackson ObjectMapper, safe to share across threads in the preview
 * executor.
 */
public record MappingRule(
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
