package io.rdfforge.shacl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.OntologyService;
import io.rdfforge.shacl.ontology.dto.OntologyDto;
import io.rdfforge.shacl.ontology.dto.OntologyImportRequest;
import io.rdfforge.shacl.ontology.dto.TermSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.rdfforge.common.security.CurrentUserAutoConfiguration;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for OntologyController. Requests include the gateway-forwarded
 * X-User-Id header so the {@code @CurrentUser} resolver resolves successfully.
 */
@WebMvcTest(OntologyController.class)
@Import(CurrentUserAutoConfiguration.class)
class OntologyControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OntologyService ontologyService;

    @Test
    void listRequiresProjectId() throws Exception {
        mockMvc.perform(get("/api/v1/ontologies")
                .header("X-User-Id", USER_ID))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsOntologiesForProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        OntologyDto dto = OntologyDto.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .name("Test Ontology")
            .namespace("http://example.org/")
            .format(RdfFormat.TURTLE)
            .version(1)
            .build();

        when(ontologyService.listByProject(any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/ontologies")
                .param("projectId", projectId.toString())
                .header("X-User-Id", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Test Ontology"));
    }

    @Test
    void importOntologyReturnsCreated() throws Exception {
        UUID projectId = UUID.randomUUID();
        OntologyImportRequest req = new OntologyImportRequest(
            projectId, "New Ontology", null, RdfFormat.TURTLE,
            "@prefix ex: <http://example.org/> .", null, null);
        OntologyDto dto = OntologyDto.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .name("New Ontology")
            .namespace("http://example.org/")
            .format(RdfFormat.TURTLE)
            .version(1)
            .build();

        when(ontologyService.importOntology(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/ontologies/import")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Ontology"));
    }

    @Test
    void classesEndpointReturnsTerms() throws Exception {
        UUID id = UUID.randomUUID();
        TermSearchResult term = TermSearchResult.builder()
            .uri("http://example.org/schema/Person")
            .type("CLASS")
            .label("Person")
            .build();

        when(ontologyService.searchTerms(any(), any(), any(), anyInt(), any()))
            .thenReturn(List.of(term));

        mockMvc.perform(get("/api/v1/ontologies/{id}/classes", id)
                .header("X-User-Id", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].uri").value("http://example.org/schema/Person"))
            .andExpect(jsonPath("$[0].label").value("Person"));
    }
}
