package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchStatsDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchCandidateController.class)
class MatchCandidateControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MatchCandidateService service;

    @Test
    void listCandidates_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(service.list(eq(projectId), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/reconciliation/candidates")
                    .param("projectId", projectId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void stats_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(service.stats(eq(projectId), any()))
            .thenReturn(new MatchStatsDto(projectId, 1, 2, 3, 4, Map.of(), Map.of()));
        mockMvc.perform(get("/api/v1/reconciliation/stats")
                    .param("projectId", projectId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void matchers_returns200() throws Exception {
        when(service.listMatchers(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/reconciliation/matchers"))
                .andExpect(status().isOk());
    }
}
