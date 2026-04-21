package io.rdfforge.pipeline.entity;

/**
 * Classifies the shape of a {@link MappingEntity}. {@link #GENERIC} is the
 * free-form source→RDF mapping authored by the Universal Mapping Studio.
 * {@link #CUBE} is the pre-populated qb:Observation template that powers the
 * existing Cube flow — the cube controller continues to own CubeEntity, and
 * "create from cube" simply materializes a MappingEntity shaped like a cube
 * without otherwise altering cube behaviour.
 */
public enum MappingType {
    GENERIC,
    CUBE,
    SKOS,
    CUSTOM
}
