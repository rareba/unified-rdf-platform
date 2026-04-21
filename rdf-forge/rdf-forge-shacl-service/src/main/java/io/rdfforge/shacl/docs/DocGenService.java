package io.rdfforge.shacl.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
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
 * repository. Mappings and release manifests live in pipeline-service and
 * are fetched over HTTP via {@link RestTemplate}. Every outbound call
 * forwards the gateway-authenticated caller's identity headers
 * ({@code X-User-Id}, {@code X-User-Email}, {@code X-User-Roles}) so
 * pipeline-service's {@code CurrentUserArgumentResolver} sees a valid
 * principal.
 *
 * <p>Failure modes are explicit — <b>never silently degrade</b>:
 * <ul>
 *   <li>401/403 downstream → {@link DocGenDownstreamAuthException} (502)</li>
 *   <li>404 for the project → {@link ResourceNotFoundException} (404)</li>
 *   <li>5xx or connect error → {@link DocGenGenerationException} (502)</li>
 * </ul>
 *
 * <p>Security: every user-supplied string (ontology names, namespaces,
 * source types…) passes through {@link HtmlUtils#htmlEscape(String)} before
 * rendering.
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

    // Test hook: allow test code to inject a RestTemplate stub without
    // requiring a full Spring context.
    void setRestTemplate(RestTemplate rt) {
        this.injectedRestTemplate = rt;
    }

    // Test hook: override service URLs directly.
    void setPipelineServiceUrl(String url) { this.pipelineServiceUrl = url; }
    void setTriplestoreServiceUrl(String url) { this.triplestoreServiceUrl = url; }

    private RestTemplate injectedRestTemplate;

    private RestTemplate restTemplate() {
        return injectedRestTemplate != null ? injectedRestTemplate : docRestTemplate;
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Build a {@link SemanticApiDoc} for the given project.
     *
     * @param projectId project UUID
     * @param user      authenticated caller; identity headers are forwarded on
     *                  every downstream call
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
    // Identity propagation
    // -----------------------------------------------------------------

    /**
     * Build the HTTP headers a downstream call must carry to satisfy
     * {@code CurrentUserArgumentResolver} on the receiving service. Only
     * non-empty values are forwarded.
     *
     * <p>This is the only spot where downstream identity is assembled — if
     * the header contract changes (see {@code CurrentUserArgumentResolver}
     * in rdf-forge-common), update this method, not the call sites.
     */
    private HttpHeaders forwardedIdentity(AuthUser user) {
        HttpHeaders headers = new HttpHeaders();
        if (user == null || user.isAnonymous()) {
            // Intentionally leave identity headers off: the downstream will
            // then reply 401, which fetchMappingSummary maps to
            // DocGenDownstreamAuthException. The caller should have been
            // required by @CurrentUser at the controller layer.
            return headers;
        }
        if (user.id() != null) {
            headers.set("X-User-Id", user.id().toString());
        }
        if (user.email() != null && !user.email().isBlank()) {
            headers.set("X-User-Email", user.email());
        }
        Set<String> roles = user.roles();
        if (roles != null && !roles.isEmpty()) {
            headers.set("X-User-Roles", String.join(",", roles));
        }
        return headers;
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

    private SemanticApiDoc.MappingSummary fetchMappingSummary(UUID projectId, AuthUser user) {
        String url = pipelineServiceUrl + "/api/v1/mappings?projectId=" + projectId;
        List<Map<String, Object>> mappings = exchangeList(url, user,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {},
            "mappings", projectId);
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
    }

    // -----------------------------------------------------------------
    // Endpoints — real (from latest PUBLISHED release) or synthetic
    // -----------------------------------------------------------------

    /**
     * Attempt to resolve the project's real SPARQL endpoint from its most
     * recent PUBLISHED release manifest. Fall back to a clearly-labeled
     * synthetic endpoint when no published release exists or the manifest
     * is silent on targets.
     */
    @SuppressWarnings("unchecked")
    private List<SemanticApiDoc.EndpointInfo> fetchEndpoints(UUID projectId, AuthUser user) {
        // Pipeline-service currently requires projectId on the releases list
        // endpoint; status filtering is done client-side.
        String url = pipelineServiceUrl + "/api/v1/releases?projectId=" + projectId;
        List<Map<String, Object>> releases;
        try {
            releases = exchangeList(url, user,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {},
                "releases", projectId);
        } catch (ResourceNotFoundException nf) {
            // If the project itself is not found, surface that — same as the
            // mapping-fetch would.
            throw nf;
        }
        if (releases == null) releases = List.of();

        // Filter to PUBLISHED, take the most recent (list is already
        // createdAt-desc in pipeline-service; we re-sort by publishedAt to be
        // defensive).
        Map<String, Object> latest = releases.stream()
            .filter(r -> "PUBLISHED".equalsIgnoreCase(asString(r.get("status"))))
            .max(Comparator.comparing(r -> asString(r.get("publishedAt")),
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);

        if (latest != null) {
            Map<String, Object> manifest = latest.get("manifest") instanceof Map
                ? (Map<String, Object>) latest.get("manifest") : Map.of();
            String triplestoreId = asString(manifest.get("triplestoreId"));
            String targetGraph = asString(manifest.get("targetGraph"));
            // Also check refs.triplestoreId / refs.targetGraph (current shape).
            if (manifest.get("refs") instanceof Map<?, ?> refs) {
                if (triplestoreId == null) triplestoreId = asString(refs.get("triplestoreId"));
                if (targetGraph == null) targetGraph = asString(refs.get("targetGraph"));
            }

            if (triplestoreId != null || targetGraph != null) {
                String graph = targetGraph != null
                    ? targetGraph
                    : "urn:rdfforge:project:" + projectId;
                return List.of(new SemanticApiDoc.EndpointInfo(
                    "SPARQL",
                    triplestoreServiceUrl + "/api/v1/sparql",
                    graph,
                    false,
                    Map.of(
                        "method", "GET/POST",
                        "accept", "application/sparql-results+json",
                        "releaseVersion", nz(asString(latest.get("version"))),
                        "triplestoreId", nz(triplestoreId)
                    )
                ));
            }
        }

        // Fallback: no published release or the manifest has no target
        // information. Emit a synthetic endpoint, clearly labeled.
        return List.of(new SemanticApiDoc.EndpointInfo(
                "SPARQL",
                triplestoreServiceUrl + "/api/v1/sparql",
                "urn:rdfforge:project:" + projectId,
                true,
                Map.of(
                    "method", "GET/POST",
                    "accept", "application/sparql-results+json",
                    "note", "No published release; URI follows project convention"
                )
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
    // Project name lookup
    // -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String resolveProjectName(UUID projectId, AuthUser user) {
        String url = pipelineServiceUrl + "/api/v1/projects/" + projectId;
        HttpEntity<Void> entity = new HttpEntity<>(forwardedIdentity(user));
        try {
            Map<String, Object> project = restTemplate().exchange(
                    url, HttpMethod.GET, entity, Map.class).getBody();
            if (project != null && project.get("name") != null) {
                return project.get("name").toString();
            }
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode sc = ex.getStatusCode();
            if (sc.value() == HttpStatus.UNAUTHORIZED.value()
                    || sc.value() == HttpStatus.FORBIDDEN.value()) {
                throw new DocGenDownstreamAuthException(
                    "Downstream (pipeline-service) rejected DocGen request when resolving project name "
                        + "for project " + projectId + " (status=" + sc.value() + "). "
                        + "Check that identity headers are being forwarded.");
            }
            if (sc.value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResourceNotFoundException("Project", projectId.toString());
            }
            throw new DocGenGenerationException(
                "pipeline-service returned " + sc.value() + " resolving project " + projectId, ex);
        } catch (ResourceAccessException ex) {
            throw new DocGenGenerationException(
                "Could not reach pipeline-service to resolve project " + projectId, ex);
        } catch (RestClientException ex) {
            // Last-resort — e.g. deserialization errors.
            log.debug("DocGen: project name fetch failed: {}", ex.getMessage());
        }
        return "Project " + projectId;
    }

    // -----------------------------------------------------------------
    // Shared exchange helper — forwards identity, maps status codes to
    // the DocGen exception hierarchy.
    // -----------------------------------------------------------------

    private <T> T exchangeList(String url,
                               AuthUser user,
                               ParameterizedTypeReference<T> typeRef,
                               String resourceLabel,
                               UUID projectId) {
        HttpEntity<Void> entity = new HttpEntity<>(forwardedIdentity(user));
        try {
            return restTemplate().exchange(url, HttpMethod.GET, entity, typeRef).getBody();
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode sc = ex.getStatusCode();
            if (sc.value() == HttpStatus.UNAUTHORIZED.value()
                    || sc.value() == HttpStatus.FORBIDDEN.value()) {
                // Do NOT include ex.getResponseBodyAsString() — may echo
                // downstream JWT / user-supplied data.
                throw new DocGenDownstreamAuthException(
                    "Downstream (pipeline-service) rejected DocGen " + resourceLabel
                        + " fetch for project " + projectId
                        + " (status=" + sc.value() + "). "
                        + "Identity headers may not be propagating.");
            }
            if (sc.value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResourceNotFoundException("Project", projectId.toString());
            }
            if (sc.is5xxServerError()) {
                throw new DocGenGenerationException(
                    "pipeline-service returned " + sc.value() + " fetching " + resourceLabel
                        + " for project " + projectId, ex);
            }
            throw new DocGenGenerationException(
                "Unexpected response " + sc.value() + " fetching " + resourceLabel
                    + " for project " + projectId, ex);
        } catch (ResourceAccessException ex) {
            throw new DocGenGenerationException(
                "Could not reach pipeline-service to fetch " + resourceLabel
                    + " for project " + projectId, ex);
        } catch (RestClientException ex) {
            // Includes deserialization errors, etc. — treat as a generation
            // failure rather than silent success.
            throw new DocGenGenerationException(
                "Failed to fetch " + resourceLabel + " from pipeline-service for project "
                    + projectId + ": " + ex.getMessage(), ex);
        }
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
          .append("<title>").append(esc(doc.projectName())).append(" &mdash; Semantic API</title>")
          .append("<style>")
          .append("body{font-family:system-ui,-apple-system,sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem;color:#222}")
          .append("h1,h2{border-bottom:1px solid #ccc;padding-bottom:.25rem}")
          .append("table{border-collapse:collapse;width:100%;margin:1rem 0}")
          .append("th,td{border:1px solid #e0e0e0;padding:.4rem .6rem;text-align:left;vertical-align:top}")
          .append("th{background:#f5f5f5}")
          .append("code,pre{background:#f5f5f5;border-radius:4px}")
          .append("pre{padding:.75rem;overflow:auto}")
          .append(".muted{color:#666}")
          .append(".synthetic{color:#a05000;font-style:italic}")
          .append("</style></head><body>");

        sb.append("<h1>").append(esc(doc.projectName())).append(" &mdash; Semantic API</h1>");
        sb.append("<p class=\"muted\">Generated ").append(esc(doc.generatedAt().toString()))
          .append(" for project <code>").append(esc(doc.projectId().toString())).append("</code></p>");

        // Ontologies
        sb.append("<h2>Ontologies</h2>");
        var os = doc.ontologySummary();
        sb.append("<p>").append(os.ontologyCount()).append(" ontologies &middot; ")
          .append(os.classCount()).append(" classes &middot; ")
          .append(os.propertyCount()).append(" properties &middot; ")
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
        sb.append("<p>").append(ms.mappingCount()).append(" mappings &middot; source types: ")
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
            sb.append("<table><thead><tr><th>Kind</th><th>URL</th><th>Graph</th><th>Status</th></tr></thead><tbody>");
            for (var ep : doc.endpoints()) {
                sb.append("<tr><td>").append(esc(ep.kind())).append("</td>")
                  .append("<td><code>").append(esc(ep.sparqlEndpoint())).append("</code></td>")
                  .append("<td><code>").append(esc(ep.publishedGraph())).append("</code></td>")
                  .append("<td>");
                if (ep.synthetic()) {
                    sb.append("<span class=\"synthetic\">")
                      .append(esc("(example - not yet published)"))
                      .append("</span>");
                } else {
                    sb.append("<span>").append(esc("Published")).append("</span>");
                }
                sb.append("</td></tr>");
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

    /** Null-safe HTML escape &mdash; {@code null} becomes empty string. */
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
