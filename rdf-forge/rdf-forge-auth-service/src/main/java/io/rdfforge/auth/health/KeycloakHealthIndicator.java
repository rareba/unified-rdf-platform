package io.rdfforge.auth.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for Keycloak connectivity.
 *
 * Checks Keycloak availability by calling the OpenID Connect well-known
 * discovery endpoint for the configured realm. This endpoint does not
 * require authentication and is a reliable indicator of Keycloak readiness.
 *
 * Results are cached briefly to avoid excessive HTTP calls on every
 * health check request.
 */
@Component
@Slf4j
public class KeycloakHealthIndicator implements HealthIndicator {

    private static final long CACHE_TTL_MS = 10_000; // 10 seconds
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    @Value("${keycloak.admin.url:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${keycloak.admin.realm:rdfforge}")
    private String realm;

    private final RestTemplate restTemplate;

    private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>();

    public KeycloakHealthIndicator() {
        // Use a dedicated RestTemplate with a short timeout to prevent
        // slow Keycloak responses from blocking the health endpoint.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(CONNECT_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public Health health() {
        CachedHealth cached = cachedHealth.get();
        if (cached != null && !cached.isExpired()) {
            return cached.health;
        }

        Health health = checkHealth();
        cachedHealth.set(new CachedHealth(health, System.currentTimeMillis()));
        return health;
    }

    @SuppressWarnings("unchecked")
    private Health checkHealth() {
        String wellKnownUrl = String.format(
                "%s/realms/%s/.well-known/openid-configuration",
                keycloakUrl, realm);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(wellKnownUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String issuer = (String) body.getOrDefault("issuer", "unknown");

                return Health.up()
                        .withDetail("url", keycloakUrl)
                        .withDetail("realm", realm)
                        .withDetail("issuer", issuer)
                        .withDetail("checkedAt", Instant.now().toString())
                        .build();
            }

            return Health.down()
                    .withDetail("url", keycloakUrl)
                    .withDetail("realm", realm)
                    .withDetail("httpStatus", response.getStatusCode().value())
                    .withDetail("checkedAt", Instant.now().toString())
                    .build();

        } catch (Exception e) {
            log.warn("Keycloak health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", keycloakUrl)
                    .withDetail("realm", realm)
                    .withDetail("error", e.getMessage())
                    .withDetail("checkedAt", Instant.now().toString())
                    .build();
        }
    }

    private record CachedHealth(Health health, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
