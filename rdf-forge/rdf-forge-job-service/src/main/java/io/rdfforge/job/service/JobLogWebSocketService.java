package io.rdfforge.job.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.rdfforge.job.entity.JobLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing job logs to WebSocket topics.
 * Enables real-time log streaming to connected clients.
 */
@Service
public class JobLogWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(JobLogWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired
    public JobLogWebSocketService(SimpMessagingTemplate messagingTemplate, MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publish a log entry to the WebSocket topic for the specified job.
     * This method is called whenever a new log is generated during job execution.
     *
     * @param jobId the job ID
     * @param logEntity the log entity to publish
     */
    public void publishLog(UUID jobId, JobLogEntity logEntity) {
        if (jobId == null || logEntity == null) {
            return;
        }

        try {
            Map<String, Object> message = createLogMessage(logEntity);
            String destination = "/topic/jobs/" + jobId + "/logs";

            messagingTemplate.convertAndSend(destination, message);
            log.debug("Published log to {}: {}", destination, logEntity.getMessage());
        } catch (Exception e) {
            // Log error but don't throw - WebSocket failures shouldn't break job execution
            log.error("Failed to publish log to WebSocket for job {}: {}", jobId, e.getMessage());
            meterRegistry.counter("websocket.publish.failures", "type", "log").increment();
        }
    }

    /**
     * Publish a log entry created from individual parameters.
     * Convenience method for creating and publishing logs in one call.
     *
     * @param jobId the job ID
     * @param level the log level
     * @param step the step name (can be null)
     * @param message the log message
     * @param details additional details (can be null)
     */
    public void publishLog(UUID jobId, JobLogEntity.LogLevel level, String step, String message, Map<String, Object> details) {
        Map<String, Object> logMessage = new HashMap<>();
        logMessage.put("type", "log");
        logMessage.put("timestamp", Instant.now().toString());
        logMessage.put("level", level.name());
        logMessage.put("step", step);
        logMessage.put("message", message);
        logMessage.put("details", details);

        try {
            String destination = "/topic/jobs/" + jobId + "/logs";
            messagingTemplate.convertAndSend(destination, logMessage);
            log.debug("Published log to {}: {}", destination, message);
        } catch (Exception e) {
            log.error("Failed to publish log to WebSocket for job {}: {}", jobId, e.getMessage());
            meterRegistry.counter("websocket.publish.failures", "type", "log").increment();
        }
    }

    /**
     * Publish a status update for a job.
     * Used to notify clients about job status changes (running, completed, failed, etc.)
     *
     * @param jobId the job ID
     * @param status the job status
     * @param progress the progress percentage (0-100)
     */
    public void publishStatusUpdate(UUID jobId, String status, int progress) {
        if (jobId == null) {
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "status");
            message.put("status", status);
            message.put("progress", progress);
            message.put("timestamp", Instant.now().toString());

            String destination = "/topic/jobs/" + jobId + "/logs";
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Published status update to {}: {} ({}%)", destination, status, progress);
        } catch (Exception e) {
            log.error("Failed to publish status update to WebSocket for job {}: {}", jobId, e.getMessage());
            meterRegistry.counter("websocket.publish.failures", "type", "status").increment();
        }
    }

    /**
     * Publish job completion event.
     *
     * @param jobId the job ID
     * @param success whether the job completed successfully
     * @param errorMessage error message if failed (can be null)
     */
    public void publishCompletion(UUID jobId, boolean success, String errorMessage) {
        if (jobId == null) {
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "completion");
            message.put("success", success);
            message.put("errorMessage", errorMessage);
            message.put("timestamp", Instant.now().toString());

            String destination = "/topic/jobs/" + jobId + "/logs";
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Published completion event to {}: success={}", destination, success);
        } catch (Exception e) {
            log.error("Failed to publish completion event to WebSocket for job {}: {}", jobId, e.getMessage());
            meterRegistry.counter("websocket.publish.failures", "type", "completion").increment();
        }
    }

    /**
     * Create a log message map from a JobLogEntity.
     */
    private Map<String, Object> createLogMessage(JobLogEntity logEntity) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "log");
        message.put("id", logEntity.getId() != null ? logEntity.getId().toString() : null);
        message.put("timestamp", logEntity.getTimestamp() != null ? logEntity.getTimestamp().toString() : Instant.now().toString());
        message.put("level", logEntity.getLevel() != null ? logEntity.getLevel().name() : "INFO");
        message.put("step", logEntity.getStep());
        message.put("message", logEntity.getMessage());
        message.put("details", logEntity.getDetails());
        return message;
    }
}
