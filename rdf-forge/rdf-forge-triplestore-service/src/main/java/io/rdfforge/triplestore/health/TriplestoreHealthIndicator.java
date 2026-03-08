package io.rdfforge.triplestore.health;

import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.HealthStatus;
import io.rdfforge.triplestore.repository.TriplestoreConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for triplestore connections.
 *
 * Reports the health status of all configured triplestore connections.
 * The overall health is UP if at least one connection is healthy or if
 * no connections are configured. It reports DOWN only when all configured
 * connections are unhealthy.
 *
 * Results are cached briefly to prevent hammering the database on repeated
 * health check requests.
 */
@Component
@Slf4j
public class TriplestoreHealthIndicator implements HealthIndicator {

    private static final long CACHE_TTL_MS = 10_000; // 10 seconds

    private final TriplestoreConnectionRepository connectionRepository;

    private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>();

    public TriplestoreHealthIndicator(TriplestoreConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
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

    private Health checkHealth() {
        try {
            List<TriplestoreConnectionEntity> connections = connectionRepository.findAll();

            if (connections.isEmpty()) {
                return Health.up()
                        .withDetail("connections", 0)
                        .withDetail("message", "No triplestore connections configured")
                        .withDetail("checkedAt", Instant.now().toString())
                        .build();
            }

            int healthy = 0;
            int unhealthy = 0;
            int unknown = 0;
            Map<String, Map<String, Object>> connectionDetails = new LinkedHashMap<>();

            for (TriplestoreConnectionEntity conn : connections) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("type", conn.getType().name());
                detail.put("url", conn.getUrl());
                detail.put("healthStatus", conn.getHealthStatus().name());

                if (conn.getLastHealthCheck() != null) {
                    detail.put("lastHealthCheck", conn.getLastHealthCheck().toString());
                }

                if (Boolean.TRUE.equals(conn.getIsDefault())) {
                    detail.put("isDefault", true);
                }

                connectionDetails.put(conn.getName(), detail);

                switch (conn.getHealthStatus()) {
                    case HEALTHY -> healthy++;
                    case UNHEALTHY -> unhealthy++;
                    case UNKNOWN -> unknown++;
                }
            }

            Health.Builder builder;
            if (unhealthy == connections.size()) {
                // All connections are unhealthy
                builder = Health.down();
            } else if (unhealthy > 0) {
                // Some connections are unhealthy
                builder = Health.up();
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("connections", connections.size())
                    .withDetail("healthy", healthy)
                    .withDetail("unhealthy", unhealthy)
                    .withDetail("unknown", unknown)
                    .withDetail("details", connectionDetails)
                    .withDetail("checkedAt", Instant.now().toString())
                    .build();

        } catch (Exception e) {
            log.warn("Triplestore health check failed: {}", e.getMessage());
            return Health.down()
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
