package io.rdfforge.data.health;

import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for the configured storage provider (MinIO or other).
 *
 * Checks connectivity by attempting to list objects in the storage backend.
 * Results are cached for a short period to avoid excessive calls to the
 * storage service on every health check request.
 */
@Component
@Slf4j
public class MinioHealthIndicator implements HealthIndicator {

    private static final long CACHE_TTL_MS = 10_000; // 10 seconds

    private final StorageProviderRegistry providerRegistry;

    private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>();

    public MinioHealthIndicator(StorageProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
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
            StorageProvider provider = providerRegistry.getActiveProvider();
            if (provider == null) {
                return Health.down()
                        .withDetail("reason", "No storage provider configured")
                        .build();
            }

            StorageProviderInfo info = provider.getProviderInfo();
            String providerType = provider.getType();

            if (!provider.isEnabled()) {
                return Health.down()
                        .withDetail("provider", providerType)
                        .withDetail("reason", "Storage provider is not enabled")
                        .build();
            }

            // Attempt a lightweight operation to verify connectivity.
            // Listing with maxKeys=1 is the least intrusive check.
            provider.list("", 1);

            Map<String, Object> config = provider.getConfiguration();

            Health.Builder builder = Health.up()
                    .withDetail("provider", providerType)
                    .withDetail("displayName", info.displayName())
                    .withDetail("checkedAt", Instant.now().toString());

            // Include non-sensitive config details
            if (config != null) {
                if (config.containsKey("endpoint")) {
                    builder.withDetail("endpoint", config.get("endpoint"));
                }
                if (config.containsKey("bucket")) {
                    builder.withDetail("bucket", config.get("bucket"));
                }
                if (config.containsKey("bucketName")) {
                    builder.withDetail("bucket", config.get("bucketName"));
                }
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("Storage health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("provider", providerRegistry.getActiveProviderType())
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
