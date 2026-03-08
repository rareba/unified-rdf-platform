package io.rdfforge.job.repository;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JobRepository} against a real PostgreSQL database
 * spun up via Testcontainers.
 *
 * <p>Uses {@code @DataJpaTest} (slice test) with Flyway disabled — the JPA layer
 * itself creates the schema via {@code spring.jpa.hibernate.ddl-auto=create-drop}.
 * This keeps the tests fast while still exercising the actual PostgreSQL SQL dialect
 * (JSONB types, custom query predicates, etc.).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("JobRepository Integration Tests")
class JobRepositoryIT {

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
        // Use create-drop so the JPA entity model drives schema creation in the slice test.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Disable Flyway so the slice test is self-contained.
        registry.add("spring.flyway.enabled", () -> "false");
        // Target the correct PostgreSQL dialect.
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        // No schema prefix for slice tests.
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "");
        registry.add("spring.datasource.hikari.schema", () -> "");
    }

    @Autowired
    private JobRepository jobRepository;

    private UUID pipelineIdA;
    private UUID pipelineIdB;

    @BeforeEach
    void setUp() {
        pipelineIdA = UUID.randomUUID();
        pipelineIdB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        jobRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JobEntity saveJob(UUID pipelineId, JobStatus status, int priority) {
        JobEntity job = new JobEntity();
        job.setPipelineId(pipelineId);
        job.setStatus(status);
        job.setPriority(priority);
        job.setCreatedAt(Instant.now());
        return jobRepository.save(job);
    }

    private JobEntity saveRunningJobStartedAt(UUID pipelineId, Instant startedAt) {
        JobEntity job = new JobEntity();
        job.setPipelineId(pipelineId);
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setCreatedAt(Instant.now());
        return jobRepository.save(job);
    }

    private JobEntity saveCompletedJobAt(UUID pipelineId, Instant completedAt) {
        JobEntity job = new JobEntity();
        job.setPipelineId(pipelineId);
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(completedAt);
        job.setCreatedAt(Instant.now());
        return jobRepository.save(job);
    }

    // ------------------------------------------------------------------
    // Basic CRUD
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("should persist and retrieve a job by id")
        void save_and_findById_returnsPersistedJob() {
            JobEntity saved = saveJob(pipelineIdA, JobStatus.PENDING, 5);

            Optional<JobEntity> found = jobRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getPipelineId()).isEqualTo(pipelineIdA);
            assertThat(found.get().getStatus()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        @DisplayName("should return empty optional for unknown id")
        void findById_unknownId_returnsEmpty() {
            Optional<JobEntity> found = jobRepository.findById(UUID.randomUUID());
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should delete a job")
        void delete_removesJob() {
            JobEntity saved = saveJob(pipelineIdA, JobStatus.PENDING, 5);
            jobRepository.delete(saved);
            assertThat(jobRepository.findById(saved.getId())).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findByPipelineIdOrderByCreatedAtDesc
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByPipelineIdOrderByCreatedAtDesc")
    class FindByPipelineIdTests {

        @Test
        @DisplayName("should return only jobs for the given pipeline, newest first")
        void findByPipelineId_returnsMatchingJobsOrderedByCreatedAt() {
            // Create jobs for pipelineIdA at different times
            JobEntity older = saveJob(pipelineIdA, JobStatus.COMPLETED, 5);
            older.setCreatedAt(Instant.now().minusSeconds(60));
            jobRepository.save(older);

            JobEntity newer = saveJob(pipelineIdA, JobStatus.PENDING, 5);
            newer.setCreatedAt(Instant.now());
            jobRepository.save(newer);

            // Noise: different pipeline
            saveJob(pipelineIdB, JobStatus.PENDING, 5);

            List<JobEntity> results = jobRepository.findByPipelineIdOrderByCreatedAtDesc(pipelineIdA);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(j -> j.getPipelineId().equals(pipelineIdA));
            // Newest createdAt is first
            assertThat(results.get(0).getCreatedAt())
                    .isAfterOrEqualTo(results.get(1).getCreatedAt());
        }

        @Test
        @DisplayName("should return empty list when no jobs exist for pipeline")
        void findByPipelineId_noneExist_returnsEmpty() {
            List<JobEntity> results = jobRepository.findByPipelineIdOrderByCreatedAtDesc(UUID.randomUUID());
            assertThat(results).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findWithFilters
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findWithFilters")
    class FindWithFiltersTests {

        @Test
        @DisplayName("should return all jobs when both filters are null")
        void findWithFilters_noFilters_returnsAll() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);
            saveJob(pipelineIdB, JobStatus.COMPLETED, 3);

            Page<JobEntity> page = jobRepository.findWithFilters(null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should filter by status")
        void findWithFilters_statusFilter_returnsMatchingJobs() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);
            saveJob(pipelineIdA, JobStatus.COMPLETED, 5);
            saveJob(pipelineIdB, JobStatus.FAILED, 5);

            Page<JobEntity> page = jobRepository.findWithFilters(JobStatus.PENDING, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        @DisplayName("should filter by pipelineId")
        void findWithFilters_pipelineIdFilter_returnsMatchingJobs() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);
            saveJob(pipelineIdA, JobStatus.RUNNING, 5);
            saveJob(pipelineIdB, JobStatus.COMPLETED, 5);

            Page<JobEntity> page = jobRepository.findWithFilters(null, pipelineIdA, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(j -> j.getPipelineId().equals(pipelineIdA));
        }

        @Test
        @DisplayName("should filter by both status and pipelineId")
        void findWithFilters_bothFilters_returnsNarrowedResults() {
            saveJob(pipelineIdA, JobStatus.RUNNING, 5);   // matches both
            saveJob(pipelineIdA, JobStatus.COMPLETED, 5); // wrong status
            saveJob(pipelineIdB, JobStatus.RUNNING, 5);   // wrong pipeline

            Page<JobEntity> page = jobRepository.findWithFilters(JobStatus.RUNNING, pipelineIdA, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(page.getContent().get(0).getPipelineId()).isEqualTo(pipelineIdA);
        }

        @Test
        @DisplayName("should return empty page when no jobs match filters")
        void findWithFilters_noMatch_returnsEmptyPage() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);

            Page<JobEntity> page = jobRepository.findWithFilters(
                    JobStatus.CANCELLED, pipelineIdA, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should honour pagination")
        void findWithFilters_pagination_returnsCorrectPage() {
            for (int i = 0; i < 5; i++) {
                saveJob(pipelineIdA, JobStatus.PENDING, 5);
            }

            Page<JobEntity> firstPage = jobRepository.findWithFilters(null, null, PageRequest.of(0, 2));
            Page<JobEntity> secondPage = jobRepository.findWithFilters(null, null, PageRequest.of(1, 2));

            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(5);
            assertThat(secondPage.getContent()).hasSize(2);
        }
    }

    // ------------------------------------------------------------------
    // findRunningJobsStartedBefore (timeout detection)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findRunningJobsStartedBefore")
    class FindRunningJobsStartedBeforeTests {

        @Test
        @DisplayName("should return running jobs started before the threshold")
        void findRunningJobsStartedBefore_returnsStaleJobs() {
            Instant threshold = Instant.now().minusSeconds(3600); // 1 hour ago

            // Stale: started 2 hours ago
            JobEntity stale = saveRunningJobStartedAt(pipelineIdA, Instant.now().minusSeconds(7200));
            // Recent: started 30 minutes ago (after threshold)
            saveRunningJobStartedAt(pipelineIdB, Instant.now().minusSeconds(1800));
            // Not running
            saveJob(pipelineIdA, JobStatus.COMPLETED, 5);

            List<JobEntity> staleJobs = jobRepository.findRunningJobsStartedBefore(threshold);

            assertThat(staleJobs).hasSize(1);
            assertThat(staleJobs.get(0).getId()).isEqualTo(stale.getId());
        }

        @Test
        @DisplayName("should return empty list when no running jobs are stale")
        void findRunningJobsStartedBefore_noneStale_returnsEmpty() {
            // Only recently started running jobs
            saveRunningJobStartedAt(pipelineIdA, Instant.now().minusSeconds(60));

            List<JobEntity> staleJobs = jobRepository.findRunningJobsStartedBefore(
                    Instant.now().minusSeconds(3600));

            assertThat(staleJobs).isEmpty();
        }

        @Test
        @DisplayName("should ignore non-running jobs regardless of start time")
        void findRunningJobsStartedBefore_ignoresNonRunningJobs() {
            JobEntity completed = new JobEntity();
            completed.setPipelineId(pipelineIdA);
            completed.setStatus(JobStatus.COMPLETED);
            completed.setStartedAt(Instant.now().minusSeconds(7200));
            completed.setCreatedAt(Instant.now());
            jobRepository.save(completed);

            List<JobEntity> staleJobs = jobRepository.findRunningJobsStartedBefore(
                    Instant.now().minusSeconds(3600));

            assertThat(staleJobs).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // countByStatus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("countByStatus")
    class CountByStatusTests {

        @Test
        @DisplayName("should count jobs with the given status accurately")
        void countByStatus_returnsCorrectCount() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);
            saveJob(pipelineIdA, JobStatus.PENDING, 3);
            saveJob(pipelineIdA, JobStatus.RUNNING, 5);

            long pendingCount = jobRepository.countByStatus(JobStatus.PENDING);
            long runningCount = jobRepository.countByStatus(JobStatus.RUNNING);
            long failedCount = jobRepository.countByStatus(JobStatus.FAILED);

            assertThat(pendingCount).isEqualTo(2);
            assertThat(runningCount).isEqualTo(1);
            assertThat(failedCount).isZero();
        }
    }

    // ------------------------------------------------------------------
    // countByStatusSince
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("countByStatusSince")
    class CountByStatusSinceTests {

        @Test
        @DisplayName("should count completed jobs within the given time window")
        void countByStatusSince_countsWithinWindow() {
            Instant oneHourAgo = Instant.now().minusSeconds(3600);
            Instant twoHoursAgo = Instant.now().minusSeconds(7200);

            // Completed within the last hour
            saveCompletedJobAt(pipelineIdA, Instant.now().minusSeconds(1800));
            saveCompletedJobAt(pipelineIdA, Instant.now().minusSeconds(600));
            // Completed more than an hour ago (outside window)
            saveCompletedJobAt(pipelineIdB, twoHoursAgo);
            // Different status
            saveJob(pipelineIdA, JobStatus.FAILED, 5);

            long count = jobRepository.countByStatusSince(JobStatus.COMPLETED, oneHourAgo);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero when no jobs match the time window")
        void countByStatusSince_noMatch_returnsZero() {
            saveCompletedJobAt(pipelineIdA, Instant.now().minusSeconds(7200));

            long count = jobRepository.countByStatusSince(
                    JobStatus.COMPLETED, Instant.now().minusSeconds(3600));

            assertThat(count).isZero();
        }
    }

    // ------------------------------------------------------------------
    // findByStatusOrderByPriorityDescCreatedAtAsc
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByStatusOrderByPriorityDescCreatedAtAsc")
    class PriorityOrderingTests {

        @Test
        @DisplayName("should return pending jobs ordered by priority descending then creation time ascending")
        void findByStatus_priorityOrdering_highestPriorityFirst() {
            JobEntity lowPriority = saveJob(pipelineIdA, JobStatus.PENDING, 2);
            JobEntity highPriority = saveJob(pipelineIdA, JobStatus.PENDING, 8);
            JobEntity medPriority = saveJob(pipelineIdA, JobStatus.PENDING, 5);
            // Noise
            saveJob(pipelineIdB, JobStatus.RUNNING, 10);

            List<JobEntity> results = jobRepository
                    .findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus.PENDING);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isEqualTo(highPriority.getId());
            assertThat(results.get(1).getId()).isEqualTo(medPriority.getId());
            assertThat(results.get(2).getId()).isEqualTo(lowPriority.getId());
        }
    }

    // ------------------------------------------------------------------
    // findByStatusIn
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByStatusIn")
    class FindByStatusInTests {

        @Test
        @DisplayName("should return jobs whose status is in the supplied list")
        void findByStatusIn_multipleStatuses_returnsMatchingJobs() {
            saveJob(pipelineIdA, JobStatus.PENDING, 5);
            saveJob(pipelineIdA, JobStatus.RUNNING, 5);
            saveJob(pipelineIdA, JobStatus.COMPLETED, 5);
            saveJob(pipelineIdB, JobStatus.FAILED, 5);

            Pageable pageable = PageRequest.of(0, 10);
            Page<JobEntity> page = jobRepository.findByStatusIn(
                    List.of(JobStatus.PENDING, JobStatus.RUNNING), pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(JobEntity::getStatus)
                    .containsOnly(JobStatus.PENDING, JobStatus.RUNNING);
        }
    }
}
