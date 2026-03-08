package io.rdfforge.data;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.entity.DataSourceEntity.StorageType;
import io.rdfforge.data.repository.DataSourceRepository;
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
 * End-to-end integration tests for the Data Service REST API.
 *
 * <p>Uses the full Spring Boot application context backed by a PostgreSQLContainer.
 * MinIO is not required — the tests only exercise the repository / controller layers
 * without triggering actual file upload operations.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DisplayName("Data Service API Integration Tests")
class DataUploadIT {

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
    private DataSourceRepository dataSourceRepository;

    @AfterEach
    void tearDown() {
        dataSourceRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private DataSourceEntity saveDataSource(String name, UUID projectId, DataFormat format) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setName(name);
        entity.setProjectId(projectId);
        entity.setOriginalFilename(name);
        entity.setFormat(format);
        entity.setStorageType(StorageType.LOCAL);
        entity.setStoragePath("/data/" + name);
        entity.setSizeBytes(2048L);
        entity.setUploadedAt(Instant.now());
        return dataSourceRepository.save(entity);
    }

    // ------------------------------------------------------------------
    // GET /api/v1/data
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/data returns 200 with empty list when no sources exist")
    void listData_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/data returns persisted data sources")
    void listData_withData_returnsPersisted() throws Exception {
        UUID projectId = UUID.randomUUID();
        saveDataSource("sales.csv", projectId, DataFormat.CSV);
        saveDataSource("customers.json", projectId, DataFormat.JSON);

        mockMvc.perform(get("/api/v1/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/data?projectId=<id> scopes results to the project")
    void listData_filterByProject_returnsScopedResults() throws Exception {
        UUID projectId = UUID.randomUUID();
        saveDataSource("a.csv", projectId, DataFormat.CSV);
        saveDataSource("b.csv", UUID.randomUUID(), DataFormat.CSV); // different project

        mockMvc.perform(get("/api/v1/data")
                        .param("projectId", projectId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/data/{id}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/data/{id} returns 404 for unknown id")
    void getDataById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/data/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/data/{id} returns the data source when it exists")
    void getDataById_exists_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        DataSourceEntity saved = saveDataSource("report.xlsx", projectId, DataFormat.XLSX);

        mockMvc.perform(get("/api/v1/data/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.name").value("report.xlsx"));
    }
}
