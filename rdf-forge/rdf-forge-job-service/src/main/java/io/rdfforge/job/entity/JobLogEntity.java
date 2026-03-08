package io.rdfforge.job.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_logs", indexes = {
    @Index(name = "idx_job_logs_job_id_timestamp", columnList = "job_id, created_at"),
    @Index(name = "idx_job_logs_job_id_level", columnList = "job_id, level")
})
public class JobLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;

    // Expose job ID for API responses without causing lazy loading issues
    @Transient
    public UUID getJobId() {
        return job != null ? job.getId() : null;
    }

    @Column(name = "created_at")
    private Instant timestamp = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level = LogLevel.INFO;

    @Column(name = "step_id")
    private String step;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> details;

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public JobEntity getJob() { return job; }
    public void setJob(JobEntity job) { this.job = job; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public LogLevel getLevel() { return level; }
    public void setLevel(LogLevel level) { this.level = level; }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
