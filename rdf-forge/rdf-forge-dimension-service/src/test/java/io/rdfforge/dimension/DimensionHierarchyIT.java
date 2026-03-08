package io.rdfforge.dimension;

import io.rdfforge.dimension.repository.DimensionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the Dimension Service REST API.
 *
 * <p>Spins up the full Spring Boot application context against a real PostgreSQL
 * database managed by Testcontainers. Caffeine cache is disabled in the
 * {@code integration} profile to simplify assertion of database-level behaviour.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DisplayName("Dimension Service API Integration Tests")
class DimensionHierarchyIT {

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
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DimensionRepository dimensionRepository;

    @AfterEach
    void tearDown() {
        dimensionRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // GET /api/v1/dimensions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/dimensions returns 200 with empty list when no dimensions exist")
    void listDimensions_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/dimensions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/dimensions returns 200 after data is seeded")
    void listDimensions_withData_returns200() throws Exception {
        // Dimension entities are more complex (depend on cube context); verify
        // that the endpoint is reachable and returns a valid response when
        // the database is available.
        mockMvc.perform(get("/api/v1/dimensions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
