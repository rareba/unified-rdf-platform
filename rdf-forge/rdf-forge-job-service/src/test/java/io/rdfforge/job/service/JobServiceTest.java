package io.rdfforge.job.service;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobEntity.TriggerType;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobLogRepository;
import io.rdfforge.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Tests")
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobLogRepository jobLogRepository;

    @Mock
    private JobExecutorService executorService;

    private JobService jobService;

    private UUID jobId;
    private UUID pipelineId;
    private UUID userId;
    private JobEntity sampleJob;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobLogRepository, executorService);
        
        jobId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();
        userId = UUID.randomUUID();

        sampleJob = new JobEntity();
        sampleJob.setId(jobId);
        sampleJob.setPipelineId(pipelineId);
        sampleJob.setStatus(JobStatus.PENDING);
        sampleJob.setPriority(5);
        sampleJob.setDryRun(false);
        sampleJob.setTriggeredBy(TriggerType.MANUAL);
        sampleJob.setCreatedBy(userId);
        sampleJob.setVariables(Map.of("key", "value"));
    }

    @Nested
    @DisplayName("getJob Tests")
    class GetJobTests {

        @Test
        @DisplayName("Should return job when found")
        void getJob_WhenFound_ReturnsJob() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            Optional<JobEntity> result = jobService.getJob(jobId);

            assertTrue(result.isPresent());
            assertEquals(jobId, result.get().getId());
            verify(jobRepository).findById(jobId);
        }

        @Test
        @DisplayName("Should return empty when job not found")
        void getJob_WhenNotFound_ReturnsEmpty() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

            Optional<JobEntity> result = jobService.getJob(jobId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getJobs Tests")
    class GetJobsTests {

        @Test
        @DisplayName("Should return page of jobs with filters")
        void getJobs_WithFilters_ReturnsPageOfJobs() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<JobEntity> jobPage = new PageImpl<>(List.of(sampleJob), pageable, 1);
            
            when(jobRepository.findWithFilters(JobStatus.PENDING, pipelineId, pageable))
                .thenReturn(jobPage);

            Page<JobEntity> result = jobService.getJobs(JobStatus.PENDING, pipelineId, 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(JobStatus.PENDING, result.getContent().get(0).getStatus());
        }

        @Test
        @DisplayName("Should return empty page when no jobs match")
        void getJobs_WhenNoMatch_ReturnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<JobEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            
            when(jobRepository.findWithFilters(any(), any(), any())).thenReturn(emptyPage);

            Page<JobEntity> result = jobService.getJobs(null, null, 0, 10);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("createJob Tests")
    class CreateJobTests {

        @Test
        @DisplayName("Should create job with correct defaults")
        void createJob_WithValidData_CreatesJob() {
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(jobId);
                return job;
            });

            JobEntity result = jobService.createJob(pipelineId, Map.of("key", "value"), 5, false, userId);

            assertNotNull(result);
            assertEquals(pipelineId, result.getPipelineId());
            assertEquals(5, result.getPriority());
            assertFalse(result.isDryRun());
            assertEquals(TriggerType.MANUAL, result.getTriggeredBy());
            assertEquals(JobStatus.PENDING, result.getStatus());
            verify(executorService).executeAsync(any(UUID.class));
        }

        @Test
        @DisplayName("Should create dry run job")
        void createJob_WithDryRun_CreatesDryRunJob() {
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(jobId);
                return job;
            });

            JobEntity result = jobService.createJob(pipelineId, Map.of(), null, true, userId);

            assertTrue(result.isDryRun());
            assertEquals(5, result.getPriority()); // Default priority
        }

        @Test
        @DisplayName("Should use default priority when null")
        void createJob_WithNullPriority_UsesDefaultPriority() {
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(jobId);
                return job;
            });

            JobEntity result = jobService.createJob(pipelineId, Map.of(), null, false, userId);

            assertEquals(5, result.getPriority());
        }

        @Test
        @DisplayName("Should trigger async execution")
        void createJob_ShouldTriggerAsyncExecution() {
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(jobId);
                return job;
            });

            jobService.createJob(pipelineId, Map.of(), 5, false, userId);

            verify(executorService).executeAsync(eq(jobId));
        }
    }

    @Nested
    @DisplayName("createScheduledJob Tests")
    class CreateScheduledJobTests {

        @Test
        @DisplayName("Should create scheduled job with correct trigger type")
        void createScheduledJob_CreatesWithScheduleTrigger() {
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(jobId);
                return job;
            });

            JobEntity result = jobService.createScheduledJob(pipelineId, Map.of("key", "value"));

            assertNotNull(result);
            assertEquals(TriggerType.SCHEDULE, result.getTriggeredBy());
            assertFalse(result.isDryRun()); // Scheduled jobs are real executions
            assertEquals(5, result.getPriority());
        }
    }

    @Nested
    @DisplayName("cancelJob Tests")
    class CancelJobTests {

        @Test
        @DisplayName("Should cancel pending job")
        void cancelJob_WhenPending_CancelsJob() {
            sampleJob.setStatus(JobStatus.PENDING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.cancelJob(jobId);

            assertEquals(JobStatus.CANCELLED, sampleJob.getStatus());
            assertNotNull(sampleJob.getCompletedAt());
            verify(jobRepository).save(sampleJob);
        }

        @Test
        @DisplayName("Should cancel running job and stop execution")
        void cancelJob_WhenRunning_CancelsAndStopsExecution() {
            sampleJob.setStatus(JobStatus.RUNNING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.cancelJob(jobId);

            assertEquals(JobStatus.CANCELLED, sampleJob.getStatus());
            verify(executorService).cancelExecution(jobId);
        }

        @Test
        @DisplayName("Should not cancel completed job")
        void cancelJob_WhenCompleted_DoesNotCancel() {
            sampleJob.setStatus(JobStatus.COMPLETED);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.cancelJob(jobId);

            assertEquals(JobStatus.COMPLETED, sampleJob.getStatus());
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not cancel failed job")
        void cancelJob_WhenFailed_DoesNotCancel() {
            sampleJob.setStatus(JobStatus.FAILED);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.cancelJob(jobId);

            assertEquals(JobStatus.FAILED, sampleJob.getStatus());
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("retryJob Tests")
    class RetryJobTests {

        @Test
        @DisplayName("Should retry failed job")
        void retryJob_WhenFailed_CreatesNewJob() {
            sampleJob.setStatus(JobStatus.FAILED);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(UUID.randomUUID());
                return job;
            });

            JobEntity result = jobService.retryJob(jobId);

            assertNotNull(result);
            assertEquals(JobStatus.PENDING, result.getStatus());
            assertEquals(pipelineId, result.getPipelineId());
            verify(executorService).executeAsync(any(UUID.class));
        }

        @Test
        @DisplayName("Should retry cancelled job")
        void retryJob_WhenCancelled_CreatesNewJob() {
            sampleJob.setStatus(JobStatus.CANCELLED);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(jobRepository.save(any(JobEntity.class))).thenAnswer(inv -> {
                JobEntity job = inv.getArgument(0);
                job.setId(UUID.randomUUID());
                return job;
            });

            JobEntity result = jobService.retryJob(jobId);

            assertNotNull(result);
            assertEquals(JobStatus.PENDING, result.getStatus());
        }

        @Test
        @DisplayName("Should throw exception when retrying running job")
        void retryJob_WhenRunning_ThrowsException() {
            sampleJob.setStatus(JobStatus.RUNNING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            assertThrows(IllegalStateException.class, () -> 
                jobService.retryJob(jobId)
            );
        }

        @Test
        @DisplayName("Should throw exception when retrying pending job")
        void retryJob_WhenPending_ThrowsException() {
            sampleJob.setStatus(JobStatus.PENDING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            assertThrows(IllegalStateException.class, () -> 
                jobService.retryJob(jobId)
            );
        }

        @Test
        @DisplayName("Should throw exception when job not found")
        void retryJob_WhenNotFound_ThrowsException() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> 
                jobService.retryJob(jobId)
            );
        }
    }

    @Nested
    @DisplayName("updateJobStatus Tests")
    class UpdateJobStatusTests {

        @Test
        @DisplayName("Should update status to running and set startedAt")
        void updateJobStatus_ToRunning_SetsStartedAt() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.updateJobStatus(jobId, JobStatus.RUNNING);

            assertEquals(JobStatus.RUNNING, sampleJob.getStatus());
            assertNotNull(sampleJob.getStartedAt());
            verify(jobRepository).save(sampleJob);
        }

        @Test
        @DisplayName("Should update status to completed and set completedAt")
        void updateJobStatus_ToCompleted_SetsCompletedAt() {
            sampleJob.setStatus(JobStatus.RUNNING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.updateJobStatus(jobId, JobStatus.COMPLETED);

            assertEquals(JobStatus.COMPLETED, sampleJob.getStatus());
            assertNotNull(sampleJob.getCompletedAt());
        }

        @Test
        @DisplayName("Should update status to failed and set completedAt")
        void updateJobStatus_ToFailed_SetsCompletedAt() {
            sampleJob.setStatus(JobStatus.RUNNING);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

            jobService.updateJobStatus(jobId, JobStatus.FAILED);

            assertEquals(JobStatus.FAILED, sampleJob.getStatus());
            assertNotNull(sampleJob.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("updateJobMetrics Tests")
    class UpdateJobMetricsTests {

        @Test
        @DisplayName("Should update job metrics")
        void updateJobMetrics_SetsMetrics() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            Map<String, Object> metrics = Map.of("rowsProcessed", 1000, "duration", 5000);

            jobService.updateJobMetrics(jobId, metrics);

            assertEquals(metrics, sampleJob.getMetrics());
            verify(jobRepository).save(sampleJob);
        }
    }

    @Nested
    @DisplayName("setJobError Tests")
    class SetJobErrorTests {

        @Test
        @DisplayName("Should set error details and status")
        void setJobError_SetsErrorDetailsAndStatus() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            Map<String, Object> details = Map.of("line", 42, "column", 10);

            jobService.setJobError(jobId, "Parse error", details);

            assertEquals(JobStatus.FAILED, sampleJob.getStatus());
            assertEquals("Parse error", sampleJob.getErrorMessage());
            assertEquals(details, sampleJob.getErrorDetails());
            assertNotNull(sampleJob.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("setJobOutput Tests")
    class SetJobOutputTests {

        @Test
        @DisplayName("Should set output graph URI")
        void setJobOutput_SetsOutputGraph() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            String graphUri = "http://example.org/graph/123";

            jobService.setJobOutput(jobId, graphUri);

            assertEquals(graphUri, sampleJob.getOutputGraph());
            verify(jobRepository).save(sampleJob);
        }
    }

    @Nested
    @DisplayName("addLog Tests")
    class AddLogTests {

        @Test
        @DisplayName("Should add log entry")
        void addLog_CreatesLogEntry() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            ArgumentCaptor<JobLogEntity> logCaptor = ArgumentCaptor.forClass(JobLogEntity.class);

            jobService.addLog(jobId, LogLevel.INFO, "step1", "Processing started", Map.of("count", 100));

            verify(jobLogRepository).save(logCaptor.capture());
            JobLogEntity savedLog = logCaptor.getValue();
            assertEquals(LogLevel.INFO, savedLog.getLevel());
            assertEquals("step1", savedLog.getStep());
            assertEquals("Processing started", savedLog.getMessage());
        }
    }

    @Nested
    @DisplayName("getLogs Tests")
    class GetLogsTests {

        @Test
        @DisplayName("Should return all logs when minLevel is null")
        void getLogs_WithNullLevel_ReturnsAllLogs() {
            List<JobLogEntity> logs = List.of(new JobLogEntity(), new JobLogEntity());
            when(jobLogRepository.findByJob_IdOrderByTimestampAsc(jobId)).thenReturn(logs);

            List<JobLogEntity> result = jobService.getLogs(jobId, null);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should filter logs by minimum level INFO")
        void getLogs_WithInfoLevel_ReturnsInfoAndHigher() {
            List<LogLevel> expectedLevels = List.of(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR);
            List<JobLogEntity> logs = List.of(new JobLogEntity());
            when(jobLogRepository.findByJobIdAndLevels(jobId, expectedLevels)).thenReturn(logs);

            List<JobLogEntity> result = jobService.getLogs(jobId, LogLevel.INFO);

            assertEquals(1, result.size());
            verify(jobLogRepository).findByJobIdAndLevels(jobId, expectedLevels);
        }

        @Test
        @DisplayName("Should filter logs by minimum level ERROR")
        void getLogs_WithErrorLevel_ReturnsOnlyErrors() {
            List<LogLevel> expectedLevels = List.of(LogLevel.ERROR);
            List<JobLogEntity> logs = List.of(new JobLogEntity());
            when(jobLogRepository.findByJobIdAndLevels(jobId, expectedLevels)).thenReturn(logs);

            List<JobLogEntity> result = jobService.getLogs(jobId, LogLevel.ERROR);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should return running job count")
        void getRunningJobCount_ReturnsCount() {
            when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(5L);

            long result = jobService.getRunningJobCount();

            assertEquals(5L, result);
        }

        @Test
        @DisplayName("Should return completed today count")
        void getCompletedTodayCount_ReturnsCount() {
            when(jobRepository.countByStatusSince(eq(JobStatus.COMPLETED), any(Instant.class)))
                .thenReturn(10L);

            long result = jobService.getCompletedTodayCount();

            assertEquals(10L, result);
        }

        @Test
        @DisplayName("Should return failed today count")
        void getFailedTodayCount_ReturnsCount() {
            when(jobRepository.countByStatusSince(eq(JobStatus.FAILED), any(Instant.class)))
                .thenReturn(2L);

            long result = jobService.getFailedTodayCount();

            assertEquals(2L, result);
        }
    }
}
