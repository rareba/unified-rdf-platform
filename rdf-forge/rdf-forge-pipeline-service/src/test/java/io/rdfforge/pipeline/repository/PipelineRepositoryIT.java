package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.PipelineEntity;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PipelineRepository} against a real PostgreSQL database.
 *
 * <p>Exercises CRUD, custom JPQL queries, JSONB column handling, and the full-text
 * search query ({@code searchByOptionalProjectId}).  Schema is created by Hibernate's
 * {@code create-drop} strategy; Flyway is disabled so the slice test remains fast and
 * self-contained.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PipelineRepository Integration Tests")
class PipelineRepositoryIT {

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
    private PipelineRepository pipelineRepository;

    private UUID projectIdA;
    private UUID projectIdB;

    @BeforeEach
    void setUp() {
        projectIdA = UUID.randomUUID();
        projectIdB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        pipelineRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PipelineEntity buildPipeline(String name, UUID projectId, boolean isTemplate) {
        return PipelineEntity.builder()
                .name(name)
                .projectId(projectId)
                .description("Description for " + name)
                .definition("{\"steps\":[]}")
                .definitionFormat("JSON")
                .isTemplate(isTemplate)
                .version(1)
                .build();
    }

    private PipelineEntity savePipeline(String name, UUID projectId) {
        return pipelineRepository.save(buildPipeline(name, projectId, false));
    }

    // ------------------------------------------------------------------
    // Basic CRUD
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("should persist and retrieve a pipeline by id")
        void save_and_findById_returnsPipeline() {
            PipelineEntity saved = savePipeline("Sales ETL", projectIdA);

            Optional<PipelineEntity> found = pipelineRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Sales ETL");
            assertThat(found.get().getProjectId()).isEqualTo(projectIdA);
        }

        @Test
        @DisplayName("should return empty optional for unknown id")
        void findById_unknownId_returnsEmpty() {
            assertThat(pipelineRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("should update a pipeline")
        void update_pipeline_persistsChanges() {
            PipelineEntity saved = savePipeline("Old Name", projectIdA);
            saved.setName("New Name");
            pipelineRepository.save(saved);

            PipelineEntity reloaded = pipelineRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("should delete a pipeline")
        void delete_pipeline_removesRecord() {
            PipelineEntity saved = savePipeline("To Delete", projectIdA);
            pipelineRepository.delete(saved);
            assertThat(pipelineRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("should auto-populate createdAt via @PrePersist")
        void save_pipeline_populatesCreatedAt() {
            PipelineEntity saved = savePipeline("Timestamped", projectIdA);
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // JSONB column handling (variables, tags)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JSONB column handling")
    class JsonbTests {

        @Test
        @DisplayName("should persist and reload a JSONB variables map")
        void save_withVariables_persistsAndReloadsJsonb() {
            Map<String, Object> variables = Map.of("env", "prod", "batchSize", 500);
            PipelineEntity entity = buildPipeline("Var Pipeline", projectIdA, false);
            entity.setVariables(variables);
            PipelineEntity saved = pipelineRepository.save(entity);

            PipelineEntity reloaded = pipelineRepository.findById(saved.getId()).orElseThrow();

            assertThat(reloaded.getVariables()).containsEntry("env", "prod");
            assertThat(reloaded.getVariables()).containsKey("batchSize");
        }

        @Test
        @DisplayName("should persist and reload a JSONB tags list")
        void save_withTags_persistsAndReloadsJsonb() {
            PipelineEntity entity = buildPipeline("Tagged Pipeline", projectIdA, false);
            entity.setTags(List.of("rdf", "etl", "batch"));
            PipelineEntity saved = pipelineRepository.save(entity);

            PipelineEntity reloaded = pipelineRepository.findById(saved.getId()).orElseThrow();

            assertThat(reloaded.getTags()).containsExactlyInAnyOrder("rdf", "etl", "batch");
        }

        @Test
        @DisplayName("should handle null JSONB values gracefully")
        void save_withNullJsonb_persistsNull() {
            PipelineEntity entity = buildPipeline("No Vars", projectIdA, false);
            entity.setVariables(null);
            entity.setTags(null);
            PipelineEntity saved = pipelineRepository.save(entity);

            PipelineEntity reloaded = pipelineRepository.findById(saved.getId()).orElseThrow();

            assertThat(reloaded.getVariables()).isNull();
            assertThat(reloaded.getTags()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // findByProjectId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByProjectId")
    class FindByProjectIdTests {

        @Test
        @DisplayName("should return only pipelines belonging to the given project")
        void findByProjectId_returnsMatchingPipelines() {
            savePipeline("Pipeline A1", projectIdA);
            savePipeline("Pipeline A2", projectIdA);
            savePipeline("Pipeline B1", projectIdB);

            Page<PipelineEntity> page = pipelineRepository.findByProjectId(
                    projectIdA, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(p -> p.getProjectId().equals(projectIdA));
        }

        @Test
        @DisplayName("should return empty page for project with no pipelines")
        void findByProjectId_noneExist_returnsEmpty() {
            Page<PipelineEntity> page = pipelineRepository.findByProjectId(
                    UUID.randomUUID(), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should honour pagination")
        void findByProjectId_pagination_returnsCorrectSlice() {
            for (int i = 0; i < 5; i++) {
                savePipeline("Pipeline " + i, projectIdA);
            }

            Page<PipelineEntity> firstPage = pipelineRepository.findByProjectId(
                    projectIdA, PageRequest.of(0, 2));

            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(5);
        }
    }

    // ------------------------------------------------------------------
    // findByProjectIdAndName
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByProjectIdAndName")
    class FindByProjectIdAndNameTests {

        @Test
        @DisplayName("should find a pipeline by project and name")
        void findByProjectIdAndName_exists_returnsPipeline() {
            savePipeline("Unique Pipeline", projectIdA);

            Optional<PipelineEntity> found = pipelineRepository
                    .findByProjectIdAndName(projectIdA, "Unique Pipeline");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Unique Pipeline");
        }

        @Test
        @DisplayName("should return empty when name exists but under a different project")
        void findByProjectIdAndName_differentProject_returnsEmpty() {
            savePipeline("Shared Name", projectIdA);

            Optional<PipelineEntity> found = pipelineRepository
                    .findByProjectIdAndName(projectIdB, "Shared Name");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty when name does not exist in project")
        void findByProjectIdAndName_noMatch_returnsEmpty() {
            Optional<PipelineEntity> found = pipelineRepository
                    .findByProjectIdAndName(projectIdA, "Ghost Pipeline");
            assertThat(found).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findByIsTemplateTrue
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByIsTemplateTrue")
    class FindTemplatesTests {

        @Test
        @DisplayName("should return only pipelines marked as templates")
        void findByIsTemplateTrue_returnsOnlyTemplates() {
            pipelineRepository.save(buildPipeline("Template 1", null, true));
            pipelineRepository.save(buildPipeline("Template 2", null, true));
            savePipeline("Regular Pipeline", projectIdA);

            List<PipelineEntity> templates = pipelineRepository.findByIsTemplateTrue();

            assertThat(templates).hasSize(2);
            assertThat(templates).allMatch(p -> Boolean.TRUE.equals(p.getIsTemplate()));
        }

        @Test
        @DisplayName("should return empty list when no templates exist")
        void findByIsTemplateTrue_noneExist_returnsEmpty() {
            savePipeline("Regular", projectIdA);
            assertThat(pipelineRepository.findByIsTemplateTrue()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findAllByOptionalProjectId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByOptionalProjectId")
    class FindAllByOptionalProjectIdTests {

        @Test
        @DisplayName("should return all pipelines when projectId is null")
        void findAllByOptionalProjectId_nullProjectId_returnsAll() {
            savePipeline("Pipeline A", projectIdA);
            savePipeline("Pipeline B", projectIdB);

            Page<PipelineEntity> page = pipelineRepository.findAllByOptionalProjectId(
                    null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should filter by projectId when provided")
        void findAllByOptionalProjectId_withProjectId_filtersResults() {
            savePipeline("Pipeline A", projectIdA);
            savePipeline("Pipeline B", projectIdB);

            Page<PipelineEntity> page = pipelineRepository.findAllByOptionalProjectId(
                    projectIdA, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getProjectId()).isEqualTo(projectIdA);
        }
    }

    // ------------------------------------------------------------------
    // searchByOptionalProjectId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("searchByOptionalProjectId")
    class SearchTests {

        @Test
        @DisplayName("should match pipeline name case-insensitively")
        void search_nameMatch_caseInsensitive_returnsResults() {
            savePipeline("Sales Data Pipeline", projectIdA);
            savePipeline("Customer ETL", projectIdA);

            Page<PipelineEntity> page = pipelineRepository.searchByOptionalProjectId(
                    null, "sales", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("Sales Data Pipeline");
        }

        @Test
        @DisplayName("should match pipeline description case-insensitively")
        void search_descriptionMatch_returnsResults() {
            PipelineEntity entity = buildPipeline("My Pipeline", projectIdA, false);
            entity.setDescription("Processes revenue data");
            pipelineRepository.save(entity);
            savePipeline("Other Pipeline", projectIdA);

            Page<PipelineEntity> page = pipelineRepository.searchByOptionalProjectId(
                    null, "revenue", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("My Pipeline");
        }

        @Test
        @DisplayName("should scope search to specific project when projectId is provided")
        void search_withProjectId_scopedToProject() {
            savePipeline("Alpha Pipeline", projectIdA);
            savePipeline("Alpha Pipeline", projectIdB); // same name, different project

            Page<PipelineEntity> page = pipelineRepository.searchByOptionalProjectId(
                    projectIdA, "alpha", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getProjectId()).isEqualTo(projectIdA);
        }

        @Test
        @DisplayName("should return empty page when no pipeline matches the search term")
        void search_noMatch_returnsEmpty() {
            savePipeline("Sales Pipeline", projectIdA);

            Page<PipelineEntity> page = pipelineRepository.searchByOptionalProjectId(
                    null, "zzz_nonexistent", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
        }
    }
}
