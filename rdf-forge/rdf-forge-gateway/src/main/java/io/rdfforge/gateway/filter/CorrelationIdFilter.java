package io.rdfforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that manages correlation IDs for distributed tracing.
 * Generates a unique correlation ID per request and propagates it through
 * all microservices via HTTP headers.
 * 
 * This enables end-to-end request tracing across the entire system,
 * making it easier to debug and monitor distributed transactions.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    
    // Header constants
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    
    // Exchange attribute keys
    public static final String CORRELATION_ID_ATTR = "correlationId";
    public static final String START_TIME_ATTR = "startTime";
    
    // Order - should run early but after any security filters
    private static final int FILTER_ORDER = 0;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        // Generate or extract correlation ID
        String correlationId = extractOrGenerateCorrelationId(exchange);
        String traceId = extractOrGenerateTraceId(exchange, correlationId);
        String requestId = generateRequestId();
        
        // Store in exchange attributes for later use
        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);
        exchange.getAttributes().put(START_TIME_ATTR, startTime);
        
        // Mutate request to add correlation headers
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .header(TRACE_ID_HEADER, traceId)
            .header(REQUEST_ID_HEADER, requestId)
            .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build();
        
        // Log request start
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} - Request started", 
                correlationId,
                mutatedRequest.getMethod(),
                mutatedRequest.getPath());
        }
        
        // Add correlation ID to response headers
        ServerHttpResponse response = mutatedExchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().add(TRACE_ID_HEADER, traceId);
        response.getHeaders().add(REQUEST_ID_HEADER, requestId);
        
        return chain.filter(mutatedExchange)
            .doOnSuccess(aVoid -> logSuccess(mutatedExchange, correlationId, startTime))
            .doOnError(throwable -> logError(mutatedExchange, correlationId, startTime, throwable));
    }
    
    /**
     * Extract correlation ID from incoming request or generate a new one.
     */
    private String extractOrGenerateCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        
        if (correlationId != null && !correlationId.isBlank()) {
            // Validate format - should be alphanumeric and reasonable length
            if (isValidCorrelationId(correlationId)) {
                return correlationId;
            } else {
                log.warn("Invalid correlation ID format received: {}. Generating new ID.", correlationId);
            }
        }
        
        // Check for trace ID as fallback
        correlationId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (correlationId != null && !correlationId.isBlank() && isValidCorrelationId(correlationId)) {
            return correlationId;
        }
        
        // Generate new correlation ID
        return generateCorrelationId();
    }
    
    /**
     * Extract or generate trace ID.
     */
    private String extractOrGenerateTraceId(ServerWebExchange exchange, String defaultTraceId) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        
        if (traceId != null && !traceId.isBlank() && isValidCorrelationId(traceId)) {
            return traceId;
        }
        
        return defaultTraceId;
    }
    
    /**
     * Generate a unique correlation ID.
     */
    private String generateCorrelationId() {
        // UUID without dashes, first 32 characters
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }
    
    /**
     * Generate a unique request ID.
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase();
    }
    
    /**
     * Validate correlation ID format.
     */
    private boolean isValidCorrelationId(String correlationId) {
        // Should be alphanumeric, 16-64 characters
        return correlationId != null 
            && correlationId.matches("^[a-fA-F0-9]{16,64}$");
    }
    
    /**
     * Log successful request completion.
     */
    private void logSuccess(ServerWebExchange exchange, String correlationId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        Integer statusCode = exchange.getResponse().getStatusCode() != null 
            ? exchange.getResponse().getStatusCode().value() 
            : null;
        
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        
        // Log at appropriate level based on status code
        if (statusCode != null && statusCode >= 500) {
            log.error("[{}] {} {} - {} - {}ms", correlationId, method, path, statusCode, duration);
        } else if (statusCode != null && statusCode >= 400) {
            log.warn("[{}] {} {} - {} - {}ms", correlationId, method, path, statusCode, duration);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} - {} - {}ms", correlationId, method, path, statusCode, duration);
        } else {
            log.info("[{}] {} {} - {} - {}ms", correlationId, method, path, statusCode, duration);
        }
    }
    
    /**
     * Log request error.
     */
    private void logError(ServerWebExchange exchange, String correlationId, long startTime, Throwable throwable) {
        long duration = System.currentTimeMillis() - startTime;
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        
        log.error("[{}] {} {} - Error after {}ms: {}", 
            correlationId, method, path, duration, throwable.getMessage(), throwable);
    }
    
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
