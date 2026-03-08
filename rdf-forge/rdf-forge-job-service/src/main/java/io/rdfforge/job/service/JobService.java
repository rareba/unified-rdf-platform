package io.rdfforge.job.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.metrics.RdfForgeMetrics;
import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobEntity.TriggerType;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobLogRepository;
import io.rdfforge.job.repository.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Transactional
@Slf4j
public class JobService {
    
    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final JobExecutorService executorService;
    private final JobLogWebSocketService webSocketService;

    private final Counter jobsCreatedCounter;
    private final Counter jobsCompletedCounter;
    private final Counter jobsFailedCounter;
    private final Timer jobDurationTimer;

    // Lock map to prevent race conditions in job status updates
    private final ConcurrentHashMap<UUID, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    // Job timeout configuration (default 4 hours)
    private static final long JOB_TIMEOUT_HOURS = 4;

    public JobService(JobRepository jobRepository, JobLogRepository jobLogRepository,
                      @Lazy JobExecutorService executorService, JobLogWebSocketService webSocketService,
                      MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.executorService = executorService;
        this.webSocketService = webSocketService;

        this.jobsCreatedCounter = Counter.builder(RdfForgeMetrics.JOBS_CREATED)
                .description("Total number of jobs created")
                .register(meterRegistry);
        this.jobsCompletedCounter = Counter.builder(RdfForgeMetrics.JOBS_COMPLETED)
                .description("Total number of jobs completed successfully")
                .register(meterRegistry);
        this.jobsFailedCounter = Counter.builder(RdfForgeMetrics.JOBS_FAILED)
                .description("Total number of jobs that failed")
                .register(meterRegistry);
        this.jobDurationTimer = Timer.builder(RdfForgeMetrics.JOB_DURATION)
                .description("Duration of job executions")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * Get or create a lock for a specific job ID.
     */
    private ReentrantLock getJobLock(UUID jobId) {
        return jobLocks.computeIfAbsent(jobId, k -> new ReentrantLock());
    }
    
    public Page<JobEntity> getJobs(JobStatus status, UUID pipelineId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return jobRepository.findWithFilters(status, pipelineId, pageable);
    }
    
    public Optional<JobEntity> getJob(UUID id) {
        return jobRepository.findById(id);
    }
    
    public JobEntity createJob(UUID pipelineId, Map<String, Object> variables, Integer priority, boolean dryRun, UUID userId) {
        JobEntity job = new JobEntity();
        job.setPipelineId(pipelineId);
        job.setVariables(variables);
        job.setPriority(priority != null ? priority : 5);
        job.setDryRun(dryRun);
        job.setTriggeredBy(TriggerType.MANUAL);
        job.setCreatedBy(userId);
        job.setStatus(JobStatus.PENDING);

        JobEntity savedJob = jobRepository.save(job);
        jobsCreatedCounter.increment();

        // Execute async after transaction commits to avoid race condition
        scheduleAsyncExecution(savedJob.getId());

        return savedJob;
    }

    private void scheduleAsyncExecution(UUID jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executorService.executeAsync(jobId);
                }
            });
        } else {
            // No active transaction, execute immediately
            executorService.executeAsync(jobId);
        }
    }
    
    public JobEntity createScheduledJob(UUID pipelineId, Map<String, Object> variables) {
        JobEntity job = new JobEntity();
        job.setPipelineId(pipelineId);
        job.setVariables(variables);
        job.setPriority(5);
        job.setDryRun(false); // Scheduled jobs are real executions
        job.setTriggeredBy(TriggerType.SCHEDULE);
        job.setStatus(JobStatus.PENDING);

        JobEntity savedJob = jobRepository.save(job);
        scheduleAsyncExecution(savedJob.getId());

        return savedJob;
    }
    
    public void cancelJob(UUID id) {
        ReentrantLock lock = getJobLock(id);
        lock.lock();
        try {
            jobRepository.findById(id).ifPresent(job -> {
                JobStatus originalStatus = job.getStatus();
                if (originalStatus == JobStatus.PENDING || originalStatus == JobStatus.RUNNING) {
                    job.setStatus(JobStatus.CANCELLED);
                    job.setCompletedAt(Instant.now());
                    jobRepository.save(job);

                    if (originalStatus == JobStatus.RUNNING) {
                        executorService.cancelExecution(id);
                    }

                    // Clean up lock for terminal state
                    jobLocks.remove(id);

                    // Publish cancellation via WebSocket
                    webSocketService.publishCompletion(id, false, "Job cancelled by user");
                }
            });
        } finally {
            lock.unlock();
        }
    }
    
    public JobEntity retryJob(UUID id) {
        return jobRepository.findById(id).map(originalJob -> {
            if (originalJob.getStatus() != JobStatus.FAILED && originalJob.getStatus() != JobStatus.CANCELLED) {
                throw new IllegalStateException("Can only retry failed or cancelled jobs");
            }

            JobEntity newJob = new JobEntity();
            newJob.setPipelineId(originalJob.getPipelineId());
            newJob.setPipelineVersion(originalJob.getPipelineVersion());
            newJob.setVariables(originalJob.getVariables());
            newJob.setPriority(originalJob.getPriority());
            newJob.setDryRun(originalJob.isDryRun());
            newJob.setTriggeredBy(TriggerType.MANUAL);
            newJob.setCreatedBy(originalJob.getCreatedBy());
            newJob.setStatus(JobStatus.PENDING);

            JobEntity savedJob = jobRepository.save(newJob);
            scheduleAsyncExecution(savedJob.getId());

            return savedJob;
        }).orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }
    
    @Transactional
    public void updateJobStatus(UUID id, JobStatus status) {
        ReentrantLock lock = getJobLock(id);
        lock.lock();
        try {
            JobEntity job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));

            // Validate status transition
            if (!isValidStatusTransition(job.getStatus(), status)) {
                log.warn("Invalid job status transition from {} to {} for job {}",
                    job.getStatus(), status, id);
                return;
            }

            JobStatus oldStatus = job.getStatus();
            job.setStatus(status);

            if (status == JobStatus.RUNNING && job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
            }
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
                job.setCompletedAt(Instant.now());
                // Clean up the lock after job completion
                jobLocks.remove(id);

                // Record metrics for terminal states
                if (status == JobStatus.COMPLETED) {
                    jobsCompletedCounter.increment();
                } else if (status == JobStatus.FAILED) {
                    jobsFailedCounter.increment();
                }

                // Record job duration if start time is available
                if (job.getStartedAt() != null) {
                    jobDurationTimer.record(
                        java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()));
                }
            }

            JobEntity savedJob = jobRepository.save(job);
            log.debug("Job {} status updated from {} to {} (duration: {}ms)",
                id, oldStatus, status, savedJob.getDuration());

            // Publish status update via WebSocket
            int progress = calculateProgress(savedJob);
            webSocketService.publishStatusUpdate(id, status.name(), progress);

            // Publish completion event if job is finished
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
                webSocketService.publishCompletion(id, status == JobStatus.COMPLETED, savedJob.getErrorMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validate if a status transition is allowed.
     */
    private boolean isValidStatusTransition(JobStatus from, JobStatus to) {
        return switch (from) {
            case PENDING -> to == JobStatus.RUNNING || to == JobStatus.CANCELLED;
            case RUNNING -> to == JobStatus.COMPLETED || to == JobStatus.FAILED || to == JobStatus.CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false; // Terminal states
        };
    }
    
    /**
     * Calculate job progress based on status.
     */
    private int calculateProgress(JobEntity job) {
        return switch (job.getStatus()) {
            case PENDING -> 0;
            case RUNNING -> job.getProgress() != null ? job.getProgress() : 50;
            case COMPLETED, FAILED, CANCELLED -> 100;
        };
    }
    
    public void updateJobMetrics(UUID id, Map<String, Object> metrics) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setMetrics(metrics);
            jobRepository.save(job);
        });
    }
    
    @Transactional
    public void setJobError(UUID id, String message, Map<String, Object> details) {
        ReentrantLock lock = getJobLock(id);
        lock.lock();
        try {
            JobEntity job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));

            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(message);
            job.setErrorDetails(details);
            jobRepository.save(job);

            log.error("Job {} failed: {}. Details: {}", id, message, details);

            // Clean up the lock after job failure
            jobLocks.remove(id);

            // Publish completion event
            webSocketService.publishCompletion(id, false, message);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handle job timeout - mark jobs as failed if they exceed timeout threshold.
     */
    @Transactional
    public void handleJobTimeouts() {
        Instant timeoutThreshold = Instant.now().minus(JOB_TIMEOUT_HOURS, ChronoUnit.HOURS);

        List<JobEntity> timedOutJobs = jobRepository.findRunningJobsStartedBefore(timeoutThreshold);

        for (JobEntity job : timedOutJobs) {
            log.warn("Job {} has exceeded timeout threshold (started at {}). Marking as failed.",
                job.getId(), job.getStartedAt());

            setJobError(job.getId(),
                "Job timed out after " + JOB_TIMEOUT_HOURS + " hours",
                Map.of("timeoutHours", JOB_TIMEOUT_HOURS,
                       "startedAt", job.getStartedAt().toString(),
                       "timeoutAt", Instant.now().toString()));
        }

        if (!timedOutJobs.isEmpty()) {
            log.info("Marked {} jobs as failed due to timeout", timedOutJobs.size());
        }
    }
    
    public void setJobOutput(UUID id, String graphUri) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setOutputGraph(graphUri);
            jobRepository.save(job);
        });
    }
    
    public void addLog(UUID jobId, LogLevel level, String step, String message, Map<String, Object> details) {
        jobRepository.findById(jobId).ifPresent(job -> {
            JobLogEntity log = new JobLogEntity();
            log.setJob(job);
            log.setLevel(level);
            log.setStep(step);
            log.setMessage(message);
            log.setDetails(details);
            JobLogEntity savedLog = jobLogRepository.save(log);
            
            // Publish to WebSocket for real-time streaming
            webSocketService.publishLog(jobId, savedLog);
        });
    }
    
    public List<JobLogEntity> getLogs(UUID jobId, LogLevel minLevel) {
        if (minLevel == null) {
            return jobLogRepository.findByJob_IdOrderByTimestampAsc(jobId);
        }
        List<LogLevel> levels = switch (minLevel) {
            case DEBUG -> List.of(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR);
            case INFO -> List.of(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR);
            case WARN -> List.of(LogLevel.WARN, LogLevel.ERROR);
            case ERROR -> List.of(LogLevel.ERROR);
        };
        return jobLogRepository.findByJobIdAndLevels(jobId, levels);
    }
    
    public Page<JobLogEntity> getLogs(UUID jobId, int page, int size) {
        return jobLogRepository.findByJob_IdOrderByTimestampAsc(jobId, PageRequest.of(page, size));
    }
    
    public long getRunningJobCount() {
        return jobRepository.countByStatus(JobStatus.RUNNING);
    }
    
    public long getCompletedTodayCount() {
        Instant startOfDay = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        return jobRepository.countByStatusSince(JobStatus.COMPLETED, startOfDay);
    }
    
    public long getFailedTodayCount() {
        Instant startOfDay = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        return jobRepository.countByStatusSince(JobStatus.FAILED, startOfDay);
    }
}
