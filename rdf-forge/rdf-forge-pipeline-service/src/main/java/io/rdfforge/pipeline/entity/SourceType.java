package io.rdfforge.pipeline.entity;

/**
 * Supported source formats for a {@link MappingEntity}. Each value implies a
 * different {@code sourceConfig} schema (see docs on {@code MappingEntity}).
 */
public enum SourceType {
    CSV,
    TSV,
    JSON,
    XML,
    XLSX
}
