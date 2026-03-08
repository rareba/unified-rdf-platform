package io.rdfforge.data.repository;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.entity.DataSourceEntity.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataSourceRepository} against a real PostgreSQL database.
 *
 * <p>Covers CRUD operations, format filtering, ILIKE search via the native query in
 * {@code findWithFilters}, JSONB metadata column handling, and the aggregate query
 * {@code getTotalSizeByProject}.
 *
 * <p>Schema is created by Hibernate's {@code create-drop} mode; Flyway is disabled to
 * keep this as a fast, self-contained slice test.  The native queries in the repository
 * use PostgreSQL-specific syntax ({@code ILIKE}, {@code CAST(...AS VARCHAR)}) which is
 * why real PostgreSQL — rather than H2 — is required.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DataSourceRepository Integration Tests")
class DataSourceRepositoryIT {

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
    private DataSourceRepository dataSourceRepository;

    private UUID projectIdA;
    private UUID projectIdB;

    @BeforeEach
    void setUp() {
        projectIdA = UUID.randomUUID();
        projectIdB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        dataSourceRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private DataSourceEntity buildDataSource(String name, UUID projectId, DataFormat format) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setName(name);
        entity.setProjectId(projectId);
        entity.setOriginalFilename(name + ".csv");
        entity.setFormat(format);
        entity.setStorageType(StorageType.LOCAL);
        entity.setStoragePath("/data/" + name);
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(Instant.now());
        return entity;
    }

    private DataSourceEntity saveDataSource(String name, UUID projectId, DataFormat format) {
        return dataSourceRepository.save(buildDataSource(name, projectId, format));
    }

    // ------------------------------------------------------------------
    // Basic CRUD
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("should persist and retrieve a data source by id")
        void save_and_findById_returnsDataSource() {
            DataSourceEntity saved = saveDataSource("sales.csv", projectIdA, DataFormat.CSV);

            Optional<DataSourceEntity> found = dataSourceRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("sales.csv");
            assertThat(found.get().getFormat()).isEqualTo(DataFormat.CSV);
        }

        @Test
        @DisplayName("should return empty optional for unknown id")
        void findById_unknownId_returnsEmpty() {
            assertThat(dataSourceRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("should update name and persist the change")
        void update_dataSource_persistsChange() {
            DataSourceEntity saved = saveDataSource("old-name.csv", projectIdA, DataFormat.CSV);
            saved.setName("new-name.csv");
            dataSourceRepository.save(saved);

            DataSourceEntity reloaded = dataSourceRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("new-name.csv");
        }

        @Test
        @DisplayName("should delete a data source")
        void delete_dataSource_removesRecord() {
            DataSourceEntity saved = saveDataSource("to-delete.csv", projectIdA, DataFormat.CSV);
            dataSourceRepository.delete(saved);
            assertThat(dataSourceRepository.findById(saved.getId())).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // JSONB metadata column
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JSONB metadata column")
    class JsonbMetadataTests {

        @Test
        @DisplayName("should persist and reload JSONB metadata map")
        void save_withMetadata_persistsAndReloadsJsonb() {
            Map<String, Object> metadata = Map.of(
                    "delimiter", ",",
                    "hasHeader", true,
                    "encoding", "UTF-8"
            );
            DataSourceEntity entity = buildDataSource("meta.csv", projectIdA, DataFormat.CSV);
            entity.setMetadata(metadata);
            DataSourceEntity saved = dataSourceRepository.save(entity);

            DataSourceEntity reloaded = dataSourceRepository.findById(saved.getId()).orElseThrow();

            assertThat(reloaded.getMetadata()).containsEntry("delimiter", ",");
            assertThat(reloaded.getMetadata()).containsEntry("hasHeader", true);
        }

        @Test
        @DisplayName("should handle null JSONB metadata gracefully")
        void save_withNullMetadata_persistsNull() {
            DataSourceEntity entity = buildDataSource("no-meta.csv", projectIdA, DataFormat.CSV);
            entity.setMetadata(null);
            DataSourceEntity saved = dataSourceRepository.save(entity);

            DataSourceEntity reloaded = dataSourceRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getMetadata()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // findByProjectIdOrderByUploadedAtDesc
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByProjectIdOrderByUploadedAtDesc")
    class FindByProjectIdTests {

        @Test
        @DisplayName("should return only data sources for the given project, newest first")
        void findByProjectIdOrderedByUploadedAt_returnsProjectSources() {
            DataSourceEntity older = saveDataSource("older.csv", projectIdA, DataFormat.CSV);
            older.setUploadedAt(Instant.now().minusSeconds(3600));
            dataSourceRepository.save(older);

            DataSourceEntity newer = saveDataSource("newer.csv", projectIdA, DataFormat.JSON);
            newer.setUploadedAt(Instant.now());
            dataSourceRepository.save(newer);

            // Noise from another project
            saveDataSource("other.csv", projectIdB, DataFormat.CSV);

            List<DataSourceEntity> results = dataSourceRepository
                    .findByProjectIdOrderByUploadedAtDesc(projectIdA);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(d -> d.getProjectId().equals(projectIdA));
            assertThat(results.get(0).getUploadedAt())
                    .isAfterOrEqualTo(results.get(1).getUploadedAt());
        }

        @Test
        @DisplayName("should return empty list for project with no data sources")
        void findByProjectId_noneExist_returnsEmpty() {
            List<DataSourceEntity> results = dataSourceRepository
                    .findByProjectIdOrderByUploadedAtDesc(UUID.randomUUID());
            assertThat(results).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findByFormat
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByFormat")
    class FindByFormatTests {

        @Test
        @DisplayName("should return only data sources with the specified format")
        void findByFormat_returnsMatchingFormat() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);
            saveDataSource("b.csv", projectIdA, DataFormat.CSV);
            saveDataSource("c.json", projectIdA, DataFormat.JSON);
            saveDataSource("d.xlsx", projectIdB, DataFormat.XLSX);

            List<DataSourceEntity> csvSources = dataSourceRepository.findByFormat(DataFormat.CSV);

            assertThat(csvSources).hasSize(2);
            assertThat(csvSources).allMatch(d -> d.getFormat() == DataFormat.CSV);
        }

        @Test
        @DisplayName("should return empty list when no sources match the format")
        void findByFormat_noMatch_returnsEmpty() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);

            List<DataSourceEntity> results = dataSourceRepository.findByFormat(DataFormat.PARQUET);

            assertThat(results).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findWithFilters (native query — exercises ILIKE and CAST)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findWithFilters (native SQL)")
    class FindWithFiltersTests {

        @Test
        @DisplayName("should return all sources when all filters are null")
        void findWithFilters_noFilters_returnsAll() {
            saveDataSource("alpha.csv", projectIdA, DataFormat.CSV);
            saveDataSource("beta.json", projectIdB, DataFormat.JSON);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    null, null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should filter by projectId")
        void findWithFilters_projectIdFilter_returnsProjectSources() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);
            saveDataSource("b.csv", projectIdA, DataFormat.CSV);
            saveDataSource("c.json", projectIdB, DataFormat.JSON);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    projectIdA, null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(d -> d.getProjectId().equals(projectIdA));
        }

        @Test
        @DisplayName("should filter by format string")
        void findWithFilters_formatFilter_returnsMatchingSources() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);
            saveDataSource("b.json", projectIdA, DataFormat.JSON);
            saveDataSource("c.xlsx", projectIdB, DataFormat.XLSX);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    null, "CSV", null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getFormat()).isEqualTo(DataFormat.CSV);
        }

        @Test
        @DisplayName("should perform case-insensitive name search via ILIKE")
        void findWithFilters_searchFilter_caseInsensitive() {
            saveDataSource("Sales Report", projectIdA, DataFormat.CSV);
            saveDataSource("Customer Data", projectIdA, DataFormat.JSON);
            saveDataSource("Inventory Sheet", projectIdB, DataFormat.XLSX);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    null, null, "sales", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("Sales Report");
        }

        @Test
        @DisplayName("should combine projectId and format filters")
        void findWithFilters_projectIdAndFormat_narrowsResults() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);
            saveDataSource("b.json", projectIdA, DataFormat.JSON);
            saveDataSource("c.csv", projectIdB, DataFormat.CSV);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    projectIdA, "CSV", null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("a.csv");
        }

        @Test
        @DisplayName("should combine all three filters")
        void findWithFilters_allFilters_narrowsMostly() {
            saveDataSource("Sales CSV", projectIdA, DataFormat.CSV);
            saveDataSource("Revenue CSV", projectIdA, DataFormat.CSV);  // no search match
            saveDataSource("Sales JSON", projectIdA, DataFormat.JSON);  // wrong format
            saveDataSource("Sales CSV", projectIdB, DataFormat.CSV);   // wrong project

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    projectIdA, "CSV", "sales", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("Sales CSV");
            assertThat(page.getContent().get(0).getProjectId()).isEqualTo(projectIdA);
        }

        @Test
        @DisplayName("should return empty page when no sources match")
        void findWithFilters_noMatch_returnsEmpty() {
            saveDataSource("a.csv", projectIdA, DataFormat.CSV);

            Page<DataSourceEntity> page = dataSourceRepository.findWithFilters(
                    null, null, "zzz_nonexistent", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should honour pagination")
        void findWithFilters_pagination_returnsCorrectSlice() {
            for (int i = 0; i < 6; i++) {
                saveDataSource("File " + i, projectIdA, DataFormat.CSV);
            }

            Page<DataSourceEntity> firstPage = dataSourceRepository.findWithFilters(
                    null, null, null, PageRequest.of(0, 2));
            Page<DataSourceEntity> thirdPage = dataSourceRepository.findWithFilters(
                    null, null, null, PageRequest.of(2, 2));

            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(6);
            assertThat(thirdPage.getContent()).hasSize(2);
        }
    }

    // ------------------------------------------------------------------
    // getTotalSizeByProject
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getTotalSizeByProject")
    class GetTotalSizeTests {

        @Test
        @DisplayName("should sum sizeBytes for all sources in a project")
        void getTotalSizeByProject_returnsSumForProject() {
            DataSourceEntity a = buildDataSource("a.csv", projectIdA, DataFormat.CSV);
            a.setSizeBytes(1_000L);
            dataSourceRepository.save(a);

            DataSourceEntity b = buildDataSource("b.json", projectIdA, DataFormat.JSON);
            b.setSizeBytes(2_500L);
            dataSourceRepository.save(b);

            // Noise — different project
            DataSourceEntity c = buildDataSource("c.csv", projectIdB, DataFormat.CSV);
            c.setSizeBytes(9_999L);
            dataSourceRepository.save(c);

            Long total = dataSourceRepository.getTotalSizeByProject(projectIdA);

            assertThat(total).isEqualTo(3_500L);
        }

        @Test
        @DisplayName("should return null when project has no data sources")
        void getTotalSizeByProject_noSources_returnsNull() {
            Long total = dataSourceRepository.getTotalSizeByProject(UUID.randomUUID());
            // SUM over an empty result set returns NULL in SQL
            assertThat(total).isNull();
        }

        @Test
        @DisplayName("should return null when sizeBytes is null for all sources in project")
        void getTotalSizeByProject_nullSizes_returnsNull() {
            DataSourceEntity entity = buildDataSource("no-size.csv", projectIdA, DataFormat.CSV);
            entity.setSizeBytes(null);
            dataSourceRepository.save(entity);

            Long total = dataSourceRepository.getTotalSizeByProject(projectIdA);
            assertThat(total).isNull();
        }
    }

    // ------------------------------------------------------------------
    // findByProjectId (paged)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByProjectId (paged)")
    class FindByProjectIdPagedTests {

        @Test
        @DisplayName("should return paginated sources for a project")
        void findByProjectId_paged_returnsPage() {
            for (int i = 0; i < 4; i++) {
                saveDataSource("File " + i, projectIdA, DataFormat.CSV);
            }

            Page<DataSourceEntity> page = dataSourceRepository.findByProjectId(
                    projectIdA, PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(4);
        }
    }
}
