package io.rdfforge.pipeline.lineage;

import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.dto.LineageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link LineageController}.
 */
@WebMvcTest(LineageController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("LineageController Tests")
class LineageControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;
    @MockBean private LineageService lineageService;

    @Test
    @DisplayName("GET /project/{id} returns lineage graph")
    void project_returnsGraph() throws Exception {
        UUID projectId = UUID.randomUUID();
        LineageDto dto = new LineageDto(
            projectId,
            List.of(new LineageDto.Node(
                "uuid:project-" + projectId, LineageDto.NodeKind.PROJECT,
                "P", Instant.now(), Map.of())),
            List.of());
        when(lineageService.forProject(eq(projectId), any())).thenReturn(dto);

        mockMvc.perform(get("/api/v1/lineage/project/{id}", projectId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId.toString()))
            .andExpect(jsonPath("$.nodes", hasSize(1)));
    }

    @Test
    @DisplayName("GET /project/{id} without auth → 401")
    void project_unauth() throws Exception {
        mockMvc.perform(get("/api/v1/lineage/project/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /resource/{kind}/{id} returns focused subgraph")
    void resource_returnsSubgraph() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        LineageDto dto = new LineageDto(
            projectId,
            List.of(new LineageDto.Node(
                "uuid:mapping-" + mappingId, LineageDto.NodeKind.MAPPING,
                "map", Instant.now(), Map.of())),
            List.of());
        when(lineageService.forResource(eq("MAPPING"), eq(mappingId), any()))
            .thenReturn(dto);

        mockMvc.perform(get("/api/v1/lineage/resource/{kind}/{id}", "MAPPING", mappingId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes[0].kind").value("MAPPING"));
    }

    @Test
    @DisplayName("GET /resource non-owner → 403")
    void resource_forbidden() throws Exception {
        when(lineageService.forResource(any(), any(), any()))
            .thenThrow(new AccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/lineage/resource/{kind}/{id}",
                "MAPPING", UUID.randomUUID())
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isForbidden());
    }
}
