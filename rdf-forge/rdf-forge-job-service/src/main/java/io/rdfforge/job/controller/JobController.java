package io.rdfforge.job.controller;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job execution and monitoring API")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class JobController {
    
    private final JobService jobService;
    
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }
    
    @GetMapping
    @Operation(summary = "List jobs", description = "Get paginated list of jobs with optional filters")
    public ResponseEntity<Page<JobEntity>> getJobs(
        @RequestParam(required = false) JobStatus status,
        @RequestParam(required = false) UUID pipelineId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(jobService.getJobs(status, pipelineId, page, size));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get job", description = "Get job details by ID")
    public ResponseEntity<JobEntity> getJob(@PathVariable UUID id, @CurrentUser AuthUser user) {
        JobEntity job = requireReadableJob(id, user);
        return ResponseEntity.ok(job);
    }

    @PostMapping
    @Operation(summary = "Create job", description = "Create and queue a new job")
    public ResponseEntity<JobEntity> createJob(@RequestBody CreateJobRequest request, @CurrentUser AuthUser user) {
        JobEntity job = jobService.createJob(
            request.pipelineId(),
            request.variables(),
            request.priority(),
            Boolean.TRUE.equals(request.dryRun()),
            user.id()
        );
        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel job", description = "Cancel a pending or running job")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableJob(id, user);
        jobService.cancelJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry job", description = "Retry a failed or cancelled job")
    public ResponseEntity<JobEntity> retryJob(@PathVariable UUID id, @CurrentUser AuthUser user) {
        requireWritableJob(id, user);
        return ResponseEntity.ok(jobService.retryJob(id));
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "Get job logs", description = "Get execution logs for a job")
    public ResponseEntity<List<JobLogEntity>> getJobLogs(
        @PathVariable UUID id,
        @RequestParam(required = false) LogLevel level,
        @CurrentUser AuthUser user
    ) {
        requireReadableJob(id, user);
        return ResponseEntity.ok(jobService.getLogs(id, level));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get job metrics", description = "Get execution metrics for a job")
    public ResponseEntity<Map<String, Object>> getJobMetrics(@PathVariable UUID id, @CurrentUser AuthUser user) {
        JobEntity job = requireReadableJob(id, user);
        return ResponseEntity.ok(job.getMetrics());
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Get job statistics", description = "Get aggregate job statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
            "running", jobService.getRunningJobCount(),
            "completedToday", jobService.getCompletedTodayCount(),
            "failedToday", jobService.getFailedTodayCount()
        ));
    }
    
    public record CreateJobRequest(
        UUID pipelineId,
        Map<String, Object> variables,
        Integer priority,
        Boolean dryRun
    ) {}

    /**
     * Ownership guard for jobs. Matches the WebSocket SUBSCRIBE authorization in
     * {@code WebSocketConfig#handleSubscribe} so REST and WS are consistent.
     * Null createdBy → admin-only.
     */
    private JobEntity requireReadableJob(UUID id, AuthUser user) {
        JobEntity job = jobService.getJob(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
        if (user.isAdmin()) return job;
        UUID owner = job.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to view this job");
        }
        return job;
    }

    private void requireWritableJob(UUID id, AuthUser user) {
        JobEntity job = jobService.getJob(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
        if (user.isAdmin()) return;
        UUID owner = job.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to modify this job");
        }
    }
}
