package io.rdfforge.engine.mapping;

import java.util.List;

/**
 * Engine-local mirror of a mapping. Carrying only what the executor actually
 * needs: a stable {@code mappingId} for trace attribution, the base URI for
 * relative template resolution, and an ordered rule list. Services convert
 * their storage-level mapping entity into this record before invoking the
 * executor.
 */
public record MappingSpec(
    String mappingId,
    String baseUri,
    List<MappingRuleSpec> rules
) {}
