package io.rdfforge.pipeline.destination;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for destination providers.
 *
 * This component automatically discovers all DestinationProvider implementations
 * at startup and provides a unified API for accessing them.
 *
 * Adding a new destination is as simple as:
 * 1. Creating a new class implementing DestinationProvider
 * 2. Annotating it with @Component
 *
 * The registry will automatically find and register it.
 */
@Component
@Slf4j
public class DestinationRegistry {

    private final List<DestinationProvider> providers;
    private final Map<String, DestinationProvider> providersByType = new ConcurrentHashMap<>();
    private final Map<String, List<DestinationProvider>> providersByCategory = new ConcurrentHashMap<>();

    public DestinationRegistry(List<DestinationProvider> providers) {
        this.providers = providers != null ? providers : Collections.emptyList();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing DestinationRegistry with {} providers", providers.size());

        for (DestinationProvider provider : providers) {
            DestinationInfo info = provider.getDestinationInfo();
            String type = info.type();

            providersByType.put(type, provider);

            // Group by category
            String category = info.category();
            providersByCategory
                .computeIfAbsent(category, k -> new ArrayList<>())
                .add(provider);

            log.info("Registered destination provider '{}' (type: {}, category: {})",
                info.displayName(), type, category);
        }

        log.info("DestinationRegistry initialized. Available destinations: {}",
            providersByType.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    /**
     * Get all available providers.
     * @return List of all registered providers
     */
    public List<DestinationProvider> getAllProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Get destination info for all available providers.
     * @return List of destination information
     */
    public List<DestinationInfo> getAvailableDestinations() {
        return providers.stream()
            .map(DestinationProvider::getDestinationInfo)
            .collect(Collectors.toList());
    }

    /**
     * Get destinations grouped by category.
     * @return Map of category to destination infos
     */
    public Map<String, List<DestinationInfo>> getDestinationsByCategory() {
        Map<String, List<DestinationInfo>> result = new HashMap<>();
        for (Map.Entry<String, List<DestinationProvider>> entry : providersByCategory.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                .map(DestinationProvider::getDestinationInfo)
                .collect(Collectors.toList()));
        }
        return result;
    }

    /**
     * Get a provider by its type identifier.
     * @param type The destination type (e.g., "triplestore", "file")
     * @return Optional containing the provider if found
     */
    public Optional<DestinationProvider> getProvider(String type) {
        return Optional.ofNullable(providersByType.get(type));
    }

    /**
     * Get providers for a specific category.
     * @param category The category (e.g., "triplestore", "cloud-storage")
     * @return List of providers in the category
     */
    public List<DestinationProvider> getProvidersByCategory(String category) {
        return providersByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Check if a provider exists for the given type.
     * @param type The destination type
     * @return true if a provider exists
     */
    public boolean hasProvider(String type) {
        return providersByType.containsKey(type);
    }

    /**
     * Get destination info for a specific type.
     * @param type The destination type
     * @return Optional containing the destination info if found
     */
    public Optional<DestinationInfo> getDestinationInfo(String type) {
        return getProvider(type).map(DestinationProvider::getDestinationInfo);
    }

    /**
     * Get all supported formats across all providers.
     * @return Set of all supported RDF formats
     */
    public Set<String> getAllSupportedFormats() {
        return providers.stream()
            .flatMap(p -> p.getDestinationInfo().supportedFormats().stream())
            .collect(Collectors.toSet());
    }

    /**
     * Get providers that support a specific format.
     * @param format The RDF format (e.g., "turtle", "json-ld")
     * @return List of providers supporting the format
     */
    public List<DestinationProvider> getProvidersSupportingFormat(String format) {
        return providers.stream()
            .filter(p -> p.getDestinationInfo().supportedFormats().contains(format.toLowerCase()))
            .collect(Collectors.toList());
    }
}
