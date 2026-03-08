package io.rdfforge.job.service;

import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for JobLogWebSocketService.
 * Tests WebSocket publishing functionality for real-time log streaming.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobLogWebSocketService Tests")
class JobLogWebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private JobLogWebSocketService webSocketService;

    private UUID jobId;

    @BeforeEach
    void setUp() {
        webSocketService = new JobLogWebSocketService(messagingTemplate);
        jobId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("publishLog with JobLogEntity Tests")
    class PublishLogEntityTests {

        @Test
        @DisplayName("Should publish log entity to correct destination")
        void publishLog_WithValidEntity_PublishesToCorrectDestination() {
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, "step1", "Processing started");

            webSocketService.publishLog(jobId, logEntity);

            String expectedDestination = "/topic/jobs/" + jobId + "/logs";
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), any(Map.class));
        }

        @Test
        @DisplayName("Should publish log with correct message structure")
        void publishLog_PublishesCorrectMessageStructure() {
            JobLogEntity logEntity = createLogEntity(LogLevel.ERROR, "validation", "Validation failed");
            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);

            webSocketService.publishLog(jobId, logEntity);

            String expectedDestination = "/topic/jobs/" + jobId + "/logs";
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertEquals("log", message.get("type"));
            assertEquals("ERROR", message.get("level"));
            assertEquals("validation", message.get("step"));
            assertEquals("Validation failed", message.get("message"));
        }

        @Test
        @DisplayName("Should handle null jobId gracefully")
        void publishLog_WithNullJobId_DoesNotPublish() {
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, null, "Test message");

            webSocketService.publishLog(null, logEntity);

            verify(messagingTemplate, never()).convertAndSend(any(), any());
        }

        @Test
        @DisplayName("Should handle null logEntity gracefully")
        void publishLog_WithNullEntity_DoesNotPublish() {
            webSocketService.publishLog(jobId, null);

            verify(messagingTemplate, never()).convertAndSend(any(), any());
        }

        @Test
        @DisplayName("Should include log details when present")
        void publishLog_WithDetails_IncludesDetails() {
            Map<String, Object> details = Map.of("rowCount", 100, "duration", 5000);
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, "step1", "Completed");
            logEntity.setDetails(details);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            webSocketService.publishLog(jobId, logEntity);

            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
            assertEquals(details, messageCaptor.getValue().get("details"));
        }

        @Test
        @DisplayName("Should handle messaging template exception gracefully")
        void publishLog_WhenMessagingFails_LogsError() {
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, "step1", "Test");
            doThrow(new RuntimeException("Connection failed"))
                .when(messagingTemplate).convertAndSend(any(), any());

            assertDoesNotThrow(() -> webSocketService.publishLog(jobId, logEntity));
        }

        @Test
        @DisplayName("Should include log ID when present")
        void publishLog_WithId_IncludesId() {
            UUID logId = UUID.randomUUID();
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, null, "Test");
            logEntity.setId(logId);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            webSocketService.publishLog(jobId, logEntity);

            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
            assertEquals(logId.toString(), messageCaptor.getValue().get("id"));
        }

        @Test
        @DisplayName("Should include timestamp when present")
        void publishLog_WithTimestamp_IncludesTimestamp() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, null, "Test");
            logEntity.setTimestamp(timestamp);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            webSocketService.publishLog(jobId, logEntity);

            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
            assertEquals(timestamp.toString(), messageCaptor.getValue().get("timestamp"));
        }

        @Test
        @DisplayName("Should use current timestamp when entity timestamp is null")
        void publishLog_WithNullTimestamp_UsesCurrentTime() {
            JobLogEntity logEntity = createLogEntity(LogLevel.INFO, null, "Test");
            logEntity.setTimestamp(null);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            webSocketService.publishLog(jobId, logEntity);

            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
            String timestamp = (String) messageCaptor.getValue().get("timestamp");
            assertNotNull(timestamp);
            // Verify it's a valid ISO timestamp
            assertDoesNotThrow(() -> Instant.parse(timestamp));
        }

        @Test
        @DisplayName("Should default to INFO level when entity level is null")
        void publishLog_WithNullLevel_DefaultsToInfo() {
            JobLogEntity logEntity = createLogEntity(null, null, "Test");
            logEntity.setLevel(null);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            webSocketService.publishLog(jobId, logEntity);

            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
            assertEquals("INFO", messageCaptor.getValue().get("level"));
        }
    }

    @Nested
    @DisplayName("publishLog with parameters Tests")
    class PublishLogParametersTests {

        @Test
        @DisplayName("Should publish log with all parameters")
        void publishLog_WithAllParameters_PublishesCorrectly() {
            Map<String, Object> details = Map.of("count", 42);

            webSocketService.publishLog(jobId, LogLevel.WARN, "parser", "Warning message", details);

            String expectedDestination = "/topic/jobs/" + jobId + "/logs";
            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertEquals("log", message.get("type"));
            assertEquals("WARN", message.get("level"));
            assertEquals("parser", message.get("step"));
            assertEquals("Warning message", message.get("message"));
            assertEquals(details, message.get("details"));
        }

        @Test
        @DisplayName("Should publish log with null details")
        void publishLog_WithNullDetails_PublishesCorrectly() {
            webSocketService.publishLog(jobId, LogLevel.DEBUG, "step", "Debug message", null);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());

            assertNull(messageCaptor.getValue().get("details"));
        }

        @Test
        @DisplayName("Should handle messaging exception gracefully")
        void publishLog_WhenMessagingFails_LogsErrorWithoutThrowing() {
            doThrow(new RuntimeException("Connection lost"))
                .when(messagingTemplate).convertAndSend(any(), any());

            assertDoesNotThrow(() ->
                webSocketService.publishLog(jobId, LogLevel.ERROR, "step", "Error", null)
            );
        }
    }

    @Nested
    @DisplayName("publishStatusUpdate Tests")
    class PublishStatusUpdateTests {

        @Test
        @DisplayName("Should publish status update with correct structure")
        void publishStatusUpdate_PublishesCorrectStructure() {
            webSocketService.publishStatusUpdate(jobId, "RUNNING", 50);

            String expectedDestination = "/topic/jobs/" + jobId + "/logs";
            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertEquals("status", message.get("type"));
            assertEquals("RUNNING", message.get("status"));
            assertEquals(50, message.get("progress"));
            assertNotNull(message.get("timestamp"));
        }

        @Test
        @DisplayName("Should handle null jobId gracefully")
        void publishStatusUpdate_WithNullJobId_DoesNotPublish() {
            webSocketService.publishStatusUpdate(null, "COMPLETED", 100);

            verify(messagingTemplate, never()).convertAndSend(any(), any());
        }

        @Test
        @DisplayName("Should handle zero progress")
        void publishStatusUpdate_WithZeroProgress_PublishesCorrectly() {
            webSocketService.publishStatusUpdate(jobId, "PENDING", 0);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());

            assertEquals(0, messageCaptor.getValue().get("progress"));
        }

        @Test
        @DisplayName("Should handle messaging exception gracefully")
        void publishStatusUpdate_WhenMessagingFails_LogsError() {
            doThrow(new RuntimeException("WebSocket error"))
                .when(messagingTemplate).convertAndSend(any(), any());

            assertDoesNotThrow(() -> webSocketService.publishStatusUpdate(jobId, "FAILED", 75));
        }
    }

    @Nested
    @DisplayName("publishCompletion Tests")
    class PublishCompletionTests {

        @Test
        @DisplayName("Should publish successful completion")
        void publishCompletion_Success_PublishesCorrectly() {
            webSocketService.publishCompletion(jobId, true, null);

            String expectedDestination = "/topic/jobs/" + jobId + "/logs";
            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertEquals("completion", message.get("type"));
            assertEquals(true, message.get("success"));
            assertNull(message.get("errorMessage"));
            assertNotNull(message.get("timestamp"));
        }

        @Test
        @DisplayName("Should publish failed completion with error message")
        void publishCompletion_Failure_PublishesWithError() {
            String errorMessage = "Pipeline execution failed";
            webSocketService.publishCompletion(jobId, false, errorMessage);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertEquals("completion", message.get("type"));
            assertEquals(false, message.get("success"));
            assertEquals(errorMessage, message.get("errorMessage"));
        }

        @Test
        @DisplayName("Should handle null jobId gracefully")
        void publishCompletion_WithNullJobId_DoesNotPublish() {
            webSocketService.publishCompletion(null, true, null);

            verify(messagingTemplate, never()).convertAndSend(any(), any());
        }

        @Test
        @DisplayName("Should handle null error message for failed job")
        void publishCompletion_FailureWithNullError_PublishesCorrectly() {
            webSocketService.publishCompletion(jobId, false, null);

            ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());

            assertEquals(false, messageCaptor.getValue().get("success"));
            assertNull(messageCaptor.getValue().get("errorMessage"));
        }

        @Test
        @DisplayName("Should handle messaging exception gracefully")
        void publishCompletion_WhenMessagingFails_LogsError() {
            doThrow(new RuntimeException("Connection closed"))
                .when(messagingTemplate).convertAndSend(any(), any());

            assertDoesNotThrow(() -> webSocketService.publishCompletion(jobId, true, null));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle all log levels")
        void publishLog_AllLevels_HandledCorrectly() {
            for (LogLevel level : LogLevel.values()) {
                reset(messagingTemplate);
                JobLogEntity logEntity = createLogEntity(level, "step", "Message");

                webSocketService.publishLog(jobId, logEntity);

                ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
                verify(messagingTemplate).convertAndSend(any(), messageCaptor.capture());
                assertEquals(level.name(), messageCaptor.getValue().get("level"));
            }
        }

        @Test
        @DisplayName("Should handle concurrent publishing")
        void publishLog_ConcurrentCalls_HandledCorrectly() {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    JobLogEntity logEntity = createLogEntity(LogLevel.INFO, "step", "Message " + index);
                    webSocketService.publishLog(jobId, logEntity);
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                assertDoesNotThrow(() -> thread.join(1000));
            }

            verify(messagingTemplate, times(threadCount)).convertAndSend(any(), any());
        }
    }

    private JobLogEntity createLogEntity(LogLevel level, String step, String message) {
        JobLogEntity entity = new JobLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setLevel(level);
        entity.setStep(step);
        entity.setMessage(message);
        entity.setTimestamp(Instant.now());
        return entity;
    }
}
