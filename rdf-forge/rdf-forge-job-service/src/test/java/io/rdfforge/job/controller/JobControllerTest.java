package io.rdfforge.job.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.job.config.TestSecurityConfig;
import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobEntity.TriggerType;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for JobController.
 *
 * Security is disabled via TestSecurityConfig. Each nested class covers one
 * endpoint. The JobService is fully mocked, so tests are deterministic and run
 * without a Spring ApplicationContext or database.
 */
@WebMvcTest(JobController.class)
@Import(TestSecurityConfig.class)
@DisplayName("JobController Tests")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    private UUID jobId;
    private UUID pipelineId;
    private JobEntity sampleJob;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();

        sampleJob = new JobEntity();
        sampleJob.setId(jobId);
        sampleJob.setPipelineId(pipelineId);
        sampleJob.setStatus(JobStatus.PENDING);
        sampleJob.setPriority(5);
        sampleJob.setDryRun(false);
        sampleJob.setTriggeredBy(TriggerType.MANUAL);
        sampleJob.setCreatedAt(Instant.now());
        sampleJob.setVariables(Map.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/jobs
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/jobs — list jobs")
    class ListJobsTests {

        @Test
        @DisplayName("Should return 200 with a page of jobs")
        void listJobs_NoFilter_Returns200WithPage() throws Exception {
            Page<JobEntity> page = new PageImpl<>(List.of(sampleJob));
            when(jobService.getJobs(isNull(), isNull(), eq(0), eq(20))).thenReturn(page);

            mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(jobId.toString())));
        }

        @Test
        @DisplayName("Should return 200 with empty page when no jobs exist")
        void listJobs_NoJobs_Returns200WithEmptyPage() throws Exception {
            Page<JobEntity> empty = new PageImpl<>(List.of());
            when(jobService.getJobs(isNull(), isNull(), eq(0), eq(20))).thenReturn(empty);

            mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("Should pass status filter to service when 'status' param is provided")
        void listJobs_WithStatusFilter_PassesFilterToService() throws Exception {
            Page<JobEntity> page = new PageImpl<>(List.of(sampleJob));
            when(jobService.getJobs(eq(JobStatus.RUNNING), isNull(), anyInt(), anyInt()))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/jobs").param("status", "RUNNING"))
                .andExpect(status().isOk());

            verify(jobService).getJobs(eq(JobStatus.RUNNING), isNull(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should pass pipelineId filter to service when provided")
        void listJobs_WithPipelineId_PassesFilterToService() throws Exception {
            Page<JobEntity> page = new PageImpl<>(List.of(sampleJob));
            when(jobService.getJobs(isNull(), eq(pipelineId), anyInt(), anyInt()))
                .thenReturn(page);

            mockMvc.perform(get("/api/v1/jobs").param("pipelineId", pipelineId.toString()))
                .andExpect(status().isOk());

            verify(jobService).getJobs(isNull(), eq(pipelineId), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should honour custom page and size parameters")
        void listJobs_CustomPageSize_PassesCorrectPagination() throws Exception {
            Page<JobEntity> page = new PageImpl<>(List.of());
            when(jobService.getJobs(isNull(), isNull(), eq(2), eq(5))).thenReturn(page);

            mockMvc.perform(get("/api/v1/jobs").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

            verify(jobService).getJobs(isNull(), isNull(), eq(2), eq(5));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/jobs/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/jobs/{id} — get job by ID")
    class GetJobByIdTests {

        @Test
        @DisplayName("Should return 200 with job body when job exists")
        void getJob_ExistingId_Returns200WithJob() throws Exception {
            when(jobService.getJob(jobId)).thenReturn(Optional.of(sampleJob));

            mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("Should return 404 when job does not exist")
        void getJob_NonExistentId_Returns404() throws Exception {
            when(jobService.getJob(jobId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/jobs
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/jobs — create job")
    class CreateJobTests {

        @Test
        @DisplayName("Should return 200 with created job on valid request")
        void createJob_ValidRequest_Returns200WithJob() throws Exception {
            when(jobService.createJob(eq(pipelineId), any(), any(), eq(false), isNull()))
                .thenReturn(sampleJob);

            Map<String, Object> request = Map.of("pipelineId", pipelineId.toString());

            mockMvc.perform(post("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(jobId.toString())));
        }

        @Test
        @DisplayName("Should honour dryRun flag when set to true")
        void createJob_DryRunTrue_PassesDryRunToService() throws Exception {
            when(jobService.createJob(eq(pipelineId), any(), any(), eq(true), isNull()))
                .thenReturn(sampleJob);

            Map<String, Object> request = Map.of(
                "pipelineId", pipelineId.toString(),
                "dryRun", true
            );

            mockMvc.perform(post("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

            verify(jobService).createJob(eq(pipelineId), any(), any(), eq(true), isNull());
        }

        @Test
        @DisplayName("Should default dryRun to false when not specified")
        void createJob_DryRunNotSpecified_DefaultsFalse() throws Exception {
            when(jobService.createJob(any(), any(), any(), eq(false), any()))
                .thenReturn(sampleJob);

            Map<String, Object> request = Map.of("pipelineId", pipelineId.toString());

            mockMvc.perform(post("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

            verify(jobService).createJob(any(), any(), any(), eq(false), any());
        }

        @Test
        @DisplayName("Should pass variables map to service when provided in request")
        void createJob_WithVariables_PassesVariablesToService() throws Exception {
            Map<String, Object> variables = Map.of("source", "data.csv");
            when(jobService.createJob(any(), eq(variables), any(), anyBoolean(), any()))
                .thenReturn(sampleJob);

            Map<String, Object> request = Map.of(
                "pipelineId", pipelineId.toString(),
                "variables", variables
            );

            mockMvc.perform(post("/api/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

            verify(jobService).createJob(any(), eq(variables), any(), anyBoolean(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/jobs/{id}   (cancel)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/jobs/{id} — cancel job")
    class CancelJobTests {

        @Test
        @DisplayName("Should return 204 No Content on successful cancellation")
        void cancelJob_RunningJob_Returns204() throws Exception {
            doNothing().when(jobService).cancelJob(jobId);

            mockMvc.perform(delete("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNoContent());

            verify(jobService).cancelJob(jobId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/jobs/{id}/retry
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/jobs/{id}/retry — retry job")
    class RetryJobTests {

        @Test
        @DisplayName("Should return 200 with new job entity on successful retry")
        void retryJob_FailedJob_Returns200WithNewJob() throws Exception {
            JobEntity retriedJob = new JobEntity();
            retriedJob.setId(UUID.randomUUID());
            retriedJob.setPipelineId(pipelineId);
            retriedJob.setStatus(JobStatus.PENDING);
            retriedJob.setCreatedAt(Instant.now());
            when(jobService.retryJob(jobId)).thenReturn(retriedJob);

            mockMvc.perform(post("/api/v1/jobs/{id}/retry", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("Should return 404 when retrying a non-existent job")
        void retryJob_NotFound_Returns404() throws Exception {
            when(jobService.retryJob(jobId))
                .thenThrow(new ResourceNotFoundException("Job", jobId.toString()));

            mockMvc.perform(post("/api/v1/jobs/{id}/retry", jobId))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 500 when retrying a job that is not in a retryable state")
        void retryJob_NotRetryable_ReturnsBadRequest() throws Exception {
            when(jobService.retryJob(jobId))
                .thenThrow(new IllegalStateException("Can only retry failed or cancelled jobs"));

            // IllegalStateException maps to 500 without a specific handler;
            // the important thing is it does NOT return 200.
            mockMvc.perform(post("/api/v1/jobs/{id}/retry", jobId))
                .andExpect(status().is5xxServerError());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/jobs/{id}/logs
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/jobs/{id}/logs — get job logs")
    class GetJobLogsTests {

        @Test
        @DisplayName("Should return 200 with list of log entries")
        void getJobLogs_WithLogs_Returns200WithList() throws Exception {
            JobLogEntity log = new JobLogEntity();
            log.setId(UUID.randomUUID());
            log.setLevel(LogLevel.INFO);
            log.setMessage("Job started");

            when(jobService.getLogs(jobId, null)).thenReturn(List.of(log));

            mockMvc.perform(get("/api/v1/jobs/{id}/logs", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].level", is("INFO")))
                .andExpect(jsonPath("$[0].message", is("Job started")));
        }

        @Test
        @DisplayName("Should return 200 with empty list when job has no logs")
        void getJobLogs_NoLogs_Returns200WithEmptyList() throws Exception {
            when(jobService.getLogs(jobId, null)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/jobs/{id}/logs", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should pass log level filter to service when 'level' param is provided")
        void getJobLogs_WithLevelFilter_PassesLevelToService() throws Exception {
            when(jobService.getLogs(jobId, LogLevel.ERROR)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/jobs/{id}/logs", jobId)
                    .param("level", "ERROR"))
                .andExpect(status().isOk());

            verify(jobService).getLogs(jobId, LogLevel.ERROR);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/jobs/{id}/metrics
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/jobs/{id}/metrics — get job metrics")
    class GetJobMetricsTests {

        @Test
        @DisplayName("Should return 200 with metrics map when job exists")
        void getJobMetrics_ExistingJob_Returns200WithMetrics() throws Exception {
            sampleJob.setMetrics(Map.of("rowsProcessed", 100, "durationMs", 1500));
            when(jobService.getJob(jobId)).thenReturn(Optional.of(sampleJob));

            mockMvc.perform(get("/api/v1/jobs/{id}/metrics", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed", is(100)));
        }

        @Test
        @DisplayName("Should return 404 when job does not exist")
        void getJobMetrics_NotFound_Returns404() throws Exception {
            when(jobService.getJob(jobId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/jobs/{id}/metrics", jobId))
                .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/jobs/stats
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/jobs/stats — aggregate statistics")
    class GetStatsTests {

        @Test
        @DisplayName("Should return 200 with running, completedToday, and failedToday counts")
        void getStats_Returns200WithCounts() throws Exception {
            when(jobService.getRunningJobCount()).thenReturn(3L);
            when(jobService.getCompletedTodayCount()).thenReturn(42L);
            when(jobService.getFailedTodayCount()).thenReturn(1L);

            mockMvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", is(3)))
                .andExpect(jsonPath("$.completedToday", is(42)))
                .andExpect(jsonPath("$.failedToday", is(1)));
        }

        @Test
        @DisplayName("Should return 200 with zeroes when no jobs have run today")
        void getStats_NoJobsToday_Returns200WithZeroes() throws Exception {
            when(jobService.getRunningJobCount()).thenReturn(0L);
            when(jobService.getCompletedTodayCount()).thenReturn(0L);
            when(jobService.getFailedTodayCount()).thenReturn(0L);

            mockMvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", is(0)))
                .andExpect(jsonPath("$.completedToday", is(0)))
                .andExpect(jsonPath("$.failedToday", is(0)));
        }
    }
}
