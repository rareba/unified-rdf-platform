package io.rdfforge.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for all RDF Forge microservices.
 * Provides consistent error responses following RFC 7807 (Problem Details for HTTP APIs).
 * 
 * Key features:
 * - Structured error responses with error codes
 * - Full stack trace logging for 500 errors
 * - Correlation ID propagation
 * - User-friendly error messages
 * - Sensitive data masking in logs
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String ERROR_CODE_KEY = "errorCode";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String SERVICE_KEY = "service";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String PATH_KEY = "path";
    private static final String INSTANCE_KEY = "instance";

    @Value("${rdf-forge.errors.rate-limit.retry-after-seconds:60}")
    private int rateLimitRetryAfterSeconds;
    
    // Error code constants
    public static final String ERROR_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ERROR_PIPELINE_VALIDATION = "PIPELINE_VALIDATION_ERROR";
    public static final String ERROR_PIPELINE_EXECUTION = "PIPELINE_EXECUTION_ERROR";
    public static final String ERROR_SHACL_VALIDATION = "SHACL_VALIDATION_ERROR";
    public static final String ERROR_TRIPLESTORE_CONNECTION = "TRIPLESTORE_CONNECTION_ERROR";
    public static final String ERROR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_RDF_FORGE = "RDF_FORGE_ERROR";
    public static final String ERROR_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_CONFLICT = "CONFLICT";
    public static final String ERROR_RATE_LIMITED = "RATE_LIMITED";
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Resource not found: {} [traceId={}, resourceType={}, resourceId={}]", 
            ex.getMessage(), traceId, ex.getResourceType(), ex.getResourceId());
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://rdf-forge.io/errors/resource-not-found"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_RESOURCE_NOT_FOUND, traceId, request);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(PipelineValidationException.class)
    public ResponseEntity<ProblemDetail> handlePipelineValidation(
            PipelineValidationException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Pipeline validation failed: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Pipeline Validation Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/pipeline-validation"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_PIPELINE_VALIDATION, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(PipelineExecutionException.class)
    public ResponseEntity<ProblemDetail> handlePipelineExecution(
            PipelineExecutionException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        // Log full stack trace for server errors
        log.error("Pipeline execution failed: {} [traceId={}]", ex.getMessage(), traceId, ex);
        
        String userMessage = "Pipeline execution failed. Please check the pipeline configuration and try again. " +
            "If the problem persists, contact support with trace ID: " + traceId;
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, userMessage);
        problem.setTitle("Pipeline Execution Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/pipeline-execution"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_PIPELINE_EXECUTION, traceId, request);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @ExceptionHandler(ShaclValidationException.class)
    public ResponseEntity<ProblemDetail> handleShaclValidation(
            ShaclValidationException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("SHACL validation failed: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("SHACL Validation Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/shacl-validation"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_SHACL_VALIDATION, traceId, request);
        
        // Add validation details if available
        if (ex.getValidationReport() != null) {
            problem.setProperty("validationReport", ex.getValidationReport());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(TriplestoreConnectionException.class)
    public ResponseEntity<ProblemDetail> handleTriplestoreConnection(
            TriplestoreConnectionException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("Triplestore connection failed: {} [traceId={}, triplestore={}]", 
            ex.getMessage(), traceId, maskSensitiveData(ex.getConnectionInfo()), ex);
        
        String userMessage = "Unable to connect to the triplestore. Please verify the connection settings and try again.";
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, userMessage);
        problem.setTitle("Triplestore Connection Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/triplestore-connection"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_TRIPLESTORE_CONNECTION, traceId, request);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        
        log.warn("Validation failed: {} [traceId={}]", fieldErrors, traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://rdf-forge.io/errors/validation"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problem.setProperty("fieldErrors", fieldErrors);
        enrichProblemDetail(problem, ERROR_VALIDATION, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(RdfForgeException.class)
    public ResponseEntity<ProblemDetail> handleRdfForgeException(
            RdfForgeException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        // Log full stack trace for application exceptions
        log.error("RDF Forge error: {} [traceId={}, errorCode={}]", 
            ex.getMessage(), traceId, ex.getErrorCode(), ex);
        
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("RDF Forge Error");
        problem.setType(URI.create("https://rdf-forge.io/errors/general"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ex.getErrorCode() != null ? ex.getErrorCode() : ERROR_RDF_FORGE, traceId, request);
        
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Invalid argument: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Argument");
        problem.setType(URI.create("https://rdf-forge.io/errors/invalid-argument"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_INVALID_ARGUMENT, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Access denied: {} [traceId={}, user={}]", 
            ex.getMessage(), traceId, MDC.get("userId"));
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://rdf-forge.io/errors/access-denied"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_FORBIDDEN, traceId, request);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationRequired(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ex, 
            WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Authentication required: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
        problem.setTitle("Authentication Required");
        problem.setType(URI.create("https://rdf-forge.io/errors/authentication-required"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_UNAUTHORIZED, traceId, request);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(io.github.resilience4j.ratelimiter.RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Rate limit exceeded: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please slow down and try again later.");
        problem.setTitle("Rate Limit Exceeded");
        problem.setType(URI.create("https://rdf-forge.io/errors/rate-limited"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_RATE_LIMITED, traceId, request);
        problem.setProperty("retryAfter", rateLimitRetryAfterSeconds);
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public ResponseEntity<ProblemDetail> handleTimeout(
            java.util.concurrent.TimeoutException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("Request timeout: {} [traceId={}]", ex.getMessage(), traceId, ex);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "The request timed out. Please try again later.");
        problem.setTitle("Request Timeout");
        problem.setType(URI.create("https://rdf-forge.io/errors/timeout"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_SERVICE_UNAVAILABLE, traceId, request);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        // Log full stack trace for unexpected errors
        log.error("Unexpected error: {} [traceId={}, class={}]", 
            ex.getMessage(), traceId, ex.getClass().getSimpleName(), ex);
        
        String userMessage = "An unexpected error occurred. Please contact support with trace ID: " + traceId;
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, userMessage);
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://rdf-forge.io/errors/internal"));
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        enrichProblemDetail(problem, ERROR_INTERNAL, traceId, request);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /**
     * Enrich the ProblemDetail with standard properties.
     */
    private void enrichProblemDetail(ProblemDetail problem, String errorCode, String traceId, WebRequest request) {
        problem.setProperty(TRACE_ID_KEY, traceId);
        problem.setProperty(ERROR_CODE_KEY, errorCode);
        problem.setProperty(TIMESTAMP_KEY, Instant.now().toString());
        problem.setProperty(CORRELATION_ID_KEY, MDC.get("correlationId"));
        
        String serviceName = getServiceName();
        if (serviceName != null) {
            problem.setProperty(SERVICE_KEY, serviceName);
        }
        
        String path = request.getDescription(false).replace("uri=", "");
        problem.setProperty(PATH_KEY, path);
    }

    /**
     * Get or create a trace ID for request correlation.
     */
    private String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = MDC.get("correlationId");
        }
        if (traceId == null) {
            traceId = generateShortId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * Generate a short unique ID.
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Get the service name from environment or system properties.
     */
    private String getServiceName() {
        String serviceName = System.getenv("SPRING_APPLICATION_NAME");
        if (serviceName == null) {
            serviceName = System.getProperty("spring.application.name");
        }
        return serviceName;
    }

    /**
     * Mask sensitive data in log messages.
     */
    private String maskSensitiveData(String data) {
        if (data == null) {
            return null;
        }
        // Simple masking - hide most of the string
        if (data.length() > 8) {
            return data.substring(0, 4) + "****" + data.substring(data.length() - 4);
        }
        return "****";
    }
}
