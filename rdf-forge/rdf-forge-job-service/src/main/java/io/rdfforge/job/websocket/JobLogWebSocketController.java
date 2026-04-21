package io.rdfforge.job.websocket;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.service.JobLogWebSocketService;
import io.rdfforge.job.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WebSocket controller for job log streaming.
 * Handles subscriptions to job log topics and provides initial historical logs.
 */
@Controller
public class JobLogWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(JobLogWebSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final JobService jobService;
    private final JobLogWebSocketService logWebSocketService;

    @Autowired
    public JobLogWebSocketController(
            SimpMessagingTemplate messagingTemplate,
            JobService jobService,
            JobLogWebSocketService logWebSocketService) {
        this.messagingTemplate = messagingTemplate;
        this.jobService = jobService;
        this.logWebSocketService = logWebSocketService;
    }

    /**
     * Handle subscription to job logs topic.
     * Returns historical logs immediately upon subscription.
     *
     * @param jobId the job ID from the subscription path
     * @return map containing historical logs and subscription confirmation
     */
    @SubscribeMapping("/topic/jobs/{jobId}/logs")
    public Map<String, Object> subscribeToJobLogs(@DestinationVariable String jobId, Principal principal) {
        log.debug("Client subscribed to logs for job: {}", jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "subscription");
        response.put("jobId", jobId);
        response.put("message", "Subscribed to job logs");

        // Defense in depth: re-verify principal + job ownership here, even though
        // the WebSocketConfig channel interceptor already enforces the same check.
        if (principal == null) {
            log.warn("Subscribe to job {} logs rejected: no authenticated principal", jobId);
            throw new AccessDeniedException("Authentication required");
        }

        try {
            UUID uuid = UUID.fromString(jobId);
            Optional<JobEntity> jobOpt = jobService.getJob(uuid);
            if (jobOpt.isEmpty()) {
                throw new AccessDeniedException("Job not found or access denied");
            }
            UUID owner = jobOpt.get().getCreatedBy();
            if (owner != null && !owner.toString().equals(principal.getName())) {
                // TODO(audit-2026-04-21 P2): also accept admin role here. Current
                // role info isn't plumbed through Principal; channel interceptor
                // already performs the admin-aware check upstream.
                log.warn("Subscribe to job {} logs rejected: principal {} != owner {}",
                        jobId, principal.getName(), owner);
                throw new AccessDeniedException("Not authorized to view this job's logs");
            }
            // Fetch and send historical logs
            List<JobLogEntity> historicalLogs = jobService.getLogs(uuid, null);
            response.put("historicalLogs", historicalLogs.stream()
                    .map(this::convertToDto)
                    .toList());
            response.put("logCount", historicalLogs.size());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid job ID format: {}", jobId);
            response.put("error", "Invalid job ID format");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching historical logs for job: {}", jobId, e);
            response.put("error", "Failed to fetch historical logs");
        }

        return response;
    }

    /**
     * Handle explicit requests for historical logs (optional client-initiated request).
     *
     * @param jobId the job ID
     */
    @MessageMapping("/jobs/{jobId}/logs/history")
    public void requestHistoricalLogs(@DestinationVariable String jobId) {
        log.debug("Client requested historical logs for job: {}", jobId);

        try {
            UUID uuid = UUID.fromString(jobId);
            List<JobLogEntity> historicalLogs = jobService.getLogs(uuid, null);

            Map<String, Object> message = new HashMap<>();
            message.put("type", "historical");
            message.put("logs", historicalLogs.stream()
                    .map(this::convertToDto)
                    .toList());

            messagingTemplate.convertAndSend("/topic/jobs/" + jobId + "/logs", message);
        } catch (Exception e) {
            log.error("Error sending historical logs for job: {}", jobId, e);
        }
    }

    /**
     * Convert JobLogEntity to a DTO map for WebSocket transmission.
     */
    private Map<String, Object> convertToDto(JobLogEntity logEntity) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", logEntity.getId() != null ? logEntity.getId().toString() : null);
        dto.put("jobId", logEntity.getJobId() != null ? logEntity.getJobId().toString() : null);
        dto.put("timestamp", logEntity.getTimestamp() != null ? logEntity.getTimestamp().toString() : Instant.now().toString());
        dto.put("level", logEntity.getLevel() != null ? logEntity.getLevel().name() : "INFO");
        dto.put("step", logEntity.getStep());
        dto.put("message", logEntity.getMessage());
        dto.put("details", logEntity.getDetails());
        return dto;
    }
}
