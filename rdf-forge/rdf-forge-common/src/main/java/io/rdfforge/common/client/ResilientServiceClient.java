package io.rdfforge.common.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resilient HTTP client for inter-service communication.
 * Provides circuit breaker and retry patterns for fault tolerance.
 */
@Slf4j
@Component
public class ResilientServiceClient {

    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    public ResilientServiceClient(
            RestTemplate restTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    /**
     * Execute a GET request with resilience patterns.
     */
    public <T> T get(String serviceName, String url, Class<T> responseType) {
        return executeWithResilience(serviceName, () -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
            return response.getBody();
        });
    }

    /**
     * Execute a POST request with resilience patterns.
     */
    public <T, R> T post(String serviceName, String url, R body, Class<T> responseType) {
        return executeWithResilience(serviceName, () -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<R> entity = new HttpEntity<>(body, headers);
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
            return response.getBody();
        });
    }

    /**
     * Execute a PUT request with resilience patterns.
     */
    public <T, R> T put(String serviceName, String url, R body, Class<T> responseType) {
        return executeWithResilience(serviceName, () -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<R> entity = new HttpEntity<>(body, headers);
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);
            return response.getBody();
        });
    }

    /**
     * Execute a DELETE request with resilience patterns.
     */
    public void delete(String serviceName, String url) {
        executeWithResilience(serviceName, () -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return null;
        });
    }

    /**
     * Execute any operation with circuit breaker and retry.
     */
    public <T> T executeWithResilience(String serviceName, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        Retry retry = getOrCreateRetry(serviceName);

        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
            Retry.decorateSupplier(retry, operation));

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.error("Service call to {} failed after retries. Circuit breaker state: {}", 
                serviceName, circuitBreaker.getState(), e);
            throw e;
        }
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> circuitBreakerRegistry.circuitBreaker(name));
    }

    private Retry getOrCreateRetry(String serviceName) {
        return retries.computeIfAbsent(serviceName, 
            name -> retryRegistry.retry(name));
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        
        // Propagate trace context
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        
        String spanId = MDC.get("spanId");
        if (spanId != null) {
            headers.set("X-Span-Id", spanId);
        }
        
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            headers.set("X-Request-Id", requestId);
        }
        
        return headers;
    }

    /**
     * Get the current state of a circuit breaker.
     */
    public CircuitBreaker.State getCircuitBreakerState(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        return cb != null ? cb.getState() : null;
    }

    /**
     * Get metrics for a circuit breaker.
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        return cb != null ? cb.getMetrics() : null;
    }
}