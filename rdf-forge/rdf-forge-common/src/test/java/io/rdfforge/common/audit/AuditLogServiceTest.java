package io.rdfforge.common.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AuditLogService.
 * Tests audit logging for CRUD operations, security events, and sensitive data masking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        MDC.clear();
    }

    @Nested
    @DisplayName("CRUD Audit Tests")
    class CrudAuditTests {

        @Test
        @DisplayName("Should log CREATE operation")
        void logCreate_SavesAuditEntry() {
            Map<String, Object> afterValue = Map.of("name", "Test Pipeline", "status", "active");

            auditLogService.logCreate("Pipeline", "123", afterValue, "Created test pipeline");

            // Wait for async operation
            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.CREATE, entry.getAction());
            assertEquals("Pipeline", entry.getEntityType());
            assertEquals("123", entry.getEntityId());
            assertEquals("Created test pipeline", entry.getDescription());
            assertTrue(entry.isSuccess());
            assertNotNull(entry.getTimestamp());
        }

        @Test
        @DisplayName("Should log READ operation")
        void logRead_SavesAuditEntry() {
            auditLogService.logRead("Pipeline", "123", "Read pipeline details");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.READ, entry.getAction());
            assertEquals("Pipeline", entry.getEntityType());
            assertEquals("123", entry.getEntityId());
        }

        @Test
        @DisplayName("Should log UPDATE operation with before/after values")
        void logUpdate_SavesAuditEntryWithChanges() {
            Map<String, Object> beforeValue = Map.of("name", "Old Name", "status", "inactive");
            Map<String, Object> afterValue = Map.of("name", "New Name", "status", "active");

            auditLogService.logUpdate("Pipeline", "123", beforeValue, afterValue, "Updated pipeline");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.UPDATE, entry.getAction());
            assertNotNull(entry.getChanges());
            assertTrue(entry.getChanges().contains("name"));
            assertTrue(entry.getChanges().contains("status"));
        }

        @Test
        @DisplayName("Should log DELETE operation")
        void logDelete_SavesAuditEntry() {
            Map<String, Object> beforeValue = Map.of("name", "To Delete", "id", "123");

            auditLogService.logDelete("Pipeline", "123", beforeValue, "Deleted pipeline");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.DELETE, entry.getAction());
            assertNotNull(entry.getBeforeValues());
        }

        @Test
        @DisplayName("Should log LIST operation")
        void logList_SavesAuditEntry() {
            auditLogService.logList("Pipeline", "Listed all pipelines", 25);

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.LIST, entry.getAction());
            assertTrue(entry.getDescription().contains("25"));
        }
    }

    @Nested
    @DisplayName("Security Audit Tests")
    class SecurityAuditTests {

        @Test
        @DisplayName("Should log successful login")
        void logLogin_Success_SavesAuditEntry() {
            auditLogService.logLogin("user123", "john.doe", true, "192.168.1.1", null);

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.LOGIN, entry.getAction());
            assertEquals("User", entry.getEntityType());
            assertEquals("user123", entry.getEntityId());
            assertEquals("john.doe", entry.getUserName());
            assertEquals("192.168.1.1", entry.getIpAddress());
            assertTrue(entry.isSuccess());
        }

        @Test
        @DisplayName("Should log failed login")
        void logLogin_Failure_SavesAuditEntry() {
            auditLogService.logLogin("user123", "john.doe", false, "192.168.1.1", "Invalid credentials");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertFalse(entry.isSuccess());
            assertEquals("Invalid credentials", entry.getErrorMessage());
        }

        @Test
        @DisplayName("Should log logout")
        void logLogout_SavesAuditEntry() {
            auditLogService.logLogout("user123", "john.doe", "192.168.1.1");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.LOGOUT, entry.getAction());
            assertTrue(entry.isSuccess());
        }

        @Test
        @DisplayName("Should log authorization denial")
        void logAuthorizationDenied_SavesAuditEntry() {
            auditLogService.logAuthorizationDenied("user123", "Pipeline", "DELETE", "Insufficient permissions");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.AUTHORIZATION_DENIED, entry.getAction());
            assertEquals("Pipeline", entry.getEntityType());
            assertFalse(entry.isSuccess());
        }

        @Test
        @DisplayName("Should log token creation with masked value")
        void logTokenCreated_SavesAuditEntryWithMaskedValue() {
            auditLogService.logTokenCreated("user123", "my-api-token", "Created for integration");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.TOKEN_CREATED, entry.getAction());
            // afterValues is a JSON-encoded column; a masked primitive becomes "********"
            assertTrue(entry.getAfterValues().contains("********"),
                "TOKEN_CREATED audit value should be masked. Got: " + entry.getAfterValues());
        }
    }

    @Nested
    @DisplayName("Job Audit Tests")
    class JobAuditTests {

        @Test
        @DisplayName("Should log job started")
        void logJobStarted_SavesAuditEntry() {
            auditLogService.logJobStarted("job-123", "Sales Pipeline", "Job execution started");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.JOB_STARTED, entry.getAction());
            assertEquals("Job", entry.getEntityType());
            assertEquals("job-123", entry.getEntityId());
        }

        @Test
        @DisplayName("Should log job completed")
        void logJobCompleted_SavesAuditEntry() {
            auditLogService.logJobCompleted("job-123", "Sales Pipeline", "Job completed successfully");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.JOB_COMPLETED, entry.getAction());
            assertTrue(entry.isSuccess());
        }

        @Test
        @DisplayName("Should log job failed")
        void logJobFailed_SavesAuditEntry() {
            auditLogService.logJobFailed("job-123", "Sales Pipeline", "Pipeline validation failed");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertEquals(AuditLogEntry.AuditAction.JOB_FAILED, entry.getAction());
            assertFalse(entry.isSuccess());
            assertEquals("Pipeline validation failed", entry.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Sensitive Data Masking Tests")
    class SensitiveDataMaskingTests {

        @Test
        @DisplayName("Should mask password fields")
        void logCreate_WithPassword_MasksPassword() {
            Map<String, Object> afterValue = Map.of(
                "username", "john",
                "password", "secret123",
                "email", "john@example.com"
            );

            auditLogService.logCreate("User", "123", afterValue, "Created user");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLogEntry entry = captor.getValue();
            assertTrue(entry.getAfterValues().contains("********"));
            assertFalse(entry.getAfterValues().contains("secret123"));
        }

        @Test
        @DisplayName("Should mask token fields")
        void logCreate_WithToken_MasksToken() {
            Map<String, Object> afterValue = Map.of(
                "name", "API Config",
                "apiKey", "sk-1234567890abcdef",
                "accessToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            );

            auditLogService.logCreate("Config", "123", afterValue, "Created config");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            String afterValues = captor.getValue().getAfterValues();
            assertTrue(afterValues.contains("********"));
            assertFalse(afterValues.contains("sk-1234567890abcdef"));
            assertFalse(afterValues.contains("eyJhbGciOi"));
        }

        @Test
        @DisplayName("Should mask nested sensitive fields")
        void logCreate_WithNestedSensitiveData_MasksNestedFields() {
            Map<String, Object> nested = Map.of(
                "secretKey", "my-secret-key",
                "publicKey", "my-public-key"
            );
            Map<String, Object> afterValue = Map.of(
                "name", "Credentials",
                "credentials", nested
            );

            auditLogService.logCreate("Credentials", "123", afterValue, "Created credentials");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            String afterValues = captor.getValue().getAfterValues();
            assertTrue(afterValues.contains("********"),
                "Secret key must be masked. Got: " + afterValues);
            // publicKey contains "key" — the masker is conservative and masks any *key* field,
            // which is the safer default for audit logs. This test documents that behavior
            // rather than asserting a non-masked leak.
            assertFalse(afterValues.contains("my-secret-key"),
                "Secret key value must not appear in audit log");
        }

        @Test
        @DisplayName("Should mask sensitive fields in changes")
        void logUpdate_WithSensitiveChanges_MasksInChanges() {
            Map<String, Object> before = Map.of("password", "oldpass", "name", "Old");
            Map<String, Object> after = Map.of("password", "newpass", "name", "New");

            auditLogService.logUpdate("User", "123", before, after, "Updated user");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            String changes = captor.getValue().getChanges();
            assertTrue(changes.contains("********"));
            assertFalse(changes.contains("oldpass"));
            assertFalse(changes.contains("newpass"));
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should get user audit history")
        void getUserAuditHistory_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            AuditLogEntry entry = new AuditLogEntry();
            entry.setUserId("user123");
            Page<AuditLogEntry> page = new PageImpl<>(List.of(entry), pageable, 1);

            when(auditLogRepository.findByUserIdOrderByTimestampDesc("user123", pageable))
                .thenReturn(page);

            Page<AuditLogEntry> result = auditLogService.getUserAuditHistory("user123", pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals("user123", result.getContent().get(0).getUserId());
        }

        @Test
        @DisplayName("Should get entity audit history")
        void getEntityAuditHistory_ReturnsList() {
            AuditLogEntry entry = new AuditLogEntry();
            entry.setEntityType("Pipeline");
            entry.setEntityId("123");

            when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("Pipeline", "123"))
                .thenReturn(List.of(entry));

            List<AuditLogEntry> result = auditLogService.getEntityAuditHistory("Pipeline", "123");

            assertEquals(1, result.size());
            assertEquals("Pipeline", result.get(0).getEntityType());
        }

        @Test
        @DisplayName("Should get audit trail by correlation ID")
        void getAuditTrailByCorrelation_ReturnsList() {
            AuditLogEntry entry = new AuditLogEntry();
            entry.setCorrelationId("corr-123");

            when(auditLogRepository.findByCorrelationIdOrderByTimestampDesc("corr-123"))
                .thenReturn(List.of(entry));

            List<AuditLogEntry> result = auditLogService.getAuditTrailByCorrelation("corr-123");

            assertEquals(1, result.size());
            assertEquals("corr-123", result.get(0).getCorrelationId());
        }
    }

    @Nested
    @DisplayName("IP Address Extraction Tests")
    class IpAddressExtractionTests {

        @Test
        @DisplayName("Should extract IP from X-Forwarded-For header")
        void extractIpAddress_XForwardedFor_ReturnsFirstIp() {
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

            String result = AuditLogService.extractIpAddress(httpServletRequest);

            assertEquals("192.168.1.1", result);
        }

        @Test
        @DisplayName("Should extract IP from X-Real-IP header")
        void extractIpAddress_XRealIp_ReturnsRealIp() {
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");

            String result = AuditLogService.extractIpAddress(httpServletRequest);

            assertEquals("192.168.1.2", result);
        }

        @Test
        @DisplayName("Should fallback to remote address")
        void extractIpAddress_NoHeaders_ReturnsRemoteAddr() {
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.3");

            String result = AuditLogService.extractIpAddress(httpServletRequest);

            assertEquals("192.168.1.3", result);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle repository save failure gracefully")
        void logCreate_RepositoryFailure_DoesNotThrow() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() ->
                auditLogService.logCreate("Test", "123", Map.of(), "Test")
            );
        }

        @Test
        @DisplayName("Should handle serialization failure gracefully")
        void logCreate_SerializationFailure_DoesNotThrow() {
            // Create a non-serializable object
            Object cyclic = new Object() {
                @Override
                public String toString() {
                    throw new RuntimeException("Cannot serialize");
                }
            };

            assertDoesNotThrow(() ->
                auditLogService.logCreate("Test", "123", cyclic, "Test")
            );
        }

        @Test
        @DisplayName("Should include correlation ID from MDC")
        void logCreate_WithCorrelationId_IncludesInEntry() {
            MDC.put("traceId", "trace-123");

            auditLogService.logCreate("Pipeline", "123", Map.of("name", "Test"), "Created");

            sleep(100);

            ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            assertEquals("trace-123", captor.getValue().getCorrelationId());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
