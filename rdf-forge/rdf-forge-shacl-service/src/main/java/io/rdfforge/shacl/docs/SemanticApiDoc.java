package io.rdfforge.shacl.docs;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Machine-readable representation of a project's semantic API — the union of
 * its ontologies, mappings, triplestore publication targets, and example
 * queries.
 *
 * <p>Rendered either as JSON (for integrations / static site generators) or
 * as HTML (for human viewing) by {@code DocGenService}.
 *
 * <p>Fields are intentionally flat and jackson-friendly so consumers do not
 * need access to service-internal entity types.
 */
public record SemanticApiDoc(
        UUID projectId,
        String projectName,
        Instant generatedAt,
        OntologySummary ontologySummary,
        MappingSummary mappingSummary,
        List<EndpointInfo> endpoints,
        List<ExampleQuery> exampleQueries
) {

    /** Summary across every ontology in the project. */
    public record OntologySummary(
            int ontologyCount,
            int classCount,
            int propertyCount,
            int skosConceptCount,
            List<NamespaceBinding> namespaces,
            List<OntologyEntry> ontologies
    ) {}

    public record OntologyEntry(
            UUID id,
            String name,
            String namespace,
            String prefix,
            String format,
            int version
    ) {}

    public record NamespaceBinding(String prefix, String uri) {}

    /** Summary across every mapping in the project. */
    public record MappingSummary(
            int mappingCount,
            List<String> sourceTypes,
            List<String> sampleTriples,
            List<MappingEntry> mappings
    ) {}

    public record MappingEntry(
            UUID id,
            String name,
            String sourceType,
            String targetNamespace,
            int version
    ) {}

    /** A SPARQL/graph endpoint the project publishes to. */
    public record EndpointInfo(
            String kind,
            String sparqlEndpoint,
            String publishedGraph,
            Map<String, String> metadata
    ) {}

    /** An executable example query with its expected top-level outcome. */
    public record ExampleQuery(
            String title,
            String description,
            String sparql
    ) {}
}
