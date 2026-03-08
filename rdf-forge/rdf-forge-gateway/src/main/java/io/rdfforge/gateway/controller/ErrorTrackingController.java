package io.rdfforge.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Controller for receiving and logging frontend errors.
 * Provides an endpoint for the frontend to report errors for centralized tracking.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/errors")
public class ErrorTrackingController {

    /**
     * Receive frontend error reports.
     * 
     * @param errorReport the error report from the frontend
     * @return empty response (fire-and-forget)
     */
    @PostMapping
    public ResponseEntity<Void> reportError(@RequestBody FrontendErrorReport errorReport) {
        // Set correlation ID from frontend if provided
        if (errorReport.correlationId() != null) {
            MDC.put("correlationId", errorReport.correlationId());
        }

        // Log the error with appropriate level based on severity
        logFrontendError(errorReport);

        // Clear MDC
        MDC.remove("correlationId");

        // Return 204 No Content - fire and forget
        return ResponseEntity.noContent().build();
    }

    /**
     * Receive batched frontend error reports.
     * 
     * @param batch the batch of error reports
     * @return empty response
     */
    @PostMapping("/batch")
    public ResponseEntity<Void> reportErrors(@RequestBody ErrorBatch batch) {
        if (batch.errors() == null || batch.errors().isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        for (FrontendErrorReport error : batch.errors()) {
            if (error.correlationId() != null) {
                MDC.put("correlationId", error.correlationId());
            }

            logFrontendError(error);

            MDC.remove("correlationId");
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Log a frontend error with appropriate severity.
     */
    private void logFrontendError(FrontendErrorReport error) {
        String message = String.format("[Frontend Error] %s | Context: %s | URL: %s | User: %s | Category: %s",
            error.message(),
            error.context() != null ? error.context() : "N/A",
            error.url(),
            error.userId() != null ? error.userId() : "anonymous",
            error.category()
        );

        switch (error.severity()) {
            case "critical":
                log.error(message, new FrontendErrorDetails(error));
                break;
            case "high":
                log.error(message);
                break;
            case "medium":
                log.warn(message);
                break;
            case "low":
            default:
                log.info(message);
                break;
        }
    }

    /**
     * Record for frontend error report.
     */
    public record FrontendErrorReport(
        String message,
        String stack,
        String context,
        String url,
        String userAgent,
        String timestamp,
        String userId,
        String correlationId,
        String severity,
        String category,
        Map<String, Object> metadata
    ) {}

    /**
     * Record for batched error reports.
     */
    public record ErrorBatch(List<FrontendErrorReport> errors) {}

    /**
     * Exception wrapper for including stack trace in logs.
     */
    private static class FrontendErrorDetails extends Exception {
        FrontendErrorDetails(FrontendErrorReport error) {
            super(String.format("Frontend error: %s\nStack: %s\nMetadata: %s",
                error.message(),
                error.stack() != null ? error.stack() : "N/A",
                error.metadata() != null ? error.metadata() : "N/A"
            ));
        }
    }
}
