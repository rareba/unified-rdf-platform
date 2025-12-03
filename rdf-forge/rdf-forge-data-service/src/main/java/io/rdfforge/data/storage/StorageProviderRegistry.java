package io.rdfforge.data.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for storage providers.
 *
 * This component automatically discovers all StorageProvider implementations
 * at startup and provides a unified API for accessing them.
 *
 * Adding a new storage provider is as simple as:
 * 1. Creating a new class implementing StorageProvider
 * 2. Annotating it with @Component
 *
 * The registry will automatically find and register it.
 *
 * The active provider is determined by the storage.provider configuration property.
 */
@Component
@Slf4j
public class StorageProviderRegistry {

    private final List<StorageProvider> providers;
    private final Map<String, StorageProvider> providersByType = new ConcurrentHashMap<>();

    @Value("${storage.provider:minio}")
    private String activeProviderType;

    private StorageProvider activeProvider;

    public StorageProviderRegistry(List<StorageProvider> providers) {
        this.providers = providers != null ? providers : Collections.emptyList();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing StorageProviderRegistry with {} providers", providers.size());

        for (StorageProvider provider : providers) {
            StorageProviderInfo info = provider.getProviderInfo();
            String type = info.type().toLowerCase();

            providersByType.put(type, provider);

            log.info("Registered storage provider '{}' ({})",
                info.displayName(), info.type());
        }

        // Set active provider
        activeProvider = providersByType.get(activeProviderType.toLowerCase());
        if (activeProvider == null) {
            log.warn("Configured storage provider '{}' not found. Available: {}",
                activeProviderType, providersByType.keySet());

            // Fall back to first available provider
            if (!providers.isEmpty()) {
                activeProvider = providers.get(0);
                log.info("Using fallback storage provider: {}", activeProvider.getType());
            }
        } else {
            log.info("Active storage provider: {} ({})",
                activeProvider.getProviderInfo().displayName(),
                activeProvider.getType());
        }

        log.info("StorageProviderRegistry initialized. Available providers: {}",
            providersByType.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    /**
     * Get all available storage providers.
     * @return List of all registered providers
     */
    public List<StorageProvider> getAllProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Get info for all available providers.
     * @return List of provider information
     */
    public List<StorageProviderInfo> getAvailableProviders() {
        return providers.stream()
            .map(StorageProvider::getProviderInfo)
            .collect(Collectors.toList());
    }

    /**
     * Get a provider by type.
     * @param type The provider type identifier
     * @return Optional containing the provider if found
     */
    public Optional<StorageProvider> getProvider(String type) {
        return Optional.ofNullable(providersByType.get(type.toLowerCase()));
    }

    /**
     * Get the currently active storage provider.
     * @return The active provider, or null if none configured
     */
    public StorageProvider getActiveProvider() {
        return activeProvider;
    }

    /**
     * Get the type of the active provider.
     * @return The active provider type
     */
    public String getActiveProviderType() {
        return activeProviderType;
    }

    /**
     * Set the active storage provider by type.
     * @param type The provider type to activate
     * @throws IllegalArgumentException if the provider type is not found
     */
    public void setActiveProvider(String type) {
        StorageProvider provider = providersByType.get(type.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider not found: " + type);
        }
        this.activeProviderType = type;
        this.activeProvider = provider;
        log.info("Switched active storage provider to: {}", type);
    }

    /**
     * Check if a provider type is supported.
     * @param type The provider type
     * @return true if the provider is available
     */
    public boolean isSupported(String type) {
        return providersByType.containsKey(type.toLowerCase());
    }

    /**
     * Get only enabled providers.
     * @return List of enabled providers
     */
    public List<StorageProvider> getEnabledProviders() {
        return providers.stream()
            .filter(StorageProvider::isEnabled)
            .collect(Collectors.toList());
    }

    /**
     * Initialize a provider with configuration.
     * @param type The provider type
     * @param config The configuration map
     * @throws IOException if initialization fails
     */
    public void initializeProvider(String type, Map<String, Object> config) throws IOException {
        StorageProvider provider = providersByType.get(type.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider not found: " + type);
        }
        provider.initialize(config);
    }
}
