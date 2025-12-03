package io.rdfforge.triplestore.connector;

import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for triplestore providers.
 *
 * This component automatically discovers all TriplestoreProvider implementations
 * at startup and provides a unified API for accessing them.
 *
 * Adding a new provider is as simple as:
 * 1. Creating a new class implementing TriplestoreProvider
 * 2. Annotating it with @Component
 *
 * The registry will automatically find and register it.
 */
@Component
@Slf4j
public class TriplestoreProviderRegistry {

    private final List<TriplestoreProvider> providers;
    private final Map<String, TriplestoreProvider> providersByType = new ConcurrentHashMap<>();
    private final Map<TriplestoreConnectionEntity.TriplestoreType, TriplestoreProvider> providersByEntityType = new ConcurrentHashMap<>();

    public TriplestoreProviderRegistry(List<TriplestoreProvider> providers) {
        this.providers = providers != null ? providers : Collections.emptyList();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing TriplestoreProviderRegistry with {} providers", providers.size());

        for (TriplestoreProvider provider : providers) {
            String type = provider.getType().toUpperCase();
            providersByType.put(type, provider);

            // Map entity types to providers
            for (TriplestoreConnectionEntity.TriplestoreType entityType : TriplestoreConnectionEntity.TriplestoreType.values()) {
                if (provider.supports(entityType)) {
                    providersByEntityType.put(entityType, provider);
                    log.info("Registered provider '{}' for type '{}'", provider.getProviderInfo().displayName(), entityType);
                }
            }
        }

        log.info("TriplestoreProviderRegistry initialized. Available providers: {}",
            providersByType.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    /**
     * Get all available providers.
     * @return List of all registered providers
     */
    public List<TriplestoreProvider> getAllProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Get provider info for all available providers.
     * @return List of provider information
     */
    public List<TriplestoreProviderInfo> getAvailableProviders() {
        return providers.stream()
            .map(TriplestoreProvider::getProviderInfo)
            .collect(Collectors.toList());
    }

    /**
     * Get a provider by its type identifier.
     * @param type The provider type (e.g., "FUSEKI", "GRAPHDB")
     * @return Optional containing the provider if found
     */
    public Optional<TriplestoreProvider> getProvider(String type) {
        return Optional.ofNullable(providersByType.get(type.toUpperCase()));
    }

    /**
     * Get a provider for a specific entity type.
     * @param type The entity type from the database
     * @return Optional containing the provider if found
     */
    public Optional<TriplestoreProvider> getProvider(TriplestoreConnectionEntity.TriplestoreType type) {
        return Optional.ofNullable(providersByEntityType.get(type));
    }

    /**
     * Create a connector for the given connection configuration.
     * @param connection The connection configuration
     * @return A connector instance
     * @throws UnsupportedOperationException if no provider supports the connection type
     */
    public TriplestoreConnector createConnector(TriplestoreConnectionEntity connection) {
        return getProvider(connection.getType())
            .map(provider -> provider.createConnector(connection))
            .orElseThrow(() -> new UnsupportedOperationException(
                "No provider found for triplestore type: " + connection.getType() +
                ". Available types: " + providersByEntityType.keySet()
            ));
    }

    /**
     * Check if a provider exists for the given type.
     * @param type The provider type
     * @return true if a provider exists
     */
    public boolean hasProvider(String type) {
        return providersByType.containsKey(type.toUpperCase());
    }

    /**
     * Check if a provider exists for the given entity type.
     * @param type The entity type
     * @return true if a provider exists
     */
    public boolean hasProvider(TriplestoreConnectionEntity.TriplestoreType type) {
        return providersByEntityType.containsKey(type);
    }

    /**
     * Get provider info for a specific type.
     * @param type The provider type
     * @return Optional containing the provider info if found
     */
    public Optional<TriplestoreProviderInfo> getProviderInfo(String type) {
        return getProvider(type).map(TriplestoreProvider::getProviderInfo);
    }
}
