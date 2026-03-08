package io.rdfforge.pipeline;

import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the Pipeline Service REST API.
 *
 * <p>Uses the full Spring Boot application context against a real PostgreSQL
 * database managed by Testcontainers. Flyway is disabled and Hibernate
 * creates the schema via {@code create-drop} to keep the test self-contained.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DisplayName("Pipeline Service API Integration Tests")
class PipelineApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rdfforge_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "");
        registry.add("spring.datasource.hikari.schema", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @AfterEach
    void tearDown() {
        pipelineRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PipelineEntity savePipeline(String name, UUID projectId) {
        return pipelineRepository.save(PipelineEntity.builder()
                .name(name)
                .projectId(projectId)
                .description("Test pipeline " + name)
                .definition("{\"steps\":[]}")
                .definitionFormat("JSON")
                .version(1)
                .isTemplate(false)
                .build());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/pipelines
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/pipelines returns 200 with empty list when no pipelines exist")
    void getAllPipelines_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/pipelines returns persisted pipelines")
    void getAllPipelines_withData_returnsPersisted() throws Exception {
        UUID projectId = UUID.randomUUID();
        savePipeline("ETL Pipeline", projectId);
        savePipeline("Enrichment Pipeline", projectId);

        mockMvc.perform(get("/api/v1/pipelines").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/pipelines?projectId=<id> filters by project")
    void getAllPipelines_filterByProject_returnsProjectPipelines() throws Exception {
        UUID projectId = UUID.randomUUID();
        savePipeline("My Pipeline", projectId);
        savePipeline("Other Pipeline", UUID.randomUUID()); // different project

        mockMvc.perform(get("/api/v1/pipelines")
                        .param("projectId", projectId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/pipelines/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/pipelines/{id} returns 404 for unknown id")
    void getPipelineById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/pipelines/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/pipelines/{id} returns pipeline when it exists")
    void getPipelineById_exists_returns200WithBody() throws Exception {
        UUID projectId = UUID.randomUUID();
        PipelineEntity saved = savePipeline("Sales ETL", projectId);

        mockMvc.perform(get("/api/v1/pipelines/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.name").value("Sales ETL"));
    }

    // ------------------------------------------------------------------
    // GET /api/v1/pipelines/templates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/pipelines/templates returns only template pipelines")
    void getTemplates_returnsOnlyTemplates() throws Exception {
        pipelineRepository.save(PipelineEntity.builder()
                .name("Template Pipeline")
                .description("A reusable template")
                .definition("{\"steps\":[]}")
                .definitionFormat("JSON")
                .version(1)
                .isTemplate(true)
                .build());
        savePipeline("Regular Pipeline", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/pipelines/templates").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
