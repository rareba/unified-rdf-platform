package io.rdfforge.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.config.TestSecurityConfig;
import io.rdfforge.pipeline.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for PipelineController.
 *
 * Security is disabled via TestSecurityConfig so every test concentrates on
 * the HTTP contract: routing, request de-serialization, response serialization,
 * status codes, and the delegation to PipelineService.
 */
@WebMvcTest(PipelineController.class)
@Import({TestSecurityConfig.class, io.rdfforge.common.exception.GlobalExceptionHandler.class})
@DisplayName("PipelineController Tests")
class PipelineControllerTest {

    // Ownership authz added 2026-04-21 — every test that hits a gated endpoint
    // must send X-User-Id matching samplePipeline.createdBy (or admin roles).
    private static final UUID TEST_USER_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USER_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PipelineService pipelineService;

    @MockBean
    private OperationRegistry operationRegistry;

    private UUID pipelineId;
    private Pipeline samplePipeline;

    @BeforeEach
    void setUp() {
        pipelineId = UUID.randomUUID();

        samplePipeline = Pipeline.builder()
            .id(pipelineId)
            .name("My Pipeline")
            .description("Test pipeline")
            .definitionFormat(Pipeline.DefinitionFormat.JSON)
            .definition("{\"steps\":[{\"id\":\"s1\",\"operation\":\"load-csv\"}]}")
            .variables(Map.of())
            .version(1)
            .createdBy(TEST_USER_ID) // match TEST_USER_ID for authz
            .createdAt(Instant.now())
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines — list pipelines")
    class ListPipelinesTests {

        @Test
        @DisplayName("Should return 200 with a page of pipelines")
        void listPipelines_WithoutFilters_Returns200WithPage() throws Exception {
            Page<Pipeline> page = new PageImpl<>(List.of(samplePipeline));
            when(pipelineService.list(isNull(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/pipelines"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(pipelineId.toString())))
                .andExpect(jsonPath("$.content[0].name", is("My Pipeline")));
        }

        @Test
        @DisplayName("Should return 200 with empty page when no pipelines exist")
        void listPipelines_NoResults_Returns200WithEmptyPage() throws Exception {
            Page<Pipeline> empty = new PageImpl<>(List.of());
            when(pipelineService.list(isNull(), any(Pageable.class))).thenReturn(empty);

            mockMvc.perform(get("/api/v1/pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("Should pass projectId filter to service when provided")
        void listPipelines_WithProjectId_PassesFilterToService() throws Exception {
            UUID projectId = UUID.randomUUID();
            Page<Pipeline> page = new PageImpl<>(List.of(samplePipeline));
            when(pipelineService.list(eq(projectId), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/pipelines")
                    .param("projectId", projectId.toString()))
                .andExpect(status().isOk());

            verify(pipelineService).list(eq(projectId), any(Pageable.class));
        }

        @Test
        @DisplayName("Should delegate to search() service method when 'search' query param is present")
        void listPipelines_WithSearchParam_DelegatesToSearchMethod() throws Exception {
            Page<Pipeline> page = new PageImpl<>(List.of(samplePipeline));
            when(pipelineService.search(isNull(), eq("etl"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/pipelines").param("search", "etl"))
                .andExpect(status().isOk());

            verify(pipelineService).search(isNull(), eq("etl"), any(Pageable.class));
            verify(pipelineService, never()).list(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/pipelines
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines — create pipeline")
    class CreatePipelineTests {

        @Test
        @DisplayName("Should return 201 with the created pipeline body")
        void createPipeline_ValidRequest_Returns201WithBody() throws Exception {
            when(pipelineService.create(any(Pipeline.class))).thenReturn(samplePipeline);

            mockMvc.perform(post("/api/v1/pipelines")
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(samplePipeline)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(pipelineId.toString())))
                .andExpect(jsonPath("$.name", is("My Pipeline")));
        }

        @Test
        @DisplayName("Should return 400 when name is blank (@Valid enforcement)")
        void createPipeline_BlankName_Returns400() throws Exception {
            Pipeline invalid = Pipeline.builder()
                .name("")   // @NotBlank violation
                .definition("{\"steps\":[]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            mockMvc.perform(post("/api/v1/pipelines")
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

            verify(pipelineService, never()).create(any());
        }

        @Test
        @DisplayName("Should return 400 when definition is blank (@Valid enforcement)")
        void createPipeline_BlankDefinition_Returns400() throws Exception {
            Pipeline invalid = Pipeline.builder()
                .name("Valid Name")
                .definition("")  // @NotBlank violation
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            mockMvc.perform(post("/api/v1/pipelines")
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

            verify(pipelineService, never()).create(any());
        }

        @Test
        @DisplayName("Should return 400 when name contains invalid characters")
        void createPipeline_InvalidCharsInName_Returns400() throws Exception {
            Pipeline invalid = Pipeline.builder()
                .name("Pipe<line>!")   // @Pattern violation
                .definition("{\"steps\":[]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            mockMvc.perform(post("/api/v1/pipelines")
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/{id} — get pipeline by ID")
    class GetPipelineByIdTests {

        @Test
        @DisplayName("Should return 200 with pipeline when it exists")
        void getById_ExistingId_Returns200WithPipeline() throws Exception {
            when(pipelineService.getById(pipelineId)).thenReturn(samplePipeline);

            mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId).header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(pipelineId.toString())))
                .andExpect(jsonPath("$.name", is("My Pipeline")));
        }

        @Test
        @DisplayName("Should return 404 when pipeline does not exist")
        void getById_NonExistentId_Returns404() throws Exception {
            when(pipelineService.getById(pipelineId))
                .thenThrow(new ResourceNotFoundException("Pipeline", pipelineId.toString()));

            mockMvc.perform(get("/api/v1/pipelines/{id}", pipelineId).header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/pipelines/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/pipelines/{id} — update pipeline")
    class UpdatePipelineTests {

        @Test
        @DisplayName("Should return 200 with updated pipeline on successful update")
        void updatePipeline_ValidRequest_Returns200WithUpdatedPipeline() throws Exception {
            Pipeline updated = Pipeline.builder()
                .id(pipelineId)
                .name("Updated Name")
                .definition("{\"steps\":[{\"id\":\"s2\",\"operation\":\"noop\"}]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .version(2)
                .createdBy(TEST_USER_ID)
                .build();
            // Controller now fetches existing pipeline first for authz.
            when(pipelineService.getById(pipelineId)).thenReturn(samplePipeline);
            when(pipelineService.update(eq(pipelineId), any(Pipeline.class))).thenReturn(updated);

            mockMvc.perform(put("/api/v1/pipelines/{id}", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.version", is(2)));
        }

        @Test
        @DisplayName("Should return 404 when updating a non-existent pipeline")
        void updatePipeline_NotFound_Returns404() throws Exception {
            // The authz pre-fetch surfaces missing pipelines as 404 now,
            // before the update call is ever reached.
            when(pipelineService.getById(pipelineId))
                .thenThrow(new ResourceNotFoundException("Pipeline", pipelineId.toString()));

            mockMvc.perform(put("/api/v1/pipelines/{id}", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(samplePipeline)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when update body fails @Valid (blank name)")
        void updatePipeline_InvalidBody_Returns400() throws Exception {
            Pipeline invalid = Pipeline.builder()
                .name("")
                .definition("{\"steps\":[]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            mockMvc.perform(put("/api/v1/pipelines/{id}", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

            verify(pipelineService, never()).update(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/pipelines/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/pipelines/{id} — delete pipeline")
    class DeletePipelineTests {

        @Test
        @DisplayName("Should return 204 No Content on successful deletion")
        void deletePipeline_ExistingId_Returns204() throws Exception {
            when(pipelineService.getById(pipelineId)).thenReturn(samplePipeline);
            doNothing().when(pipelineService).delete(pipelineId);

            mockMvc.perform(delete("/api/v1/pipelines/{id}", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNoContent());

            verify(pipelineService).delete(pipelineId);
        }

        @Test
        @DisplayName("Should return 404 when deleting a non-existent pipeline")
        void deletePipeline_NotFound_Returns404() throws Exception {
            // The authz pre-fetch catches missing pipelines before reaching delete.
            when(pipelineService.getById(pipelineId))
                .thenThrow(new ResourceNotFoundException("Pipeline", pipelineId.toString()));

            mockMvc.perform(delete("/api/v1/pipelines/{id}", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/pipelines/{id}/duplicate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/{id}/duplicate — duplicate pipeline")
    class DuplicatePipelineTests {

        @Test
        @DisplayName("Should return 201 with duplicated pipeline")
        void duplicatePipeline_ExistingId_Returns201WithCopy() throws Exception {
            UUID copyId = UUID.randomUUID();
            Pipeline copy = Pipeline.builder()
                .id(copyId)
                .name("My Pipeline (copy)")
                .definition(samplePipeline.getDefinition())
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .createdBy(TEST_USER_ID)
                .build();
            when(pipelineService.getById(pipelineId)).thenReturn(samplePipeline);
            when(pipelineService.duplicate(pipelineId, null)).thenReturn(copy);
            // The controller reassigns ownership after duplicate; stub the update.
            when(pipelineService.update(eq(copyId), any(Pipeline.class))).thenReturn(copy);

            mockMvc.perform(post("/api/v1/pipelines/{id}/duplicate", pipelineId)
                    .header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", containsString("copy")));
        }

        @Test
        @DisplayName("Should pass custom name to service when 'newName' param is given")
        void duplicatePipeline_WithNewName_PassesNameToService() throws Exception {
            when(pipelineService.getById(pipelineId)).thenReturn(samplePipeline);
            when(pipelineService.duplicate(pipelineId, "Custom Name")).thenReturn(samplePipeline);
            when(pipelineService.update(eq(pipelineId), any(Pipeline.class))).thenReturn(samplePipeline);

            mockMvc.perform(post("/api/v1/pipelines/{id}/duplicate", pipelineId)
                    .param("newName", "Custom Name")
                    .header(USER_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isCreated());

            verify(pipelineService).duplicate(pipelineId, "Custom Name");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/pipelines/validate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/validate — validate pipeline definition")
    class ValidatePipelineTests {

        @Test
        @DisplayName("Should return 200 with validation result for a valid pipeline")
        void validatePipeline_ValidDefinition_Returns200() throws Exception {
            PipelineService.ValidationResult validResult =
                new PipelineService.ValidationResult(true, List.of(), List.of());
            when(pipelineService.validate(any(Pipeline.class))).thenReturn(validResult);

            mockMvc.perform(post("/api/v1/pipelines/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(samplePipeline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.errors", hasSize(0)));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines/templates
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/templates — list templates")
    class GetTemplatesTests {

        @Test
        @DisplayName("Should return 200 with list of template pipelines")
        void getTemplates_WithTemplates_Returns200WithList() throws Exception {
            Pipeline template = Pipeline.builder()
                .id(UUID.randomUUID())
                .name("CSV to RDF Template")
                .definition("{\"steps\":[]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .template(true)
                .build();
            when(pipelineService.getTemplates()).thenReturn(List.of(template));

            mockMvc.perform(get("/api/v1/pipelines/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].template", is(true)));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no templates exist")
        void getTemplates_NoTemplates_Returns200WithEmptyList() throws Exception {
            when(pipelineService.getTemplates()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/pipelines/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines/operations
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/operations — list available operations")
    class GetOperationsTests {

        @Test
        @DisplayName("Should return 200 with operation catalog map")
        void getOperations_Returns200WithCatalog() throws Exception {
            when(operationRegistry.getCatalog()).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/pipelines/operations"))
                .andExpect(status().isOk());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines/operations/{operationId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/operations/{operationId}")
    class GetOperationByIdTests {

        @Test
        @DisplayName("Should return 200 with operation info when operation exists")
        void getOperation_ExistingId_Returns200() throws Exception {
            OperationRegistry.OperationInfo info = new OperationRegistry.OperationInfo(
                "load-csv", "Load CSV", "Load data from CSV",
                Operation.OperationType.SOURCE, Map.of(), null);
            when(operationRegistry.getOperationInfo("load-csv")).thenReturn(Optional.of(info));

            mockMvc.perform(get("/api/v1/pipelines/operations/load-csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("load-csv")));
        }

        @Test
        @DisplayName("Should return 404 when operation does not exist")
        void getOperation_UnknownId_Returns404() throws Exception {
            when(operationRegistry.getOperationInfo("unknown")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/pipelines/operations/unknown"))
                .andExpect(status().isNotFound());
        }
    }
}
