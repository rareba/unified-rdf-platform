package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    private UUID id;
    private UUID pipelineId;
    private Integer pipelineVersion;
    private JobStatus status;
    private Integer priority;
    private Map<String, Object> variables;
    private TriggerType triggeredBy;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private Map<String, Object> errorDetails;
    private JobMetrics metrics;
    private UUID createdBy;
    private Instant createdAt;

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum TriggerType {
        MANUAL,
        SCHEDULE,
        API,
        WEBHOOK
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobMetrics {
        private Long rowsProcessed;
        private Long quadsGenerated;
        private Long durationMs;
        private Long bytesRead;
        private Long bytesWritten;
    }
}
