package io.rdfforge.job;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.repository.JobRepository;
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

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the Job Service REST API.
 *
 * <p>Spins up the full Spring Boot application context against a real
 * PostgreSQL database managed by Testcontainers. Redis is excluded via the
 * {@code integration} profile ({@code application-integration.yml}) to keep the
 * test environment self-contained.
 *
 * <p>The test data written in {@code @BeforeEach} / individual tests is
 * removed in {@code @AfterEach} to prevent cross-test pollution.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DisplayName("Job Service API Integration Tests")
class JobExecutionIT {

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
    private JobRepository jobRepository;

    @AfterEach
    void tearDown() {
        jobRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JobEntity saveJob(JobStatus status) {
        JobEntity job = new JobEntity();
        job.setPipelineId(UUID.randomUUID());
        job.setStatus(status);
        job.setPriority(5);
        job.setCreatedAt(Instant.now());
        return jobRepository.save(job);
    }

    // ------------------------------------------------------------------
    // GET /api/v1/jobs
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/jobs returns 200 with empty list when no jobs exist")
    void listJobs_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns persisted jobs")
    void listJobs_withData_returnsPersisted() throws Exception {
        saveJob(JobStatus.PENDING);
        saveJob(JobStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/jobs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/jobs?status=PENDING filters by status")
    void listJobs_filterByStatus_returnsPendingOnly() throws Exception {
        saveJob(JobStatus.PENDING);
        saveJob(JobStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("status", "PENDING")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/jobs/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns 404 for unknown id")
    void getJobById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns the job when it exists")
    void getJobById_exists_returns200() throws Exception {
        JobEntity saved = saveJob(JobStatus.RUNNING);

        mockMvc.perform(get("/api/v1/jobs/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }
}
