package io.rdfforge.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * <p>Spins up a shared PostgreSQLContainer (reused across the full test suite run
 * by Testcontainers' static-field reuse semantics) and wires its JDBC coordinates
 * into the Spring {@code DataSource} via {@link DynamicPropertySource}.
 *
 * <p>Subclasses should add {@code @ActiveProfiles("integration")} and configure
 * Flyway or schema creation via {@code application-integration.yml} (or equivalent).
 *
 * <p>Usage:
 * <pre>{@code
 * @DataJpaTest
 * @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
 * class MyRepositoryIT extends PostgresIntegrationTest { ... }
 * }</pre>
 */
@Testcontainers
@SpringBootTest
public abstract class PostgresIntegrationTest {

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
    }
}
