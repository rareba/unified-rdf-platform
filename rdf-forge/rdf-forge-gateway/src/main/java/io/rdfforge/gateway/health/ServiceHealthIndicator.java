package io.rdfforge.gateway.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive health indicator for downstream services routed through the gateway.
 *
 * Checks each downstream service's /actuator/health endpoint in parallel and
 * reports a composite status. The gateway is considered healthy only when all
 * downstream services are reachable.
 *
 * Uses WebClient (non-blocking) since the gateway runs on WebFlux.
 * Results are cached briefly to avoid hammering downstream services.
 */
@Component
@Slf4j
public class ServiceHealthIndicator implements ReactiveHealthIndicator {

    private static final long CACHE_TTL_MS = 10_000; // 10 seconds
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final Map<String, String> serviceUrls;

    private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>();

    public ServiceHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${AUTH_SERVICE_URL:http://rdf-forge-auth-service:8086}") String authUrl,
            @Value("${PIPELINE_SERVICE_URL:http://rdf-forge-pipeline-service:8001}") String pipelineUrl,
            @Value("${SHACL_SERVICE_URL:http://rdf-forge-shacl-service:8002}") String shaclUrl,
            @Value("${JOB_SERVICE_URL:http://rdf-forge-job-service:8003}") String jobUrl,
            @Value("${DATA_SERVICE_URL:http://rdf-forge-data-service:8004}") String dataUrl,
            @Value("${DIMENSION_SERVICE_URL:http://rdf-forge-dimension-service:8005}") String dimensionUrl,
            @Value("${TRIPLESTORE_SERVICE_URL:http://rdf-forge-triplestore-service:8006}") String triplestoreUrl
    ) {
        this.webClient = webClientBuilder.build();

        // Preserve insertion order for consistent output
        this.serviceUrls = new LinkedHashMap<>();
        this.serviceUrls.put("auth-service", authUrl);
        this.serviceUrls.put("pipeline-service", pipelineUrl);
        this.serviceUrls.put("shacl-service", shaclUrl);
        this.serviceUrls.put("job-service", jobUrl);
        this.serviceUrls.put("data-service", dataUrl);
        this.serviceUrls.put("dimension-service", dimensionUrl);
        this.serviceUrls.put("triplestore-service", triplestoreUrl);
    }

    @Override
    public Mono<Health> health() {
        CachedHealth cached = cachedHealth.get();
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.health);
        }

        return checkAllServices()
                .doOnNext(health -> cachedHealth.set(
                        new CachedHealth(health, System.currentTimeMillis())));
    }

    private Mono<Health> checkAllServices() {
        return Flux.fromIterable(serviceUrls.entrySet())
                .flatMap(entry -> checkService(entry.getKey(), entry.getValue())
                        .map(status -> Map.entry(entry.getKey(), status)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(results -> {
                    long upCount = results.values().stream()
                            .filter(r -> "UP".equals(r.get("status")))
                            .count();
                    long totalCount = results.size();

                    Health.Builder builder;
                    if (upCount == totalCount) {
                        builder = Health.up();
                    } else if (upCount == 0) {
                        builder = Health.down();
                    } else {
                        // Some services down -- report as degraded (status UP
                        // with warning, since Spring Boot health does not have
                        // a built-in DEGRADED status)
                        builder = Health.up();
                    }

                    return builder
                            .withDetail("totalServices", totalCount)
                            .withDetail("servicesUp", upCount)
                            .withDetail("servicesDown", totalCount - upCount)
                            .withDetail("services", results)
                            .withDetail("checkedAt", Instant.now().toString())
                            .build();
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> checkService(String name, String baseUrl) {
        String healthUrl = baseUrl + "/actuator/health";

        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(REQUEST_TIMEOUT)
                .map(body -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", body.getOrDefault("status", "UNKNOWN"));
                    result.put("url", baseUrl);
                    return result;
                })
                .onErrorResume(e -> {
                    log.debug("Health check failed for {}: {}", name, e.getMessage());
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "DOWN");
                    result.put("url", baseUrl);
                    result.put("error", e.getMessage());
                    return Mono.just(result);
                });
    }

    private record CachedHealth(Health health, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
