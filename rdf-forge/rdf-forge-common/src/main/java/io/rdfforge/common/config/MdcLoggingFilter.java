package io.rdfforge.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds MDC context for structured logging across all requests.
 * This enables distributed tracing by propagating trace IDs through the system.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String REQUEST_ID = "requestId";
    private static final String USER_ID = "userId";
    private static final String REQUEST_PATH = "requestPath";
    private static final String REQUEST_METHOD = "requestMethod";
    private static final String CLIENT_IP = "clientIp";
    
    // Standard headers for trace propagation
    private static final String X_TRACE_ID_HEADER = "X-Trace-Id";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String X_CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        try {
            setupMdc(request, response);
            filterChain.doFilter(request, response);
        } finally {
            clearMdc();
        }
    }

    private void setupMdc(HttpServletRequest request, HttpServletResponse response) {
        // Generate or extract trace ID
        String traceId = extractOrGenerateTraceId(request);
        MDC.put(TRACE_ID, traceId);
        
        // Generate span ID for this specific request
        String spanId = generateShortId();
        MDC.put(SPAN_ID, spanId);
        
        // Request ID (same as trace for new requests)
        String requestId = extractHeader(request, X_REQUEST_ID_HEADER, traceId);
        MDC.put(REQUEST_ID, requestId);
        
        // Request context
        MDC.put(REQUEST_PATH, request.getRequestURI());
        MDC.put(REQUEST_METHOD, request.getMethod());
        
        // Client IP (considering proxies)
        String clientIp = extractClientIp(request);
        MDC.put(CLIENT_IP, clientIp);
        
        // User ID from authenticated principal (if available)
        if (request.getUserPrincipal() != null) {
            MDC.put(USER_ID, request.getUserPrincipal().getName());
        }
        
        // Add trace ID to response headers for client correlation
        response.addHeader(X_TRACE_ID_HEADER, traceId);
        response.addHeader(X_REQUEST_ID_HEADER, requestId);
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // Check for existing trace ID from upstream services
        String traceId = request.getHeader(X_TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        
        // Check correlation ID (alternative header)
        traceId = request.getHeader(X_CORRELATION_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        
        // Generate new trace ID
        return generateTraceId();
    }

    private String extractHeader(HttpServletRequest request, String headerName, String defaultValue) {
        String value = request.getHeader(headerName);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private String extractClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For for proxied requests
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Return first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void clearMdc() {
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
        MDC.remove(REQUEST_ID);
        MDC.remove(USER_ID);
        MDC.remove(REQUEST_PATH);
        MDC.remove(REQUEST_METHOD);
        MDC.remove(CLIENT_IP);
    }
}