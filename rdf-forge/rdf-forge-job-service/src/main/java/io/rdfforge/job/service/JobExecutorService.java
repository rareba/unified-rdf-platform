package io.rdfforge.job.service;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobExecutorService {
    
    private static final Logger log = LoggerFactory.getLogger(JobExecutorService.class);
    
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final ConcurrentHashMap<UUID, Thread> runningJobs = new ConcurrentHashMap<>();
    
    public JobExecutorService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
        this.jobService = null;
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
            
            logToJob(jobId, LogLevel.INFO, null, "Job started");
            
            simulatePipelineExecution(job);
            
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(java.time.Instant.now());
            job.setMetrics(Map.of(
                "rowsProcessed", 1000,
                "quadsGenerated", 5000
            ));
            jobRepository.save(job);
            
            logToJob(jobId, LogLevel.INFO, null, "Job completed successfully");
            
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
                job.setErrorDetails(Map.of(
                    "stackTrace", getStackTrace(e)
                ));
                jobRepository.save(job);
            });
            logToJob(jobId, LogLevel.ERROR, null, "Job failed: " + e.getMessage());
        } finally {
            runningJobs.remove(jobId);
        }
    }
    
    public void cancelExecution(UUID jobId) {
        Thread thread = runningJobs.get(jobId);
        if (thread != null) {
            thread.interrupt();
        }
    }
    
    private void simulatePipelineExecution(JobEntity job) throws InterruptedException {
        String[] steps = {"LoadData", "Transform", "Validate", "Publish"};
        
        for (String step : steps) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Job cancelled");
            }
            
            logToJob(job.getId(), LogLevel.INFO, step, "Executing step: " + step);
            
            Thread.sleep(1000);
            
            logToJob(job.getId(), LogLevel.INFO, step, "Step completed: " + step);
        }
    }
    
    private void logToJob(UUID jobId, LogLevel level, String step, String message) {
        log.debug("[Job {}] [{}] {}: {}", jobId, step, level, message);
    }
    
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
