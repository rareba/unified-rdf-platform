package io.rdfforge.common.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an audit log entry.
 * Stores all CRUD operations and security-relevant events for compliance and debugging.
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_correlation_id", columnList = "correlation_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who performed the action (username or user ID).
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /**
     * User's display name at the time of the action.
     */
    @Column(name = "user_name", length = 255)
    private String userName;

    /**
     * The type of action performed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /**
     * The type of entity affected (e.g., "Pipeline", "Job", "User").
     */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /**
     * The ID of the entity affected.
     */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /**
     * Human-readable description of the action.
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Entity state before the action (for updates and deletes).
     * Sensitive fields should be masked.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "before_values", columnDefinition = "TEXT")
    private String beforeValues;

    /**
     * Entity state after the action (for creates and updates).
     * Sensitive fields should be masked.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "after_values", columnDefinition = "TEXT")
    private String afterValues;

    /**
     * Changes made - simplified view of what changed.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;

    /**
     * IP address of the user.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Correlation ID for request tracing.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * The service that handled the request.
     */
    @Column(name = "service_name", length = 100)
    private String serviceName;

    /**
     * Success or failure status.
     */
    @Column(name = "success", nullable = false)
    private boolean success;

    /**
     * Error message if the action failed.
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * Timestamp of the action.
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Additional metadata as key-value pairs.
     */
    @ElementCollection
    @CollectionTable(name = "audit_log_metadata", joinColumns = @JoinColumn(name = "audit_log_id"))
    @MapKeyColumn(name = "meta_key", length = 100)
    @Column(name = "meta_value", length = 500)
    private Map<String, String> metadata;

    /**
     * Pre-persist hook to set timestamp.
     */
    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Audit action types.
     */
    public enum AuditAction {
        // CRUD operations
        CREATE,
        READ,
        UPDATE,
        DELETE,
        LIST,
        
        // Security operations
        LOGIN,
        LOGOUT,
        AUTHENTICATION_FAILED,
        AUTHORIZATION_DENIED,
        PASSWORD_CHANGE,
        PASSWORD_RESET,
        TOKEN_CREATED,
        TOKEN_REVOKED,
        
        // Job operations
        JOB_STARTED,
        JOB_COMPLETED,
        JOB_FAILED,
        JOB_CANCELLED,
        
        // Pipeline operations
        PIPELINE_EXECUTED,
        PIPELINE_VALIDATED,
        
        // Data operations
        DATA_EXPORTED,
        DATA_IMPORTED,
        DATA_VALIDATED,
        
        // System operations
        CONFIG_CHANGED,
        BACKUP_CREATED,
        BACKUP_RESTORED
    }
}
