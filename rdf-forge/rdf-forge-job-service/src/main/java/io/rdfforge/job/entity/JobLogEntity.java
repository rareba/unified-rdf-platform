package io.rdfforge.job.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_logs")
public class JobLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;
    
    @Column(nullable = false)
    private Instant timestamp = Instant.now();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level = LogLevel.INFO;
    
    @Column
    private String step;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = JsonMapConverter.class)
    private java.util.Map<String, Object> details;
    
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
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
    
    public java.util.Map<String, Object> getDetails() { return details; }
    public void setDetails(java.util.Map<String, Object> details) { this.details = details; }
}
