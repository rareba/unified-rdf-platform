package io.rdfforge.pipeline.dto;

/**
 * Serializable triple for preview/explain responses. {@code objectType}
 * discriminates between URI, literal, and blank node objects so the UI can
 * render each appropriately.
 */
public record TripleDto(
    String subject,
    String predicate,
    String object,
    ObjectType objectType,
    String datatype,
    String language
) {
    public enum ObjectType {
        URI,
        LITERAL,
        BNODE
    }
}
