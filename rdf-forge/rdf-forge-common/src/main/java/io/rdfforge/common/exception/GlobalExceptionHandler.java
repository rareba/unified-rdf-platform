package io.rdfforge.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String ERROR_CODE_KEY = "errorCode";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String SERVICE_KEY = "service";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.warn("Resource not found: {} [traceId={}]", ex.getMessage(), traceId);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://rdf-forge.io/errors/resource-not-found"));
        enrichProblemDetail(problem, "RESOURCE_NOT_FOUND", traceId);
        
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
        enrichProblemDetail(problem, "PIPELINE_VALIDATION_ERROR", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(PipelineExecutionException.class)
    public ResponseEntity<ProblemDetail> handlePipelineExecution(
            PipelineExecutionException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("Pipeline execution failed: {} [traceId={}]", ex.getMessage(), traceId, ex);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Pipeline Execution Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/pipeline-execution"));
        enrichProblemDetail(problem, "PIPELINE_EXECUTION_ERROR", traceId);
        
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
        enrichProblemDetail(problem, "SHACL_VALIDATION_ERROR", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(TriplestoreConnectionException.class)
    public ResponseEntity<ProblemDetail> handleTriplestoreConnection(
            TriplestoreConnectionException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("Triplestore connection failed: {} [traceId={}]", ex.getMessage(), traceId, ex);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Triplestore Connection Failed");
        problem.setType(URI.create("https://rdf-forge.io/errors/triplestore-connection"));
        enrichProblemDetail(problem, "TRIPLESTORE_CONNECTION_ERROR", traceId);
        
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
        problem.setProperty("fieldErrors", fieldErrors);
        enrichProblemDetail(problem, "VALIDATION_ERROR", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(RdfForgeException.class)
    public ResponseEntity<ProblemDetail> handleRdfForgeException(
            RdfForgeException ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("RDF Forge error: {} [traceId={}, errorCode={}]", 
            ex.getMessage(), traceId, ex.getErrorCode(), ex);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("RDF Forge Error");
        problem.setType(URI.create("https://rdf-forge.io/errors/general"));
        enrichProblemDetail(problem, ex.getErrorCode(), traceId);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
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
        enrichProblemDetail(problem, "INVALID_ARGUMENT", traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {
        
        String traceId = getOrCreateTraceId();
        log.error("Unexpected error: {} [traceId={}]", ex.getMessage(), traceId, ex);
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            "An unexpected error occurred. Please contact support with trace ID: " + traceId);
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://rdf-forge.io/errors/internal"));
        enrichProblemDetail(problem, "INTERNAL_ERROR", traceId);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void enrichProblemDetail(ProblemDetail problem, String errorCode, String traceId) {
        problem.setProperty(TRACE_ID_KEY, traceId);
        problem.setProperty(ERROR_CODE_KEY, errorCode);
        problem.setProperty(TIMESTAMP_KEY, Instant.now().toString());
        
        String serviceName = System.getenv("SPRING_APPLICATION_NAME");
        if (serviceName != null) {
            problem.setProperty(SERVICE_KEY, serviceName);
        }
    }

    private String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }
}