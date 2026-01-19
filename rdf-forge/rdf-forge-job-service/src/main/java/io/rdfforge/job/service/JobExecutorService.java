package io.rdfforge.job.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;
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
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
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
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Value("${PIPELINE_SERVICE_URL:http://pipeline-service:8001}")
    private String pipelineServiceUrl;
    
    public JobExecutorService(JobRepository jobRepository, PipelineExecutor pipelineExecutor, RestTemplate restTemplate, JobService jobService) {
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
        
        try {
            JobEntity job = jobRepository.findById(jobId).orElseThrow(() -> 
                new RuntimeException("Job not found: " + jobId));
            
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(java.time.Instant.now());
            jobRepository.save(job);
            
            logToJob(jobId, LogLevel.INFO, null, "Job started" + (job.isDryRun() ? " (DRY RUN)" : ""));
            
            // Fetch pipeline definition
            Pipeline pipeline = fetchPipeline(job.getPipelineId());
            List<PipelineStep> steps = parseDefinition(pipeline.getDefinition(), pipeline.getDefinitionFormat());
            
            PipelineDefinition pipelineDef = PipelineDefinition.builder()
                .id(pipeline.getId().toString())
                .name(pipeline.getName())
                .steps(steps)
                .defaultVariables(pipeline.getVariables())
                .build();

            // Execute pipeline
            PipelineExecutor.ExecutionResult result = pipelineExecutor.execute(
                pipelineDef, 
                job.getVariables(), 
                job.isDryRun(),
                new JobExecutionCallback(jobId, jobService)
            );
            
            job.setStatus(result.isSuccess() ? JobStatus.COMPLETED : JobStatus.FAILED);
            job.setCompletedAt(java.time.Instant.now());
            job.setMetrics(result.getMetrics());
            if (!result.isSuccess()) {
                job.setErrorMessage(result.getErrorMessage());
            }
            jobRepository.save(job);
            
            logToJob(jobId, result.isSuccess() ? LogLevel.INFO : LogLevel.ERROR, null, 
                "Job " + (result.isSuccess() ? "completed successfully" : "failed"));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Job {} was cancelled", jobId);
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(JobStatus.CANCELLED);
                job.setCompletedAt(java.time.Instant.now());
                jobRepository.save(job);
            });
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(java.time.Instant.now());
                job.setErrorMessage(e.getMessage());
                job.setErrorDetails(Map.of("stackTrace", getStackTrace(e)));
                jobRepository.save(job);
            });
            logToJob(jobId, LogLevel.ERROR, null, "Job execution error: " + e.getMessage());
        } finally {
            runningJobs.remove(jobId);
        }
    }
    
    private Pipeline fetchPipeline(UUID pipelineId) {
        String url = pipelineServiceUrl + "/api/v1/pipelines/" + pipelineId;
        return restTemplate.getForObject(url, Pipeline.class);
    }

    private List<PipelineStep> parseDefinition(String definition, Pipeline.DefinitionFormat format) throws Exception {
        ObjectMapper mapper = format == Pipeline.DefinitionFormat.YAML ? yamlMapper : jsonMapper;
        Map<String, Object> parsed = mapper.readValue(definition, new TypeReference<>() {});
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stepsData = (List<Map<String, Object>>) parsed.get("steps");
        
        if (stepsData == null) {
            return Collections.emptyList();
        }

        List<PipelineStep> steps = new ArrayList<>();
        for (Map<String, Object> stepData : stepsData) {
            @SuppressWarnings("unchecked")
            // Support both "params" (UI format) and "parameters" (API format)
            Map<String, Object> params = (Map<String, Object>) stepData.get("params");
            if (params == null) {
                params = (Map<String, Object>) stepData.get("parameters");
            }
            PipelineStep step = PipelineStep.builder()
                .id((String) stepData.get("id"))
                .operationType((String) stepData.get("operation"))
                .name((String) stepData.get("name"))
                .parameters(params)
                .inputConnections((List<String>) stepData.get("inputs"))
                .outputConnections((List<String>) stepData.get("outputs"))
                .build();
            steps.add(step);
        }
        return steps;
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
    
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
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
            } catch (Exception ignored) {}
            jobService.addLog(jobId, logLevel, stepId, message, null);
        }

        @Override
        public void onComplete(boolean success, String errorMessage) {
            // Handled in main method
        }
    }
}
