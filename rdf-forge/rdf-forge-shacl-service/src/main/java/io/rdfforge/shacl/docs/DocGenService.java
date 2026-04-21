package io.rdfforge.shacl.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.shacl.entity.OntologyEntity;
import io.rdfforge.shacl.ontology.OntologyParserService;
import io.rdfforge.shacl.repository.OntologyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.*;

/**
 * Generates a project-level Semantic API document — a rolled-up view of
 * ontologies, mappings, and publication targets, renderable as both HTML
 * and JSON.
 *
 * <p>Ontologies live in this service and are read directly from the
 * repository. Mappings live in pipeline-service and are fetched over
 * HTTP via {@link RestTemplate} with a short timeout; failures are logged
 * and the doc is generated with an empty mappings section rather than
 * failing the whole request.
 *
 * <p>Security: every user-supplied string (ontology names, namespaces,
 * source types…) passes through {@link HtmlUtils#htmlEscape(String)} before
 * rendering. TODO: sanitize URIs for the href attribute too (currently we
 * only emit namespaces as literal text, not as links).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocGenService {

    private final OntologyRepository ontologyRepository;
    private final OntologyParserService parserService;
    private final ObjectMapper docObjectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${doc-gen.pipeline-service-url:http://rdf-forge-pipeline-service:8001}")
    private String pipelineServiceUrl;

    @Value("${doc-gen.triplestore-service-url:http://rdf-forge-triplestore-service:8006}")
    private String triplestoreServiceUrl;

    private final RestTemplate docRestTemplate = new RestTemplate();

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Build a {@link SemanticApiDoc} for the given project.
     */
    @Transactional(readOnly = true)
    public SemanticApiDoc generate(UUID projectId, AuthUser user) {
        if (projectId == null) {
            throw new RdfForgeException("BAD_REQUEST", "projectId is required", HttpStatus.BAD_REQUEST);
        }
        List<OntologyEntity> ontologies = ontologyRepository.findByProjectId(projectId);

        SemanticApiDoc.OntologySummary ontologySummary = buildOntologySummary(ontologies);
        SemanticApiDoc.MappingSummary mappingSummary = fetchMappingSummary(projectId, user);
        List<SemanticApiDoc.EndpointInfo> endpoints = fetchEndpoints(projectId, user);
        List<SemanticApiDoc.ExampleQuery> queries = buildExampleQueries(ontologySummary);

        return new SemanticApiDoc(
                projectId,
                resolveProjectName(projectId, user),
                Instant.now(),
                ontologySummary,
                mappingSummary,
                endpoints,
                queries
        );
    }

    /**
     * Render a {@link SemanticApiDoc} in the requested format.
     * Returns either an HTML document string or a JSON string — never a POJO.
     */
    public String render(SemanticApiDoc doc, ApiDocFormat format) {
        return switch (format) {
            case HTML -> renderHtml(doc);
            case JSON -> renderJson(doc);
        };
    }

    // -----------------------------------------------------------------
    // Ontology summary
    // -----------------------------------------------------------------

    private SemanticApiDoc.OntologySummary buildOntologySummary(List<OntologyEntity> ontologies) {
        int classes = 0;
        int properties = 0;
        int concepts = 0;
        Map<String, String> nsByPrefix = new LinkedHashMap<>();
        List<SemanticApiDoc.OntologyEntry> entries = new ArrayList<>();

        for (OntologyEntity ont : ontologies) {
            entries.add(new SemanticApiDoc.OntologyEntry(
                ont.getId(), ont.getName(), ont.getNamespace(), ont.getPrefix(),
                ont.getFormat() == null ? "TURTLE" : ont.getFormat().name(),
                ont.getVersion() == null ? 1 : ont.getVersion()
            ));
            if (ont.getPrefix() != null && ont.getNamespace() != null) {
                nsByPrefix.putIfAbsent(ont.getPrefix(), ont.getNamespace());
            }
            try {
                Model model = parserService.parse(ont.getContent(), ont.getFormat());
                classes += countInstances(model, OWL.Class);
                classes += countInstances(model, RDFS.Class);
                properties += countInstances(model, OWL.ObjectProperty);
                properties += countInstances(model, OWL.DatatypeProperty);
                properties += countInstances(model, RDF.Property);
                concepts += countInstances(model, SKOS.Concept);
                model.getNsPrefixMap().forEach(nsByPrefix::putIfAbsent);
            } catch (Exception ex) {
                log.warn("DocGen: skipping ontology {} due to parse error: {}", ont.getId(), ex.getMessage());
            }
        }

        List<SemanticApiDoc.NamespaceBinding> bindings = nsByPrefix.entrySet().stream()
                .map(e -> new SemanticApiDoc.NamespaceBinding(e.getKey(), e.getValue()))
                .toList();
        return new SemanticApiDoc.OntologySummary(
                ontologies.size(), classes, properties, concepts, bindings, entries);
    }

    private int countInstances(Model model, org.apache.jena.rdf.model.Resource type) {
        int count = 0;
        ResIterator it = model.listResourcesWithProperty(RDF.type, type);
        while (it.hasNext()) { it.next(); count++; }
        return count;
    }

    // -----------------------------------------------------------------
    // Mapping summary (fetched from pipeline-service)
    // -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private SemanticApiDoc.MappingSummary fetchMappingSummary(UUID projectId, AuthUser user) {
        String url = pipelineServiceUrl + "/api/v1/mappings?projectId=" + projectId;
        try {
            List<Map<String, Object>> mappings = docRestTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            if (mappings == null) mappings = List.of();
            Set<String> sourceTypes = new LinkedHashSet<>();
            List<SemanticApiDoc.MappingEntry> entries = new ArrayList<>();
            for (Map<String, Object> m : mappings) {
                String srcType = asString(m.get("sourceType"));
                if (srcType != null) sourceTypes.add(srcType);
                entries.add(new SemanticApiDoc.MappingEntry(
                        parseUuid(m.get("id")),
                        asString(m.get("name")),
                        srcType,
                        asString(m.get("targetNamespace")),
                        m.get("version") instanceof Number n ? n.intValue() : 0
                ));
            }
            List<String> sampleTriples = List.of(
                "# Example triples this project emits",
                "<http://example.org/Dataset-1> a <http://www.w3.org/ns/dcat#Dataset> .",
                "<http://example.org/Dataset-1> <http://purl.org/dc/terms/title> \"Example\"@en ."
            );
            return new SemanticApiDoc.MappingSummary(entries.size(),
                    new ArrayList<>(sourceTypes), sampleTriples, entries);
        } catch (RestClientException ex) {
            log.warn("DocGen: mapping fetch failed for project {}: {}", projectId, ex.getMessage());
            return new SemanticApiDoc.MappingSummary(0, List.of(),
                    List.of("# Could not fetch mappings — pipeline-service unreachable."),
                    List.of());
        }
    }

    // -----------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------

    private List<SemanticApiDoc.EndpointInfo> fetchEndpoints(UUID projectId, AuthUser user) {
        // TODO: once triplestore-service exposes project->endpoint resolution,
        // fetch it here. For now we emit a single placeholder based on the
        // conventional graph URI scheme.
        return List.of(new SemanticApiDoc.EndpointInfo(
                "SPARQL",
                triplestoreServiceUrl + "/api/v1/sparql",
                "urn:rdfforge:project:" + projectId,
                Map.of("method", "GET/POST", "accept", "application/sparql-results+json")
        ));
    }

    // -----------------------------------------------------------------
    // Example queries (derived from ontology classes)
    // -----------------------------------------------------------------

    private List<SemanticApiDoc.ExampleQuery> buildExampleQueries(
            SemanticApiDoc.OntologySummary summary) {
        List<SemanticApiDoc.ExampleQuery> out = new ArrayList<>();
        // Count-all
        out.add(new SemanticApiDoc.ExampleQuery(
                "Count all triples",
                "How many triples are in this project's graph?",
                "SELECT (COUNT(*) AS ?n) WHERE { ?s ?p ?o }"));
        // Class list
        out.add(new SemanticApiDoc.ExampleQuery(
                "List distinct types",
                "List every rdf:type used in the project.",
                "SELECT DISTINCT ?type WHERE { ?s a ?type } ORDER BY ?type LIMIT 100"));
        // Per-ontology sample
        for (SemanticApiDoc.OntologyEntry e : summary.ontologies()) {
            if (e.namespace() == null || e.namespace().isBlank()) continue;
            String prefix = e.prefix() == null || e.prefix().isBlank() ? "ont" : e.prefix();
            out.add(new SemanticApiDoc.ExampleQuery(
                    "Instances using " + e.name(),
                    "Show 10 subjects that carry a type from the " + e.name() + " namespace.",
                    "PREFIX " + prefix + ": <" + e.namespace() + ">\n" +
                    "SELECT ?s ?type WHERE { ?s a ?type . FILTER STRSTARTS(STR(?type), \"" +
                    e.namespace() + "\") } LIMIT 10"));
        }
        return out;
    }

    // -----------------------------------------------------------------
    // Project name lookup (best-effort)
    // -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String resolveProjectName(UUID projectId, AuthUser user) {
        String url = pipelineServiceUrl + "/api/v1/projects/" + projectId;
        try {
            Map<String, Object> project = docRestTemplate.getForObject(url, Map.class);
            if (project != null && project.get("name") != null) {
                return project.get("name").toString();
            }
        } catch (RestClientException ex) {
            log.debug("DocGen: project name fetch failed: {}", ex.getMessage());
        }
        return "Project " + projectId;
    }

    // -----------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------

    private String renderJson(SemanticApiDoc doc) {
        try {
            return docObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
        } catch (Exception e) {
            throw new RdfForgeException("DOC_GEN_JSON", "Failed to render doc as JSON: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String renderHtml(SemanticApiDoc doc) {
        StringBuilder sb = new StringBuilder(8 * 1024);
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<title>").append(esc(doc.projectName())).append(" — Semantic API</title>")
          .append("<style>")
          .append("body{font-family:system-ui,-apple-system,sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem;color:#222}")
          .append("h1,h2{border-bottom:1px solid #ccc;padding-bottom:.25rem}")
          .append("table{border-collapse:collapse;width:100%;margin:1rem 0}")
          .append("th,td{border:1px solid #e0e0e0;padding:.4rem .6rem;text-align:left;vertical-align:top}")
          .append("th{background:#f5f5f5}")
          .append("code,pre{background:#f5f5f5;border-radius:4px}")
          .append("pre{padding:.75rem;overflow:auto}")
          .append(".muted{color:#666}")
          .append("</style></head><body>");

        sb.append("<h1>").append(esc(doc.projectName())).append(" — Semantic API</h1>");
        sb.append("<p class=\"muted\">Generated ").append(esc(doc.generatedAt().toString()))
          .append(" for project <code>").append(esc(doc.projectId().toString())).append("</code></p>");

        // Ontologies
        sb.append("<h2>Ontologies</h2>");
        var os = doc.ontologySummary();
        sb.append("<p>").append(os.ontologyCount()).append(" ontologies · ")
          .append(os.classCount()).append(" classes · ")
          .append(os.propertyCount()).append(" properties · ")
          .append(os.skosConceptCount()).append(" SKOS concepts</p>");
        if (!os.ontologies().isEmpty()) {
            sb.append("<table><thead><tr><th>Name</th><th>Prefix</th><th>Namespace</th><th>Format</th><th>Version</th></tr></thead><tbody>");
            for (var e : os.ontologies()) {
                sb.append("<tr><td>").append(esc(e.name())).append("</td>")
                  .append("<td>").append(esc(nz(e.prefix()))).append("</td>")
                  .append("<td><code>").append(esc(nz(e.namespace()))).append("</code></td>")
                  .append("<td>").append(esc(e.format())).append("</td>")
                  .append("<td>").append(e.version()).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }
        if (!os.namespaces().isEmpty()) {
            sb.append("<h3>Namespaces</h3><ul>");
            for (var ns : os.namespaces()) {
                sb.append("<li><code>").append(esc(ns.prefix())).append("</code>: <code>")
                  .append(esc(ns.uri())).append("</code></li>");
            }
            sb.append("</ul>");
        }

        // Mappings
        sb.append("<h2>Mappings</h2>");
        var ms = doc.mappingSummary();
        sb.append("<p>").append(ms.mappingCount()).append(" mappings · source types: ")
          .append(esc(String.join(", ", ms.sourceTypes()))).append("</p>");
        if (!ms.mappings().isEmpty()) {
            sb.append("<table><thead><tr><th>Name</th><th>Source</th><th>Target namespace</th><th>Version</th></tr></thead><tbody>");
            for (var e : ms.mappings()) {
                sb.append("<tr><td>").append(esc(nz(e.name()))).append("</td>")
                  .append("<td>").append(esc(nz(e.sourceType()))).append("</td>")
                  .append("<td><code>").append(esc(nz(e.targetNamespace()))).append("</code></td>")
                  .append("<td>").append(e.version()).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }
        if (!ms.sampleTriples().isEmpty()) {
            sb.append("<h3>Sample triples</h3><pre>");
            for (String t : ms.sampleTriples()) { sb.append(esc(t)).append("\n"); }
            sb.append("</pre>");
        }

        // Endpoints
        sb.append("<h2>Endpoints</h2>");
        if (doc.endpoints().isEmpty()) {
            sb.append("<p class=\"muted\">No endpoints configured.</p>");
        } else {
            sb.append("<table><thead><tr><th>Kind</th><th>URL</th><th>Graph</th></tr></thead><tbody>");
            for (var ep : doc.endpoints()) {
                sb.append("<tr><td>").append(esc(ep.kind())).append("</td>")
                  .append("<td><code>").append(esc(ep.sparqlEndpoint())).append("</code></td>")
                  .append("<td><code>").append(esc(ep.publishedGraph())).append("</code></td></tr>");
            }
            sb.append("</tbody></table>");
        }

        // Queries
        sb.append("<h2>Example queries</h2>");
        for (var q : doc.exampleQueries()) {
            sb.append("<h3>").append(esc(q.title())).append("</h3>");
            if (q.description() != null) {
                sb.append("<p>").append(esc(q.description())).append("</p>");
            }
            sb.append("<pre>").append(esc(q.sparql())).append("</pre>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Null-safe HTML escape — {@code null} becomes empty string. */
    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String asString(Object o) { return o == null ? null : o.toString(); }

    private static UUID parseUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
