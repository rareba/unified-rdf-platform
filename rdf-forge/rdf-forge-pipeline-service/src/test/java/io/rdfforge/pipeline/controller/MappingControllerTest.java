package io.rdfforge.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.dto.ExplainResponse;
import io.rdfforge.pipeline.dto.MappingCreateRequest;
import io.rdfforge.pipeline.dto.MappingDto;
import io.rdfforge.pipeline.dto.MappingPreviewResponse;
import io.rdfforge.pipeline.dto.MappingValidationResponse;
import io.rdfforge.pipeline.dto.TripleDto;
import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.SourceType;
import io.rdfforge.pipeline.service.MappingService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link MappingController}. Mirrors the auth model used
 * by {@link ProjectControllerTest} — the service layer is mocked, the
 * controller plus {@code @CurrentUser} resolution plus {@code @Valid} are
 * exercised.
 */
@WebMvcTest(MappingController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("MappingController Tests")
class MappingControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USER_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MappingService mappingService;

    private UUID projectId;
    private UUID mappingId;
    private MappingDto sampleDto;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        sampleDto = new MappingDto(
            mappingId, projectId, "My Map", "desc",
            SourceType.CSV, Map.of("delimiter", ","),
            "https://ex.org/", null,
            List.of(), MappingType.GENERIC, 1, OWNER_ID,
            Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /?projectId=X lists mappings for the project")
    void list_returnsOk() throws Exception {
        when(mappingService.listByProject(eq(projectId), any()))
            .thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/v1/mappings").param("projectId", projectId.toString())
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(mappingId.toString()));
    }

    @Test
    @DisplayName("GET / without X-User-Id → 401")
    void list_unauth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/mappings").param("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST / creates mapping and returns 201")
    void create_returns201() throws Exception {
        MappingCreateRequest req = new MappingCreateRequest(
            projectId, "My Map", "desc", SourceType.CSV,
            Map.of("delimiter", ","), "https://ex.org/", null,
            List.of(), MappingType.GENERIC);
        when(mappingService.create(any(MappingCreateRequest.class), any()))
            .thenReturn(sampleDto);

        mockMvc.perform(post("/api/v1/mappings")
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(mappingId.toString()));
    }

    @Test
    @DisplayName("POST / invalid body (missing projectId) → 400")
    void create_missingProjectId_returns400() throws Exception {
        String body = "{\"name\":\"X\",\"sourceType\":\"CSV\"}";
        mockMvc.perform(post("/api/v1/mappings")
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id} non-owner → 403 (service throws AccessDenied)")
    void get_forbidden() throws Exception {
        when(mappingService.findById(eq(mappingId), any()))
            .thenThrow(new AccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/mappings/{id}", mappingId)
                .header(USER_HEADER, OTHER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{id}/preview returns triples list")
    void preview_returnsTriples() throws Exception {
        MappingPreviewResponse resp = new MappingPreviewResponse(
            List.of(new TripleDto("s", "p", "o", TripleDto.ObjectType.URI, null, null)),
            1, 1);
        when(mappingService.preview(eq(mappingId), any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/mappings/{id}/preview", mappingId)
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceRows\":[{\"id\":\"1\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sampleSize").value(1))
            .andExpect(jsonPath("$.triples", hasSize(1)));
    }

    @Test
    @DisplayName("POST /{id}/explain returns row trace")
    void explain_returnsRows() throws Exception {
        ExplainResponse resp = new ExplainResponse(List.of());
        when(mappingService.explain(eq(mappingId), any(), any())).thenReturn(resp);
        mockMvc.perform(post("/api/v1/mappings/{id}/explain", mappingId)
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceRows\":[]}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/validate returns issue list")
    void validate_returnsIssues() throws Exception {
        MappingValidationResponse resp = new MappingValidationResponse(true, List.of());
        when(mappingService.validate(eq(mappingId), any(), any())).thenReturn(resp);
        mockMvc.perform(post("/api/v1/mappings/{id}/validate", mappingId)
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("DELETE /{id} as owner → 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/mappings/{id}", mappingId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("list without projectId query param → 400")
    void list_noProjectId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/mappings")
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isBadRequest());
    }

    // Force MappingRule / MappingType onto classpath for Jackson test;
    // also exercises DTO deserialization in another test.
    @Test
    @DisplayName("POST / with full rule list deserializes correctly")
    void create_withRules_deserializes() throws Exception {
        MappingRule r = new MappingRule(
            "r1", MappingRule.RuleType.FIXED_URI, null, null,
            "${baseUri}a", null, null, null);
        MappingCreateRequest req = new MappingCreateRequest(
            projectId, "R", null, SourceType.CSV, null, "https://ex.org/", null,
            List.of(r), MappingType.GENERIC);
        when(mappingService.create(any(), any())).thenReturn(sampleDto);

        mockMvc.perform(post("/api/v1/mappings")
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());
    }
}
