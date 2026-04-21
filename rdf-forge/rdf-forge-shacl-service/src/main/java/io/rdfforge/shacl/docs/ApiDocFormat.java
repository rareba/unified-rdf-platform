package io.rdfforge.shacl.docs;

/**
 * Output format selector for {@code DocGenService.generate}.
 *
 * <p>{@link #HTML} produces a self-contained HTML string suitable for serving
 * inline or dumping to a static site.
 * <p>{@link #JSON} produces a {@link SemanticApiDoc} serialised as JSON —
 * used for machine consumption or custom templating.
 */
public enum ApiDocFormat {
    HTML,
    JSON
}
