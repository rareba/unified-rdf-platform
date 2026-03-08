package io.rdfforge.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for audit log entries.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    /**
     * Find all audit entries for a specific user.
     */
    Page<AuditLogEntry> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find all audit entries for a specific entity.
     */
    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    /**
     * Find all audit entries by action type.
     */
    Page<AuditLogEntry> findByActionOrderByTimestampDesc(AuditLogEntry.AuditAction action, Pageable pageable);

    /**
     * Find all audit entries within a time range.
     */
    @Query("SELECT a FROM AuditLogEntry a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    Page<AuditLogEntry> findByTimeRange(
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    /**
     * Find all audit entries by correlation ID.
     */
    List<AuditLogEntry> findByCorrelationIdOrderByTimestampDesc(String correlationId);

    /**
     * Find recent audit entries with filtering.
     */
    @Query("SELECT a FROM AuditLogEntry a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:start IS NULL OR a.timestamp >= :start) AND " +
           "(:end IS NULL OR a.timestamp <= :end) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLogEntry> findWithFilters(
        @Param("userId") String userId,
        @Param("entityType") String entityType,
        @Param("action") AuditLogEntry.AuditAction action,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    /**
     * Count audit entries by action type in a time range.
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLogEntry a WHERE a.timestamp BETWEEN :start AND :end GROUP BY a.action")
    List<Object[]> countByActionInTimeRange(
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    /**
     * Delete audit entries older than the specified date.
     */
    void deleteByTimestampBefore(Instant timestamp);
}
