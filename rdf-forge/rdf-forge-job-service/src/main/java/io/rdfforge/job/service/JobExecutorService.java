package io.rdfforge.job.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;
import io.rdfforge.common.util.PipelineDefinitionParser;
import io.rdfforge.engine.pipeline.PipelineExecutor;
import io.rdfforge.engine.pipeline.PipelineExecutor.PipelineDefinition;
import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobExecutorService {
    
    private static final Logger log = LoggerFactory.getLogger(JobExecutorService.class);
    
    private final JobRepository jobRepository;
    private final PipelineExecutor pipelineExecutor;
    private final RestTemplate restTemplate;
    private final JobService jobService;
    private final ConcurrentHashMap<UUID, Thread> runningJobs = new ConcurrentHashMap<>();

    @Value("${PIPELINE_SERVICE_URL:http://pipeline-service:8001}")
    private String pipelineServiceUrl;

    public JobExecutorService(JobRepository jobRepository, PipelineExecutor pipelineExecutor,
                              RestTemplate restTemplate, JobService jobService) {
        this.jobRepository = jobRepository;
        this.pipelineExecutor = pipelineExecutor;
        this.restTemplate = restTemplate;
        this.jobService = jobService;
    }
    
    @Async
    public void executeAsync(UUID jobId) {
        log.info("Starting job execution: {}", jobId);

        Thread currentThread = Thread.currentThread();
        runningJobs.put(jobId, currentThread);
        long startTime = System.currentTimeMillis();

        try {
            JobEntity job = jobRepository.findById(jobId).orElseThrow(() ->
                new ResourceNotFoundException("Job", jobId.toString()));

            // Use service method to update status for proper locking
            jobService.updateJobStatus(jobId, JobStatus.RUNNING);

            logToJob(jobId, LogLevel.INFO, null, "Job started" + (job.isDryRun() ? " (DRY RUN)" : ""));

            // Fetch pipeline definition with retry logic
            Pipeline pipeline = fetchPipelineWithRetry(job.getPipelineId(), 3);
            List<PipelineStep> steps = parseDefinition(pipeline.getDefinition(), pipeline.getDefinitionFormat());

            PipelineDefinition pipelineDef = PipelineDefinition.builder()
                .id(pipeline.getId().toString())
                .name(pipeline.getName())
                .steps(steps)
                .defaultVariables(pipeline.getVariables())
                .build();

            // Execute pipeline with timeout handling
            PipelineExecutor.ExecutionResult result = executePipelineWithTimeout(
                pipelineDef, job.getVariables(), job.isDryRun(),
                new JobExecutionCallback(jobId, jobService), jobId);

            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                job.setStatus(JobStatus.COMPLETED);
                job.setCompletedAt(java.time.Instant.now());
                job.setMetrics(result.getMetrics());
                log.info("Job {} completed successfully in {}ms", jobId, duration);
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(java.time.Instant.now());
                job.setErrorMessage(result.getErrorMessage());
                job.setErrorDetails(buildErrorDetails(new Exception(result.getErrorMessage()), duration));
                log.error("Job {} failed: {} (duration: {}ms)", jobId, result.getErrorMessage(), duration);
            }
            jobRepository.save(job);

            logToJob(jobId, result.isSuccess() ? LogLevel.INFO : LogLevel.ERROR, null,
                "Job " + (result.isSuccess() ? "completed successfully" : "failed") +
                " in " + duration + "ms");

        } catch (ResourceNotFoundException e) {
            log.error("Job {} failed - resource not found: {}", jobId, e.getMessage());
            handleJobFailure(jobId, "Resource not found: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Job {} failed with unexpected error", jobId, e);
            handleJobFailure(jobId, "Unexpected error: " + e.getMessage(), e);
        } finally {
            runningJobs.remove(jobId);
        }
    }

    /**
     * Handle job cancellation with proper cleanup.
     */
    private void handleJobCancellation(UUID jobId) {
        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(JobStatus.CANCELLED);
                job.setCompletedAt(java.time.Instant.now());
                jobRepository.save(job);
            });
            logToJob(jobId, LogLevel.INFO, null, "Job cancelled by user request");
        } catch (Exception e) {
            log.error("Error handling job cancellation for job {}", jobId, e);
        }
    }

    /**
     * Handle job failure with proper error logging.
     */
    private void handleJobFailure(UUID jobId, String message, Exception e) {
        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(java.time.Instant.now());
                job.setErrorMessage(message);
                job.setErrorDetails(buildErrorDetails(e, 0));
                jobRepository.save(job);
            });
            logToJob(jobId, LogLevel.ERROR, null, "Job execution failed: " + message);
        } catch (Exception ex) {
            log.error("Error handling job failure for job {}", jobId, ex);
        }
    }

    /**
     * Fetch pipeline with retry logic for resilience.
     */
    private Pipeline fetchPipelineWithRetry(UUID pipelineId, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return fetchPipeline(pipelineId);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed to fetch pipeline {}: {}", attempt, pipelineId, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Failed to fetch pipeline after " + maxRetries + " attempts", lastException);
    }

    /**
     * Execute pipeline with timeout protection.
     */
    private PipelineExecutor.ExecutionResult executePipelineWithTimeout(
            PipelineDefinition pipelineDef,
            Map<String, Object> variables,
            boolean dryRun,
            PipelineExecutor.ExecutionCallback callback,
            UUID jobId) {
        // The actual timeout handling is done via the scheduled timeout checker in JobService
        return pipelineExecutor.execute(pipelineDef, variables, dryRun, callback);
    }
    
    private Pipeline fetchPipeline(UUID pipelineId) throws ResourceNotFoundException {
        String url = pipelineServiceUrl + "/api/v1/pipelines/" + pipelineId;
        try {
            Pipeline pipeline = restTemplate.getForObject(url, Pipeline.class);
            if (pipeline == null) {
                throw new ResourceNotFoundException("Pipeline", pipelineId.toString());
            }
            return pipeline;
        } catch (RestClientException e) {
            log.error("Failed to fetch pipeline {} from {}", pipelineId, url, e);
            throw new ResourceNotFoundException("Pipeline", pipelineId.toString());
        }
    }

    private List<PipelineStep> parseDefinition(String definition, Pipeline.DefinitionFormat format) {
        return PipelineDefinitionParser.parse(definition, format);
    }
    
    public void cancelExecution(UUID jobId) {
        Thread thread = runningJobs.get(jobId);
        if (thread != null) {
            thread.interrupt();
        }
    }
    
    private void logToJob(UUID jobId, LogLevel level, String step, String message) {
        log.debug("[Job {}] [{}] {}: {}", jobId, step, level, message);
        jobService.addLog(jobId, level, step, message, null);
    }
    
    /**
     * Build a sanitized error details map suitable for API responses.
     * Limits stack trace to the first 3 frames to avoid leaking internal package structure.
     */
    private Map<String, Object> buildErrorDetails(Exception e, long durationMs) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("errorType", e.getClass().getName());
        details.put("message", e.getMessage());
        details.put("traceId", UUID.randomUUID().toString());
        details.put("durationMs", durationMs);
        details.put("timestamp", java.time.Instant.now().toString());

        // Include only the first 3 stack frames
        StackTraceElement[] frames = e.getStackTrace();
        List<String> limitedFrames = new ArrayList<>();
        for (int i = 0; i < Math.min(3, frames.length); i++) {
            limitedFrames.add(frames[i].toString());
        }
        details.put("stackFrames", limitedFrames);

        return details;
    }

    private static class JobExecutionCallback implements PipelineExecutor.ExecutionCallback {
        private final UUID jobId;
        private final JobService jobService;

        public JobExecutionCallback(UUID jobId, JobService jobService) {
            this.jobId = jobId;
            this.jobService = jobService;
        }

        @Override
        public void onStart(String pipelineId) {
            jobService.addLog(jobId, LogLevel.INFO, null, "Pipeline started", null);
        }

        @Override
        public void onStepStart(String stepId, String stepName) {
            jobService.addLog(jobId, LogLevel.INFO, stepId, "Starting step: " + stepName, null);
        }

        @Override
        public void onStepComplete(String stepId, boolean success, String errorMessage) {
            if (success) {
                jobService.addLog(jobId, LogLevel.INFO, stepId, "Step completed successfully", null);
            } else {
                jobService.addLog(jobId, LogLevel.ERROR, stepId, "Step failed: " + errorMessage, null);
            }
        }

        @Override
        public void onProgress(String stepId, long processed, long total) {
            // Optional: update progress metrics in DB or just log occasionally
        }

        @Override
        public void onLog(String stepId, String level, String message) {
            LogLevel logLevel = LogLevel.INFO;
            try {
                logLevel = LogLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown log level '{}', defaulting to INFO", level);
                logLevel = LogLevel.INFO;
            }
            jobService.addLog(jobId, logLevel, stepId, message, null);
        }

        @Override
        public void onComplete(boolean success, String errorMessage) {
            // Handled in main method
        }
    }
}
