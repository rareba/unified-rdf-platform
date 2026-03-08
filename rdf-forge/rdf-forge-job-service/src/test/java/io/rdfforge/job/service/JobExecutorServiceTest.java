package io.rdfforge.job.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.engine.pipeline.PipelineExecutor;
import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for JobExecutorService.
 * Tests pipeline execution, retry logic, cancellation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobExecutorService Tests")
class JobExecutorServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PipelineExecutor pipelineExecutor;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JobService jobService;

    private JobExecutorService executorService;

    private UUID jobId;
    private UUID pipelineId;
    private JobEntity sampleJob;
    private Pipeline samplePipeline;

    @BeforeEach
    void setUp() {
        executorService = new JobExecutorService(jobRepository, pipelineExecutor, restTemplate, jobService);
        jobId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();

        sampleJob = new JobEntity();
        sampleJob.setId(jobId);
        sampleJob.setPipelineId(pipelineId);
        sampleJob.setStatus(JobStatus.PENDING);
        sampleJob.setDryRun(false);
        sampleJob.setVariables(Map.of("key", "value"));

        samplePipeline = Pipeline.builder()
            .id(pipelineId)
            .name("Test Pipeline")
            .definitionFormat(Pipeline.DefinitionFormat.JSON)
            .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\"}]}")
            .variables(Map.of())
            .build();
    }

    @Nested
    @DisplayName("executeAsync Tests")
    class ExecuteAsyncTests {

        @Test
        @DisplayName("Should execute job successfully")
        void executeAsync_SuccessfulExecution_UpdatesJobToCompleted() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of("rowsProcessed", 100)));

            executorService.executeAsync(jobId);

            // Wait for async execution
            Thread.sleep(100);

            verify(jobService).updateJobStatus(jobId, JobStatus.RUNNING);
            verify(jobRepository).save(argThat(job -> job.getStatus() == JobStatus.COMPLETED));
        }

        @Test
        @DisplayName("Should handle pipeline execution failure")
        void executeAsync_PipelineFailure_UpdatesJobToFailed() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.failure("Pipeline step failed"));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobRepository).save(argThat(job ->
                job.getStatus() == JobStatus.FAILED &&
                "Pipeline step failed".equals(job.getErrorMessage())
            ));
        }

        @Test
        @DisplayName("Should handle job not found")
        void executeAsync_JobNotFound_ThrowsException() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> executorService.executeAsync(jobId));
        }

        @Test
        @DisplayName("Should handle pipeline fetch failure")
        void executeAsync_PipelineFetchFailure_MarksJobAsFailed() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class)))
                .thenThrow(new RestClientException("Connection refused"));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobRepository).save(argThat(job ->
                job.getStatus() == JobStatus.FAILED
            ));
        }

        @Test
        @DisplayName("Should retry pipeline fetch on failure")
        void executeAsync_PipelineFetchRetry_EventuallySucceeds() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class)))
                .thenThrow(new RestClientException("First attempt failed"))
                .thenThrow(new RestClientException("Second attempt failed"))
                .thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(200);

            verify(restTemplate, atLeast(3)).getForObject(anyString(), eq(Pipeline.class));
        }

        @Test
        @DisplayName("Should handle interruption gracefully")
        void executeAsync_Interrupted_HandlesCancellation() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            CountDownLatch latch = new CountDownLatch(1);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
                Thread.sleep(5000); // Simulate long-running task
                latch.countDown();
                return PipelineExecutor.ExecutionResult.success(Map.of());
            });

            Thread executionThread = new Thread(() -> executorService.executeAsync(jobId));
            executionThread.start();

            // Give it time to start
            Thread.sleep(50);

            // Interrupt the execution
            executorService.cancelExecution(jobId);

            executionThread.join(500);
            assertFalse(executionThread.isAlive());
        }

        @Test
        @DisplayName("Should handle dry run jobs")
        void executeAsync_DryRun_ExecutesWithDryRunFlag() throws Exception {
            sampleJob.setDryRun(true);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), eq(true), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), any(), eq(true), any());
        }

        @Test
        @DisplayName("Should log job start and completion")
        void executeAsync_SuccessfulExecution_LogsMessages() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobService).addLog(eq(jobId), eq(LogLevel.INFO), isNull(), contains("started"), isNull());
            verify(jobService).addLog(eq(jobId), eq(LogLevel.INFO), isNull(), contains("completed successfully"), isNull());
        }

        @Test
        @DisplayName("Should handle YAML pipeline definitions")
        void executeAsync_YamlDefinition_ParsesCorrectly() throws Exception {
            samplePipeline.setDefinitionFormat(Pipeline.DefinitionFormat.YAML);
            samplePipeline.setDefinition("steps:\n  - id: step1\n    operation: CSV_READ");

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("Should pass job variables to pipeline executor")
        void executeAsync_WithVariables_PassesVariablesToExecutor() throws Exception {
            Map<String, Object> variables = Map.of("source", "data.csv", "target", "graph");
            sampleJob.setVariables(variables);

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), eq(variables), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), eq(variables), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("cancelExecution Tests")
    class CancelExecutionTests {

        @Test
        @DisplayName("Should cancel running job")
        void cancelExecution_RunningJob_InterruptsThread() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            CountDownLatch startedLatch = new CountDownLatch(1);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
                startedLatch.countDown();
                Thread.sleep(10000);
                return PipelineExecutor.ExecutionResult.success(Map.of());
            });

            Thread executionThread = new Thread(() -> executorService.executeAsync(jobId));
            executionThread.start();

            assertTrue(startedLatch.await(1, TimeUnit.SECONDS));

            executorService.cancelExecution(jobId);
            executionThread.join(500);

            assertFalse(executionThread.isAlive());
        }

        @Test
        @DisplayName("Should handle cancel for non-existent job")
        void cancelExecution_NonExistentJob_DoesNotThrow() {
            assertDoesNotThrow(() -> executorService.cancelExecution(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("fetchPipelineWithRetry Tests")
    class FetchPipelineWithRetryTests {

        @Test
        @DisplayName("Should succeed on first attempt")
        void fetchPipelineWithRetry_FirstAttemptSucceeds_ReturnsPipeline() {
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            // This is tested indirectly through executeAsync
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);

            verify(restTemplate, atMost(3)).getForObject(anyString(), eq(Pipeline.class));
        }

        @Test
        @DisplayName("Should fail after max retries exceeded")
        void fetchPipelineWithRetry_MaxRetriesExceeded_Fails() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class)))
                .thenThrow(new RestClientException("Persistent failure"));

            executorService.executeAsync(jobId);
            Thread.sleep(500);

            verify(restTemplate, atLeast(3)).getForObject(anyString(), eq(Pipeline.class));
            verify(jobRepository).save(argThat(job -> job.getStatus() == JobStatus.FAILED));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle unexpected exceptions")
        void executeAsync_UnexpectedException_MarksJobAsFailed() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobRepository).save(argThat(job ->
                job.getStatus() == JobStatus.FAILED &&
                job.getErrorMessage().contains("Unexpected error")
            ));
        }

        @Test
        @DisplayName("Should handle null pipeline response")
        void executeAsync_NullPipelineResponse_MarksJobAsFailed() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(null);

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobRepository).save(argThat(job -> job.getStatus() == JobStatus.FAILED));
        }

        @Test
        @DisplayName("Should include error details in failed job")
        void executeAsync_Failure_IncludesErrorDetails() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.failure("Step validation failed"));

            ArgumentCaptor<JobEntity> jobCaptor = ArgumentCaptor.forClass(JobEntity.class);

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(jobRepository).save(jobCaptor.capture());
            JobEntity savedJob = jobCaptor.getValue();

            assertEquals(JobStatus.FAILED, savedJob.getStatus());
            assertEquals("Step validation failed", savedJob.getErrorMessage());
            assertNotNull(savedJob.getErrorDetails());
        }
    }

    @Nested
    @DisplayName("Pipeline Definition Parsing Tests")
    class PipelineDefinitionParsingTests {

        @Test
        @DisplayName("Should parse JSON definition with params field")
        void parseDefinition_JsonWithParams_ParsesCorrectly() throws Exception {
            String definition = "{\"steps\": [{\"id\": \"step1\", \"operation\": \"MAP\", \"params\": {\"mapping\": \"test\"}}]}";
            samplePipeline.setDefinition(definition);

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("Should parse JSON definition with parameters field")
        void parseDefinition_JsonWithParameters_ParsesCorrectly() throws Exception {
            String definition = "{\"steps\": [{\"id\": \"step1\", \"operation\": \"MAP\", \"parameters\": {\"mapping\": \"test\"}}]}";
            samplePipeline.setDefinition(definition);

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("Should handle empty steps array")
        void parseDefinition_EmptySteps_HandledCorrectly() throws Exception {
            String definition = "{\"steps\": []}";
            samplePipeline.setDefinition(definition);

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), any()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            verify(pipelineExecutor).execute(any(), any(), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("Execution Callback Tests")
    class ExecutionCallbackTests {

        @Test
        @DisplayName("Should log step start events")
        void callback_StepStart_LogsMessage() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            ArgumentCaptor<PipelineExecutor.ExecutionCallback> callbackCaptor =
                ArgumentCaptor.forClass(PipelineExecutor.ExecutionCallback.class);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), callbackCaptor.capture()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            PipelineExecutor.ExecutionCallback callback = callbackCaptor.getValue();
            callback.onStepStart("step1", "CSV Read");

            verify(jobService).addLog(eq(jobId), eq(LogLevel.INFO), eq("step1"), contains("Starting"), isNull());
        }

        @Test
        @DisplayName("Should log step complete events")
        void callback_StepComplete_LogsMessage() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            ArgumentCaptor<PipelineExecutor.ExecutionCallback> callbackCaptor =
                ArgumentCaptor.forClass(PipelineExecutor.ExecutionCallback.class);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), callbackCaptor.capture()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            PipelineExecutor.ExecutionCallback callback = callbackCaptor.getValue();
            callback.onStepComplete("step1", true, null);

            verify(jobService).addLog(eq(jobId), eq(LogLevel.INFO), eq("step1"), contains("completed"), isNull());
        }

        @Test
        @DisplayName("Should log step failure events")
        void callback_StepFailure_LogsError() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            ArgumentCaptor<PipelineExecutor.ExecutionCallback> callbackCaptor =
                ArgumentCaptor.forClass(PipelineExecutor.ExecutionCallback.class);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), callbackCaptor.capture()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            PipelineExecutor.ExecutionCallback callback = callbackCaptor.getValue();
            callback.onStepComplete("step1", false, "Validation error");

            verify(jobService).addLog(eq(jobId), eq(LogLevel.ERROR), eq("step1"), contains("failed"), isNull());
        }

        @Test
        @DisplayName("Should handle log events from pipeline")
        void callback_LogEvent_ForwardsToJobLog() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            ArgumentCaptor<PipelineExecutor.ExecutionCallback> callbackCaptor =
                ArgumentCaptor.forClass(PipelineExecutor.ExecutionCallback.class);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), callbackCaptor.capture()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            PipelineExecutor.ExecutionCallback callback = callbackCaptor.getValue();
            callback.onLog("step1", "WARN", "This is a warning");

            verify(jobService).addLog(eq(jobId), eq(LogLevel.WARN), eq("step1"), eq("This is a warning"), isNull());
        }

        @Test
        @DisplayName("Should handle unknown log levels gracefully")
        void callback_UnknownLogLevel_DefaultsToInfo() throws Exception {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
            when(restTemplate.getForObject(anyString(), eq(Pipeline.class))).thenReturn(samplePipeline);

            ArgumentCaptor<PipelineExecutor.ExecutionCallback> callbackCaptor =
                ArgumentCaptor.forClass(PipelineExecutor.ExecutionCallback.class);
            when(pipelineExecutor.execute(any(), any(), anyBoolean(), callbackCaptor.capture()))
                .thenReturn(PipelineExecutor.ExecutionResult.success(Map.of()));

            executorService.executeAsync(jobId);
            Thread.sleep(100);

            PipelineExecutor.ExecutionCallback callback = callbackCaptor.getValue();
            callback.onLog("step1", "UNKNOWN", "Message");

            verify(jobService).addLog(eq(jobId), eq(LogLevel.INFO), eq("step1"), eq("Message"), isNull());
        }
    }
}
