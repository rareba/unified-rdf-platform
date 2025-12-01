package io.rdfforge.job.controller;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import io.rdfforge.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job execution and monitoring API")
@CrossOrigin(origins = "*")
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
    public ResponseEntity<JobEntity> getJob(@PathVariable UUID id) {
        return jobService.getJob(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Operation(summary = "Create job", description = "Create and queue a new job")
    public ResponseEntity<JobEntity> createJob(@RequestBody CreateJobRequest request) {
        JobEntity job = jobService.createJob(
            request.pipelineId(),
            request.variables(),
            request.priority(),
            Boolean.TRUE.equals(request.dryRun()),
            null
        );
        return ResponseEntity.ok(job);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel job", description = "Cancel a pending or running job")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID id) {
        jobService.cancelJob(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry job", description = "Retry a failed or cancelled job")
    public ResponseEntity<JobEntity> retryJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.retryJob(id));
    }
    
    @GetMapping("/{id}/logs")
    @Operation(summary = "Get job logs", description = "Get execution logs for a job")
    public ResponseEntity<List<JobLogEntity>> getJobLogs(
        @PathVariable UUID id,
        @RequestParam(required = false) LogLevel level
    ) {
        return ResponseEntity.ok(jobService.getLogs(id, level));
    }
    
    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get job metrics", description = "Get execution metrics for a job")
    public ResponseEntity<Map<String, Object>> getJobMetrics(@PathVariable UUID id) {
        return jobService.getJob(id)
            .map(job -> ResponseEntity.ok(job.getMetrics()))
            .orElse(ResponseEntity.notFound().build());
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
}
