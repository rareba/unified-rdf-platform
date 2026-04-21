package io.rdfforge.shacl.controller;

import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.common.security.CurrentUserAutoConfiguration;
import io.rdfforge.shacl.docs.ApiDocFormat;
import io.rdfforge.shacl.docs.DocGenService;
import io.rdfforge.shacl.docs.SemanticApiDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link DocGenController}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Unauthenticated calls are rejected by {@code @CurrentUser} resolver (401).</li>
 *   <li>HTML rendering still escapes user-supplied labels (no XSS regression).</li>
 * </ul>
 */
@WebMvcTest(DocGenController.class)
@Import({CurrentUserAutoConfiguration.class, GlobalExceptionHandler.class, io.rdfforge.shacl.config.TestSecurityConfig.class})
class DocGenControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;

    @MockBean private DocGenService docGenService;

    @Test
    void generate_unauthenticated_returns401() throws Exception {
        UUID projectId = UUID.randomUUID();
        // No X-User-Id header → CurrentUserArgumentResolver throws 401.
        mockMvc.perform(get("/api/v1/docs/project/{id}", projectId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void generate_returnsHtmlWithEscapedLabels() throws Exception {
        UUID projectId = UUID.randomUUID();
        String maliciousName = "<script>alert('xss')</script>Proj";

        SemanticApiDoc doc = new SemanticApiDoc(
            projectId,
            maliciousName,
            Instant.parse("2026-04-01T00:00:00Z"),
            new SemanticApiDoc.OntologySummary(0, 0, 0, 0, List.of(), List.of()),
            new SemanticApiDoc.MappingSummary(0, List.of(), List.of(), List.of()),
            List.of(new SemanticApiDoc.EndpointInfo(
                "SPARQL",
                "http://triplestore/api/v1/sparql",
                "urn:rdfforge:project:" + projectId,
                true,
                Map.of("note", "No published release; URI follows project convention")
            )),
            List.of()
        );

        when(docGenService.generate(eq(projectId), any())).thenReturn(doc);
        // Delegate to the real renderer so we verify escape behaviour.
        when(docGenService.render(eq(doc), eq(ApiDocFormat.HTML)))
            .thenAnswer(inv -> renderRealHtml(doc));

        mockMvc.perform(get("/api/v1/docs/project/{id}", projectId)
                .param("format", "HTML")
                .header("X-User-Id", USER_ID))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("<script>alert"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "&lt;script&gt;alert")))
            // Synthetic endpoint must be visibly labeled.
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "(example - not yet published)")));
    }

    /** Invoke the real renderer so escape logic is actually exercised. */
    private String renderRealHtml(SemanticApiDoc doc) {
        DocGenService real = new DocGenService(null, null);
        return real.render(doc, ApiDocFormat.HTML);
    }
}
