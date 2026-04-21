package io.rdfforge.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Object-level authorization tests for PipelineController.
 *
 * <p>Exercises the ownership check added 2026-04-21 — unauthenticated/other-user
 * access is rejected, owner access works, admin bypasses ownership.
 */
@WebMvcTest(PipelineController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("PipelineController Security Tests")
class PipelineControllerSecurityTest {

    private static final UUID OWNER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OTHER_USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID ADMIN_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PipelineService pipelineService;

    @MockBean
    private OperationRegistry operationRegistry;

    private UUID pipelineId;
    private Pipeline ownerPipeline;

    @BeforeEach
    void setUp() {
        pipelineId = UUID.randomUUID();
        ownerPipeline = Pipeline.builder()
            .id(pipelineId)
            .name("Owner's Pipeline")
            .definition("{\"steps\":[]}")
            .definitionFormat(Pipeline.DefinitionFormat.JSON)
            .version(1)
            .createdBy(OWNER_ID)
            .createdAt(Instant.now())
            .build();
        when(pipelineService.getById(pipelineId)).thenReturn(ownerPipeline);
    }

    @Test
    @DisplayName("GET /{id} without X-User-Id → 401")
    void getPipeline_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id} as non-owner → 403")
    void getPipeline_NonOwner_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", OTHER_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /{id} as owner → 200")
    void getPipeline_Owner_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", OWNER_ID.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} as admin → 200 even for someone else's pipeline")
    void getPipeline_Admin_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", ADMIN_ID.toString())
                .header("X-User-Roles", "ROLE_ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /{id} as non-owner → 403 and service.delete not called")
    void deletePipeline_NonOwner_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", OTHER_USER_ID.toString()))
            .andExpect(status().isForbidden());
        verify(pipelineService, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /{id} as owner → 204")
    void deletePipeline_Owner_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", OWNER_ID.toString()))
            .andExpect(status().isNoContent());
        verify(pipelineService).delete(pipelineId);
    }

    @Test
    @DisplayName("PUT /{id} as non-owner → 403 and service.update not called")
    void updatePipeline_NonOwner_Returns403() throws Exception {
        mockMvc.perform(put("/api/v1/pipelines/{id}", pipelineId)
                .header("X-User-Id", OTHER_USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ownerPipeline)))
            .andExpect(status().isForbidden());
        verify(pipelineService, never()).update(any(), any());
    }

    @Test
    @DisplayName("POST / (create) overwrites client-supplied createdBy with gateway id")
    void createPipeline_OverridesClientSuppliedOwner() throws Exception {
        // Even if a malicious client sends createdBy=someoneElseId, the
        // controller must overwrite it with the gateway-trusted identity.
        Pipeline attacker = Pipeline.builder()
            .name("MineNow")
            .definition("{\"steps\":[]}")
            .definitionFormat(Pipeline.DefinitionFormat.JSON)
            .createdBy(OTHER_USER_ID) // attempted spoof
            .build();
        when(pipelineService.create(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/pipelines")
                .header("X-User-Id", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(attacker)))
            .andExpect(status().isCreated());

        verify(pipelineService).create(argThat(p ->
            OWNER_ID.equals(p.getCreatedBy()) && OWNER_ID.equals(p.getUpdatedBy())
        ));
    }

    @Test
    @DisplayName("List endpoint remains accessible without X-User-Id (not gated)")
    void listPipelines_Unauthenticated_Returns200() throws Exception {
        // Listing is not gated; per-item ownership is still enforced when an
        // individual pipeline is fetched by id.
        when(pipelineService.list(any(), any())).thenReturn(
            new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        mockMvc.perform(get("/api/v1/pipelines"))
            .andExpect(status().isOk());
    }
}
