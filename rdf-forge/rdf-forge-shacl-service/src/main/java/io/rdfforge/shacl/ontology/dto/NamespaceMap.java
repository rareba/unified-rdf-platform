package io.rdfforge.shacl.ontology.dto;

import java.util.List;

/** List of prefix-to-IRI bindings extracted from an ontology. */
public record NamespaceMap(List<Entry> entries) {

    public record Entry(String prefix, String uri) {}

    public static NamespaceMap of(List<Entry> entries) {
        return new NamespaceMap(entries);
    }
}
