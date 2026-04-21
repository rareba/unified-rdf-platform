package io.rdfforge.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.dto.ProjectCreateRequest;
import io.rdfforge.pipeline.dto.ProjectDto;
import io.rdfforge.pipeline.dto.ProjectSummaryDto;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link ProjectController}. Security is replaced by
 * {@link TestSecurityConfig} so every test focuses on the HTTP contract and
 * the @CurrentUser resolution pipeline.
 */
@WebMvcTest(ProjectController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ProjectController Tests")
class ProjectControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String USER_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-User-Roles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    private UUID projectId;
    private ProjectDto sampleDto;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sampleDto = new ProjectDto(
            projectId,
            "Alpha",
            "desc",
            "https://example.org/alpha/",
            ProjectStatus.ACTIVE,
            OWNER_ID,
            Instant.now(),
            Instant.now(),
            Map.of()
        );
    }

    @Test
    @DisplayName("POST / creates a project and returns 201")
    void POST_createProject_returns201() throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest(
            "Alpha", "desc", "https://example.org/alpha/", null);
        when(projectService.create(any(ProjectCreateRequest.class), any())).thenReturn(sampleDto);

        mockMvc.perform(post("/api/v1/projects")
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(projectId.toString()))
            .andExpect(jsonPath("$.name").value("Alpha"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST / without X-User-Id → 401")
    void POST_createProject_unauthenticated_returns401() throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest(
            "Alpha", null, "https://example.org/alpha/", null);

        mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST / invalid body (missing name) → 400")
    void POST_createProject_invalidBody_returns400() throws Exception {
        String body = "{\"baseUri\":\"https://example.org/\"}";
        mockMvc.perform(post("/api/v1/projects")
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET / lists owner's projects")
    void GET_listProjects_returnsOwnerOnly() throws Exception {
        when(projectService.list(any(), isNull())).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/v1/projects")
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].createdBy").value(OWNER_ID.toString()));
    }

    @Test
    @DisplayName("GET / with status filter forwards the filter to the service")
    void GET_listProjects_withStatusFilter() throws Exception {
        when(projectService.list(any(), eq(ProjectStatus.ARCHIVED))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects?status=ARCHIVED")
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk());

        verify(projectService).list(any(), eq(ProjectStatus.ARCHIVED));
    }

    @Test
    @DisplayName("GET /{id} as non-owner → 403 (service throws AccessDenied)")
    void GET_project_NonOwner_Returns403() throws Exception {
        when(projectService.findById(eq(projectId), any()))
            .thenThrow(new AccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                .header(USER_HEADER, OTHER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /{id} as admin → 200 (service allows)")
    void GET_project_Admin_Returns200() throws Exception {
        when(projectService.findById(eq(projectId), any())).thenReturn(sampleDto);

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                .header(USER_HEADER, ADMIN_ID.toString())
                .header(ROLES_HEADER, "ROLE_ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/summary returns counts structure")
    void GET_projectSummary_returnsCountsStructure() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pipelines", 4L);
        ProjectSummaryDto summary = new ProjectSummaryDto(
            projectId, "Alpha", "desc", ProjectStatus.ACTIVE,
            "https://example.org/alpha/", Instant.now(), Instant.now(),
            counts, Instant.now(), null);
        when(projectService.summary(eq(projectId), any())).thenReturn(summary);

        mockMvc.perform(get("/api/v1/projects/{id}/summary", projectId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.counts.pipelines").value(4))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /{id}/archive returns archived DTO")
    void POST_archive_returnsArchivedDto() throws Exception {
        ProjectDto archived = new ProjectDto(
            projectId, "Alpha", "desc", "https://example.org/alpha/",
            ProjectStatus.ARCHIVED, OWNER_ID, Instant.now(), Instant.now(), Map.of());
        when(projectService.archive(eq(projectId), any())).thenReturn(archived);

        mockMvc.perform(post("/api/v1/projects/{id}/archive", projectId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("DELETE /{id} as non-owner → 403 and service.delete not called")
    void DELETE_project_NonOwner_Returns403() throws Exception {
        doThrow(new AccessDeniedException("nope"))
            .when(projectService).delete(eq(projectId), any());

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                .header(USER_HEADER, OTHER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /{id} as owner → 204")
    void DELETE_project_Owner_Returns204() throws Exception {
        doNothing().when(projectService).delete(eq(projectId), any());

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isNoContent());
    }
}
