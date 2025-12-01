package io.rdfforge.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for circuit breakers, retries, and time limiters.
 * Provides robust fault tolerance for inter-service communication.
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Creates a Circuit Breaker Registry with default configuration.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            // Number of calls to evaluate failure rate
            .slidingWindowSize(10)
            // Failure rate threshold to open the circuit
            .failureRateThreshold(50)
            // Wait duration before transitioning from OPEN to HALF_OPEN
            .waitDurationInOpenState(Duration.ofSeconds(30))
            // Calls allowed in HALF_OPEN state
            .permittedNumberOfCallsInHalfOpenState(5)
            // Slow call duration threshold
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            // Slow call rate threshold
            .slowCallRateThreshold(50)
            // Enable automatic transition from OPEN to HALF_OPEN
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // Record specific exceptions as failures
            .recordExceptions(
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            // Ignore specific exceptions
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    /**
     * Creates a Retry Registry with default configuration.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
            // Maximum number of retry attempts
            .maxAttempts(3)
            // Wait duration between retries (exponential backoff)
            .waitDuration(Duration.ofMillis(500))
            // Retry on specific exceptions
            .retryExceptions(
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class
            )
            // Don't retry on specific exceptions
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            // Exponential backoff multiplier
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(500, 2))
            .build();

        return RetryRegistry.of(defaultConfig);
    }

    /**
     * Creates a Time Limiter Registry with default configuration.
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
            // Timeout duration for operations
            .timeoutDuration(Duration.ofSeconds(30))
            // Cancel running futures on timeout
            .cancelRunningFuture(true)
            .build();

        return TimeLimiterRegistry.of(defaultConfig);
    }

    /**
     * Circuit breaker for Pipeline Service calls.
     */
    @Bean
    public CircuitBreaker pipelineServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("pipelineService",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build()
        );
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Pipeline Service Circuit Breaker state: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for SHACL Service calls.
     */
    @Bean
    public CircuitBreaker shaclServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("shaclService",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build()
        );
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("SHACL Service Circuit Breaker state: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for Job Service calls.
     */
    @Bean
    public CircuitBreaker jobServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("jobService",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build()
        );
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Job Service Circuit Breaker state: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for Triplestore operations.
     */
    @Bean
    public CircuitBreaker triplestoreCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("triplestore",
            CircuitBreakerConfig.custom()
                // Triplestore operations may take longer
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .slowCallRateThreshold(70)
                .slidingWindowSize(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build()
        );
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Triplestore Circuit Breaker state: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Retry configuration for file storage operations.
     */
    @Bean
    public Retry fileStorageRetry(RetryRegistry registry) {
        return registry.retry("fileStorage",
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .retryExceptions(java.io.IOException.class)
                .build()
        );
    }

    /**
     * Time limiter for long-running operations.
     */
    @Bean
    public TimeLimiter longRunningOperationTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter("longRunning",
            TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMinutes(5))
                .cancelRunningFuture(true)
                .build()
        );
    }
}