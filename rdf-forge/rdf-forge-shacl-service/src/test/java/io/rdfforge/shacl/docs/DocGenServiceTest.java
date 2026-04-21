package io.rdfforge.shacl.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.shacl.ontology.OntologyParserService;
import io.rdfforge.shacl.repository.OntologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link DocGenService}.
 *
 * <p>Verifies the two things the service MUST do correctly:
 * <ol>
 *   <li>Propagate the caller's identity to pipeline-service on every outbound
 *       call — the previous implementation shipped a bare RestTemplate, which
 *       silently turned into 401 in a secured deployment.</li>
 *   <li>Fail loudly on downstream errors — no more empty-mappings-masquerading
 *       -as-success.</li>
 * </ol>
 */
class DocGenServiceTest {

    private static final String PIPELINE_URL = "http://pipeline-test";
    private static final String TRIPLESTORE_URL = "http://triplestore-test";

    private DocGenService service;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private OntologyRepository ontologyRepository;
    private OntologyParserService parserService;
    private ObjectMapper objectMapper;

    private AuthUser user;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        ontologyRepository = mock(OntologyRepository.class);
        parserService = mock(OntologyParserService.class);
        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // Ontologies are local to this service — return an empty list so the
        // service focuses purely on the downstream call behaviour.
        when(ontologyRepository.findByProjectId(any())).thenReturn(List.of());

        service = new DocGenService(ontologyRepository, parserService);
        service.setRestTemplate(restTemplate);
        service.setPipelineServiceUrl(PIPELINE_URL);
        service.setTriplestoreServiceUrl(TRIPLESTORE_URL);

        user = new AuthUser(
            UUID.randomUUID(),
            "alice@example.com",
            Set.of("USER", "ADMIN")
        );
        projectId = UUID.randomUUID();
    }

    // -----------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------

    @Test
    void generate_withValidAuth_returnsRealMappings() throws Exception {
        // Call order in generate(): mappings → releases → project name.
        List<Map<String, Object>> mappings = List.of(
            Map.of("id", UUID.randomUUID().toString(),
                   "name", "People CSV",
                   "sourceType", "CSV",
                   "targetNamespace", "http://example.org/people/",
                   "version", 2)
        );
        expectMappingsLookup(HttpStatus.OK, objectMapper.writeValueAsString(mappings));
        expectReleasesLookup(HttpStatus.OK, "[]");
        expectProjectLookup(HttpStatus.OK,
            objectMapper.writeValueAsString(Map.of("id", projectId.toString(), "name", "Alpha")));

        SemanticApiDoc doc = service.generate(projectId, user);

        assertThat(doc.projectName()).isEqualTo("Alpha");
        assertThat(doc.mappingSummary().mappingCount()).isEqualTo(1);
        assertThat(doc.mappingSummary().mappings().get(0).name()).isEqualTo("People CSV");
        assertThat(doc.mappingSummary().sourceTypes()).containsExactly("CSV");
        mockServer.verify();
    }

    // -----------------------------------------------------------------
    // Fail-loud behaviour — the key regression guard
    // -----------------------------------------------------------------

    @Test
    void generate_downstream401_throwsAuthException() {
        // Mappings is the FIRST downstream call — a 401 there aborts the
        // whole generate(), nothing else is called.
        expectMappingsLookup(HttpStatus.UNAUTHORIZED, "{}");

        assertThatThrownBy(() -> service.generate(projectId, user))
            .isInstanceOf(DocGenDownstreamAuthException.class)
            .hasMessageContaining("pipeline-service");
    }

    @Test
    void generate_downstreamProject401_throwsAuthException() throws Exception {
        // If mappings + releases succeed but project lookup 401s, the
        // service must still surface that — never swallow.
        expectMappingsLookup(HttpStatus.OK, "[]");
        expectReleasesLookup(HttpStatus.OK, "[]");
        expectProjectLookup(HttpStatus.UNAUTHORIZED, "{}");

        assertThatThrownBy(() -> service.generate(projectId, user))
            .isInstanceOf(DocGenDownstreamAuthException.class);
    }

    @Test
    void generate_downstream404_throwsResourceNotFound() {
        // 404 on the first call (mappings) — treated as project-not-found.
        expectMappingsLookup(HttpStatus.NOT_FOUND, "{}");

        assertThatThrownBy(() -> service.generate(projectId, user))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(projectId.toString());
    }

    @Test
    void generate_downstream5xx_throwsGenerationException() {
        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/mappings?projectId=" + projectId))
            .andRespond(withServerError());

        assertThatThrownBy(() -> service.generate(projectId, user))
            .isInstanceOf(DocGenGenerationException.class);
    }

    @Test
    void generate_forwardsIdentityHeaders() throws Exception {
        // Call order: mappings → releases → project.
        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/mappings?projectId=" + projectId))
            .andExpect(header("X-User-Id", user.id().toString()))
            .andExpect(header("X-User-Email", "alice@example.com"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/releases?projectId=" + projectId))
            .andExpect(header("X-User-Id", user.id().toString()))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/projects/" + projectId))
            .andExpect(header("X-User-Id", user.id().toString()))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(Map.of("name", "Alpha")),
                MediaType.APPLICATION_JSON));

        SemanticApiDoc doc = service.generate(projectId, user);
        assertThat(doc).isNotNull();
        mockServer.verify();
    }

    // -----------------------------------------------------------------
    // Endpoint resolution: synthetic vs real
    // -----------------------------------------------------------------

    @Test
    void generate_noPublishedRelease_endpointMarkedSynthetic() throws Exception {
        expectMappingsLookup(HttpStatus.OK, "[]");
        // Only drafts exist.
        expectReleasesLookup(HttpStatus.OK,
            "[{\"status\":\"DRAFT\",\"version\":\"0.1.0\"}]");
        expectProjectLookup(HttpStatus.OK, "{\"name\":\"Alpha\"}");

        SemanticApiDoc doc = service.generate(projectId, user);

        assertThat(doc.endpoints()).hasSize(1);
        SemanticApiDoc.EndpointInfo ep = doc.endpoints().get(0);
        assertThat(ep.synthetic()).isTrue();
        assertThat(ep.publishedGraph()).startsWith("urn:rdfforge:project:");
        assertThat(ep.metadata()).containsKey("note");
    }

    @Test
    void generate_publishedReleaseExists_usesRealEndpoint() throws Exception {
        expectMappingsLookup(HttpStatus.OK, "[]");

        UUID tsId = UUID.randomUUID();
        String targetGraph = "http://data.example.org/graphs/alpha/v1";
        Map<String, Object> manifest = new HashMap<>();
        Map<String, Object> refs = new HashMap<>();
        refs.put("triplestoreId", tsId.toString());
        refs.put("targetGraph", targetGraph);
        manifest.put("refs", refs);
        List<Map<String, Object>> releases = List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "status", "PUBLISHED",
                "version", "1.0.0",
                "publishedAt", "2026-01-01T00:00:00Z",
                "manifest", manifest
            )
        );
        expectReleasesLookup(HttpStatus.OK, objectMapper.writeValueAsString(releases));
        expectProjectLookup(HttpStatus.OK, "{\"name\":\"Alpha\"}");

        SemanticApiDoc doc = service.generate(projectId, user);

        assertThat(doc.endpoints()).hasSize(1);
        SemanticApiDoc.EndpointInfo ep = doc.endpoints().get(0);
        assertThat(ep.synthetic()).isFalse();
        assertThat(ep.publishedGraph()).isEqualTo(targetGraph);
        assertThat(ep.metadata()).containsEntry("releaseVersion", "1.0.0");
        assertThat(ep.metadata()).containsEntry("triplestoreId", tsId.toString());
    }

    // -----------------------------------------------------------------
    // Identity not present — anonymous users should NOT get through
    // -----------------------------------------------------------------

    @Test
    void generate_anonymousUser_downstream401_throwsAuthException() {
        // forwardedIdentity returns no headers for anonymous; pipeline
        // replies 401, which maps to DocGenDownstreamAuthException.
        // (Mappings is the first downstream call.)
        expectMappingsLookup(HttpStatus.UNAUTHORIZED, "{}");

        assertThatThrownBy(() -> service.generate(projectId, AuthUser.anonymous()))
            .isInstanceOf(DocGenDownstreamAuthException.class);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void expectProjectLookup(HttpStatus status, String body) {
        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/projects/" + projectId))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andRespond(withStatus(status)
                .body(body == null ? "" : body)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private void expectMappingsLookup(HttpStatus status, String body) {
        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/mappings?projectId=" + projectId))
            .andRespond(withStatus(status)
                .body(body == null ? "" : body)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private void expectReleasesLookup(HttpStatus status, String body) {
        mockServer.expect(ExpectedCount.once(),
                requestTo(PIPELINE_URL + "/api/v1/releases?projectId=" + projectId))
            .andRespond(withStatus(status)
                .body(body == null ? "" : body)
                .contentType(MediaType.APPLICATION_JSON));
    }
}
