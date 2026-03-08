package io.rdfforge.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for audit logging of CRUD operations and security events.
 * 
 * This service provides comprehensive audit trail capabilities including:
 * - Automatic logging of CRUD operations
 * - Security event tracking
 * - Before/after value capture
 * - Correlation with request tracing
 * 
 * All audit operations are performed asynchronously to avoid impacting
 * application performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "secret", "token", "key", "credential", "auth",
        "passwordHash", "apiKey", "privateKey", "accessToken", "refreshToken"
    );
    
    private static final String MASK = "********";
    
    private final ObjectMapper objectMapper;
    
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ==================== CRUD Audit Methods ====================

    /**
     * Log a CREATE operation.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(String entityType, String entityId, Object afterValue, String description) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.CREATE,
                entityType,
                entityId,
                null,
                maskSensitiveData(afterValue),
                description,
                true,
                null
            );
            
            auditLogRepository.save(entry);
            log.debug("Audit log: CREATE {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to log CREATE audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * Log a READ operation.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRead(String entityType, String entityId, String description) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.READ,
                entityType,
                entityId,
                null,
                null,
                description,
                true,
                null
            );
            
            auditLogRepository.save(entry);
            log.debug("Audit log: READ {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to log READ audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * Log an UPDATE operation with before/after values.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUpdate(String entityType, String entityId, Object beforeValue, Object afterValue, String description) {
        try {
            String changes = calculateChanges(beforeValue, afterValue);
            
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.UPDATE,
                entityType,
                entityId,
                maskSensitiveData(beforeValue),
                maskSensitiveData(afterValue),
                description,
                true,
                null
            );
            entry.setChanges(changes);
            
            auditLogRepository.save(entry);
            log.debug("Audit log: UPDATE {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to log UPDATE audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * Log a DELETE operation.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDelete(String entityType, String entityId, Object beforeValue, String description) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.DELETE,
                entityType,
                entityId,
                maskSensitiveData(beforeValue),
                null,
                description,
                true,
                null
            );
            
            auditLogRepository.save(entry);
            log.debug("Audit log: DELETE {} {}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to log DELETE audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * Log a LIST operation.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logList(String entityType, String description, int resultCount) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.LIST,
                entityType,
                null,
                null,
                null,
                description + " (returned " + resultCount + " results)",
                true,
                null
            );
            
            auditLogRepository.save(entry);
            log.debug("Audit log: LIST {}", entityType);
        } catch (Exception e) {
            log.error("Failed to log LIST audit entry for {}: {}", entityType, e.getMessage());
        }
    }

    // ==================== Security Audit Methods ====================

    /**
     * Log a successful login.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(String userId, String userName, boolean success, String ipAddress, String failureReason) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.LOGIN,
                "User",
                userId,
                null,
                null,
                success ? "User logged in successfully" : "Login failed: " + failureReason,
                success,
                failureReason
            );
            entry.setIpAddress(ipAddress);
            entry.setUserName(userName);
            
            auditLogRepository.save(entry);
            log.info("Audit log: LOGIN {} - {}", userId, success ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            log.error("Failed to log LOGIN audit entry: {}", e.getMessage());
        }
    }

    /**
     * Log a logout.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(String userId, String userName, String ipAddress) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.LOGOUT,
                "User",
                userId,
                null,
                null,
                "User logged out",
                true,
                null
            );
            entry.setIpAddress(ipAddress);
            entry.setUserName(userName);
            
            auditLogRepository.save(entry);
            log.info("Audit log: LOGOUT {}", userId);
        } catch (Exception e) {
            log.error("Failed to log LOGOUT audit entry: {}", e.getMessage());
        }
    }

    /**
     * Log an authorization failure.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthorizationDenied(String userId, String resource, String action, String reason) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.AUTHORIZATION_DENIED,
                resource,
                null,
                null,
                null,
                "Access denied for action '" + action + "' on " + resource + ": " + reason,
                false,
                reason
            );
            
            auditLogRepository.save(entry);
            log.warn("Audit log: AUTHORIZATION_DENIED {} on {} - {}", userId, resource, reason);
        } catch (Exception e) {
            log.error("Failed to log AUTHORIZATION_DENIED audit entry: {}", e.getMessage());
        }
    }

    /**
     * Log a token creation.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenCreated(String userId, String tokenName, String description) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                AuditLogEntry.AuditAction.TOKEN_CREATED,
                "PersonalAccessToken",
                tokenName,
                null,
                MASK,
                description,
                true,
                null
            );
            
            auditLogRepository.save(entry);
            log.info("Audit log: TOKEN_CREATED {}", tokenName);
        } catch (Exception e) {
            log.error("Failed to log TOKEN_CREATED audit entry: {}", e.getMessage());
        }
    }

    // ==================== Job Audit Methods ====================

    /**
     * Log a job started event.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logJobStarted(String jobId, String pipelineName, String description) {
        logJobEvent(AuditLogEntry.AuditAction.JOB_STARTED, jobId, pipelineName, description, true, null);
    }

    /**
     * Log a job completed event.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logJobCompleted(String jobId, String pipelineName, String description) {
        logJobEvent(AuditLogEntry.AuditAction.JOB_COMPLETED, jobId, pipelineName, description, true, null);
    }

    /**
     * Log a job failed event.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logJobFailed(String jobId, String pipelineName, String errorMessage) {
        logJobEvent(AuditLogEntry.AuditAction.JOB_FAILED, jobId, pipelineName, null, false, errorMessage);
    }

    private void logJobEvent(AuditLogEntry.AuditAction action, String jobId, String pipelineName, 
                            String description, boolean success, String errorMessage) {
        try {
            AuditLogEntry entry = buildAuditEntry(
                action,
                "Job",
                jobId,
                null,
                null,
                description != null ? description : "Job " + action.name().toLowerCase() + " for pipeline: " + pipelineName,
                success,
                errorMessage
            );
            
            auditLogRepository.save(entry);
            log.debug("Audit log: {} {}", action, jobId);
        } catch (Exception e) {
            log.error("Failed to log {} audit entry: {}", action, e.getMessage());
        }
    }

    // ==================== Query Methods ====================

    /**
     * Get audit entries for a specific user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogEntry> getUserAuditHistory(String userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Get audit entries for a specific entity.
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntry> getEntityAuditHistory(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    /**
     * Get audit entries by correlation ID.
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditTrailByCorrelation(String correlationId) {
        return auditLogRepository.findByCorrelationIdOrderByTimestampDesc(correlationId);
    }

    // ==================== Private Helper Methods ====================

    private AuditLogEntry buildAuditEntry(AuditLogEntry.AuditAction action, String entityType, 
                                         String entityId, Object beforeValue, Object afterValue,
                                         String description, boolean success, String errorMessage) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDescription(description);
        entry.setSuccess(success);
        entry.setErrorMessage(errorMessage);
        entry.setTimestamp(Instant.now());
        
        // Set user info from security context
        entry.setUserId(getCurrentUserId());
        entry.setUserName(getCurrentUserName());
        
        // Set values
        if (beforeValue != null) {
            entry.setBeforeValues(serializeToJson(beforeValue));
        }
        if (afterValue != null) {
            entry.setAfterValues(serializeToJson(afterValue));
        }
        
        // Set correlation ID from MDC
        entry.setCorrelationId(MDC.get("traceId"));
        
        // Set service name
        entry.setServiceName(System.getProperty("spring.application.name", "unknown"));
        
        return entry;
    }

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return value.toString();
        }
    }

    private Object maskSensitiveData(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            Map<String, Object> map = objectMapper.convertValue(value, Map.class);
            return maskSensitiveFields(map);
        } catch (Exception e) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> maskSensitiveFields(Map<String, Object> map) {
        Map<String, Object> masked = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            
            if (isSensitiveField(key)) {
                masked.put(key, MASK);
            } else if (val instanceof Map) {
                masked.put(key, maskSensitiveFields((Map<String, Object>) val));
            } else if (val instanceof List) {
                masked.put(key, maskSensitiveList((List<?>) val));
            } else {
                masked.put(key, val);
            }
        }
        
        return masked;
    }

    @SuppressWarnings("unchecked")
    private List<Object> maskSensitiveList(List<?> list) {
        return list.stream()
            .map(item -> {
                if (item instanceof Map) {
                    return maskSensitiveFields((Map<String, Object>) item);
                }
                return item;
            })
            .collect(Collectors.toList());
    }

    private boolean isSensitiveField(String fieldName) {
        String lowerName = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream()
            .anyMatch(sensitive -> lowerName.contains(sensitive.toLowerCase()));
    }

    private String calculateChanges(Object before, Object after) {
        if (before == null || after == null) {
            return null;
        }
        
        try {
            Map<String, Object> beforeMap = objectMapper.convertValue(before, Map.class);
            Map<String, Object> afterMap = objectMapper.convertValue(after, Map.class);
            
            Map<String, Object> changes = new LinkedHashMap<>();
            
            for (String key : afterMap.keySet()) {
                Object beforeVal = beforeMap.get(key);
                Object afterVal = afterMap.get(key);
                
                if (!Objects.equals(beforeVal, afterVal)) {
                    if (isSensitiveField(key)) {
                        changes.put(key, Map.of("old", MASK, "new", MASK));
                    } else {
                        changes.put(key, Map.of("old", beforeVal, "new", afterVal));
                    }
                }
            }
            
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUserId() {
        // Try to get from MDC first
        String userId = MDC.get("userId");
        if (userId != null) {
            return userId;
        }
        
        // Try to get from SecurityContext
        try {
            org.springframework.security.core.context.SecurityContext context = 
                org.springframework.security.core.context.SecurityContextHolder.getContext();
            if (context.getAuthentication() != null && context.getAuthentication().isAuthenticated()) {
                return context.getAuthentication().getName();
            }
        } catch (Exception e) {
            log.debug("Could not get user from SecurityContext: {}", e.getMessage());
        }
        
        return "anonymous";
    }

    private String getCurrentUserName() {
        return getCurrentUserId(); // Can be extended to fetch actual display name
    }

    /**
     * Extract IP address from HTTP request.
     */
    public static String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
