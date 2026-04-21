package io.rdfforge.shacl.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.common.security.CurrentUserArgumentResolver;
import io.rdfforge.shacl.validation.dto.ValidationRunDto;
import io.rdfforge.shacl.validation.dto.ValidationSuiteDto;
import io.rdfforge.shacl.validation.dto.ValidationSuiteCreateRequest;
import io.rdfforge.shacl.validation.dto.ValidationSuiteUpdateRequest;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-style controller tests wired without Spring context so we can
 * assert the routing surface and @CurrentUser behaviour against the
 * gateway header contract.
 */
class ValidationControllerTest {

    private MockMvc mockMvc;
    private ValidationService service;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ValidationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController(service))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listSuitesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/validation/suites")
                .param("projectId", UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listSuitesReturnsOkWithUser() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(service.listSuites(eq(projectId))).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/validation/suites")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER")
                .param("projectId", projectId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void createSuiteReturnsCreated() throws Exception {
        UUID projectId = UUID.randomUUID();
        ValidationSuiteDto dto = new ValidationSuiteDto(
            UUID.randomUUID(), projectId, "my-suite", "desc", List.of(),
            ReleaseGate.FAIL_ON_ERROR, UUID.randomUUID(), Instant.now(), Instant.now());
        when(service.createSuite(any(ValidationSuiteCreateRequest.class), any())).thenReturn(dto);

        String body = mapper.writeValueAsString(new ValidationSuiteCreateRequest(
            projectId, "my-suite", "desc", List.of(), ReleaseGate.FAIL_ON_ERROR));

        mockMvc.perform(post("/api/v1/validation/suites")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("my-suite"));
    }

    @Test
    void updateSuiteReturnsOk() throws Exception {
        UUID suiteId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ValidationSuiteDto dto = new ValidationSuiteDto(
            suiteId, projectId, "renamed", "desc", List.of(),
            ReleaseGate.FAIL_ON_WARNING, UUID.randomUUID(), Instant.now(), Instant.now());
        when(service.updateSuite(eq(suiteId), any(ValidationSuiteUpdateRequest.class), any())).thenReturn(dto);

        String body = mapper.writeValueAsString(new ValidationSuiteUpdateRequest(
            "renamed", "desc", List.of(), ReleaseGate.FAIL_ON_WARNING));

        mockMvc.perform(put("/api/v1/validation/suites/" + suiteId)
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("renamed"));
    }

    @Test
    void deleteSuiteReturnsNoContent() throws Exception {
        UUID suiteId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/validation/suites/" + suiteId)
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER"))
            .andExpect(status().isNoContent());
    }

    @Test
    void runReturnsOk() throws Exception {
        UUID suiteId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ValidationRunDto dto = new ValidationRunDto(
            UUID.randomUUID(), suiteId, projectId, Instant.now(), 10L,
            ValidationStatus.PASSED, 0, 0, 0, 0, 0, "ok", java.util.Map.of(), UUID.randomUUID());
        when(service.run(eq(suiteId), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/validation/suites/" + suiteId + "/run")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetGraph\":\"urn:g\",\"targetTriplestoreId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PASSED"));
    }

    @Test
    void issuesReturnsOk() throws Exception {
        UUID runId = UUID.randomUUID();
        when(service.issues(eq(runId), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
        mockMvc.perform(get("/api/v1/validation/runs/" + runId + "/issues")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk());
    }
}
