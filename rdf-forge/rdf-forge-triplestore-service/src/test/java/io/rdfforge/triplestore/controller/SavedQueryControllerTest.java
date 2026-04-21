package io.rdfforge.triplestore.controller;

import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunResponse;
import io.rdfforge.triplestore.entity.SavedQueryEntity.QueryType;
import io.rdfforge.triplestore.service.SavedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SavedQueryController.class)
class SavedQueryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SavedQueryService savedQueryService;

    @Test
    void list_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(savedQueryService.list(eq(projectId), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/sparql/queries").param("projectId", projectId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void runInline_returns200() throws Exception {
        when(savedQueryService.runInline(any(), any())).thenReturn(
            new SavedQueryRunResponse(QueryType.SELECT, List.of("s"), List.of(), null, null, null, 1L, Instant.now())
        );
        mockMvc.perform(post("/api/v1/sparql/run")
                    .contentType("application/json")
                    .content("{\"queryText\":\"SELECT * WHERE { ?s ?p ?o }\"," +
                             "\"triplestoreId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void get_forbidden_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(savedQueryService.get(eq(id), any())).thenThrow(new AccessDeniedException("nope"));
        mockMvc.perform(get("/api/v1/sparql/queries/" + id))
                .andExpect(status().is4xxClientError());
    }
}
