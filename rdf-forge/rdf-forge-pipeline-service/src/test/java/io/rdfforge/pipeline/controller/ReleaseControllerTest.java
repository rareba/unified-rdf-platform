package io.rdfforge.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.dto.ReleaseBuildResponse;
import io.rdfforge.pipeline.dto.ReleaseCreateRequest;
import io.rdfforge.pipeline.dto.ReleaseDto;
import io.rdfforge.pipeline.entity.ReleaseStatus;
import io.rdfforge.pipeline.service.ReleaseService;
import io.rdfforge.pipeline.service.ReleaseService.ReleaseArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link ReleaseController}. Service is mocked; the
 * @CurrentUser resolution pipeline and @Valid are exercised.
 */
@WebMvcTest(ReleaseController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ReleaseController Tests")
class ReleaseControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USER_HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReleaseService releaseService;

    private UUID projectId;
    private UUID releaseId;
    private ReleaseDto sampleDto;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        releaseId = UUID.randomUUID();
        sampleDto = new ReleaseDto(
            releaseId, projectId, "1.0.0", "release-one", "notes",
            ReleaseStatus.DRAFT, Map.of(), null, 0L,
            OWNER_ID, Instant.now(), Instant.now(), null, null
        );
    }

    @Test
    @DisplayName("GET /?projectId= lists releases")
    void list_returnsOk() throws Exception {
        when(releaseService.list(eq(projectId), any())).thenReturn(List.of(sampleDto));
        mockMvc.perform(get("/api/v1/releases")
                .param("projectId", projectId.toString())
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(releaseId.toString()));
    }

    @Test
    @DisplayName("GET without X-User-Id → 401")
    void list_unauth() throws Exception {
        mockMvc.perform(get("/api/v1/releases").param("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST creates draft and returns 201")
    void create_returns201() throws Exception {
        ReleaseCreateRequest req = new ReleaseCreateRequest(
            "1.0.0", "rel", "notes",
            new ReleaseCreateRequest.ManifestRefs(
                List.of(), List.of(), List.of(), List.of(), null, List.of()));
        when(releaseService.create(eq(projectId), any(), any())).thenReturn(sampleDto);

        mockMvc.perform(post("/api/v1/releases")
                .param("projectId", projectId.toString())
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(releaseId.toString()));
    }

    @Test
    @DisplayName("POST missing body fields → 400")
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/releases")
                .param("projectId", projectId.toString())
                .header(USER_HEADER, OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id} non-owner → 403")
    void get_forbidden() throws Exception {
        when(releaseService.get(eq(releaseId), any()))
            .thenThrow(new AccessDeniedException("nope"));
        mockMvc.perform(get("/api/v1/releases/{id}", releaseId)
                .header(USER_HEADER, OTHER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{id}/build returns build response with artifact uri")
    void build_returnsArtifact() throws Exception {
        ReleaseBuildResponse resp = new ReleaseBuildResponse(
            releaseId, "/tmp/rdf-forge-releases/x.zip", 4242L,
            Map.of("buildCompletedAt", Instant.now().toString()),
            Map.of("passed", true));
        when(releaseService.build(eq(releaseId), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/releases/{id}/build", releaseId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseId").value(releaseId.toString()))
            .andExpect(jsonPath("$.artifactSizeBytes").value(4242));
    }

    @Test
    @DisplayName("POST /{id}/archive returns updated DTO")
    void archive_returnsDto() throws Exception {
        ReleaseDto archived = new ReleaseDto(
            releaseId, projectId, "1.0.0", "rel", null,
            ReleaseStatus.ARCHIVED, Map.of(), null, 0L,
            OWNER_ID, Instant.now(), Instant.now(), null, null);
        when(releaseService.archive(eq(releaseId), any())).thenReturn(archived);

        mockMvc.perform(post("/api/v1/releases/{id}/archive", releaseId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("DELETE /{id} returns 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/releases/{id}", releaseId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /{id}/manifest returns JSON object")
    void manifest_returnsObject() throws Exception {
        when(releaseService.getManifest(eq(releaseId), any()))
            .thenReturn(Map.of("refs", Map.of("mappings", List.of())));
        mockMvc.perform(get("/api/v1/releases/{id}/manifest", releaseId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refs").exists());
    }

    @Test
    @DisplayName("GET /{id}/download streams application/zip with Content-Disposition")
    void download_streamsZip() throws Exception {
        byte[] zipBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04, 0x00 }; // PK header
        ReleaseArtifact art = new ReleaseArtifact(
            new ByteArrayResource(zipBytes), "rel-1.0.0.zip", zipBytes.length);
        when(releaseService.download(eq(releaseId), any())).thenReturn(art);

        mockMvc.perform(get("/api/v1/releases/{id}/download", releaseId)
                .header(USER_HEADER, OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Content-Disposition",
                containsString("attachment; filename=\"rel-1.0.0.zip\"")));
    }
}
