package io.rdfforge.job.service;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobEntity.TriggerType;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.repository.JobLogRepository;
import io.rdfforge.job.repository.JobRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class JobService {
    
    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final JobExecutorService executorService;
    
    public JobService(JobRepository jobRepository, JobLogRepository jobLogRepository, @Lazy JobExecutorService executorService) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.executorService = executorService;
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
        jobRepository.findById(id).ifPresent(job -> {
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING) {
                job.setStatus(JobStatus.CANCELLED);
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
                
                if (job.getStatus() == JobStatus.RUNNING) {
                    executorService.cancelExecution(id);
                }
            }
        });
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
        }).orElseThrow(() -> new RuntimeException("Job not found: " + id));
    }
    
    public void updateJobStatus(UUID id, JobStatus status) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setStatus(status);
            if (status == JobStatus.RUNNING && job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
            }
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
                job.setCompletedAt(Instant.now());
            }
            jobRepository.save(job);
        });
    }
    
    public void updateJobMetrics(UUID id, Map<String, Object> metrics) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setMetrics(metrics);
            jobRepository.save(job);
        });
    }
    
    public void setJobError(UUID id, String message, Map<String, Object> details) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(message);
            job.setErrorDetails(details);
            jobRepository.save(job);
        });
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
            jobLogRepository.save(log);
        });
    }
    
    public List<JobLogEntity> getLogs(UUID jobId, LogLevel minLevel) {
        if (minLevel == null) {
            return jobLogRepository.findByJobIdOrderByTimestampAsc(jobId);
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
        return jobLogRepository.findByJobIdOrderByTimestampAsc(jobId, PageRequest.of(page, size));
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
