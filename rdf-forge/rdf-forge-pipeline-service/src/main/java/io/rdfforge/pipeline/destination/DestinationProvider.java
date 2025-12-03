package io.rdfforge.pipeline.destination;

import org.apache.jena.rdf.model.Model;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for RDF destination providers.
 *
 * Each provider implementation must be a Spring component and will be
 * automatically discovered by the DestinationRegistry.
 *
 * To add a new destination:
 * 1. Create a class implementing this interface
 * 2. Annotate it with @Component
 * 3. Implement getDestinationInfo() with destination metadata
 * 4. Implement the publish methods
 *
 * The provider will be automatically registered and available via the API.
 */
public interface DestinationProvider {

    /**
     * Get metadata about this destination.
     * @return Destination information including name, config fields, and capabilities
     */
    DestinationInfo getDestinationInfo();

    /**
     * Get the destination type identifier.
     * @return The type identifier (e.g., "triplestore", "file", "s3")
     */
    default String getType() {
        return getDestinationInfo().type();
    }

    /**
     * Publish an RDF model to the destination.
     * @param model The RDF model to publish
     * @param config Configuration for the destination
     * @return Result of the publish operation
     * @throws IOException if publishing fails
     */
    PublishResult publish(Model model, Map<String, Object> config) throws IOException;

    /**
     * Publish an RDF model to a specific graph.
     * @param model The RDF model to publish
     * @param graphUri The target graph URI (null for default graph)
     * @param config Configuration for the destination
     * @return Result of the publish operation
     * @throws IOException if publishing fails
     */
    default PublishResult publishToGraph(Model model, String graphUri, Map<String, Object> config) throws IOException {
        Map<String, Object> configWithGraph = new java.util.HashMap<>(config);
        configWithGraph.put("graph", graphUri);
        return publish(model, configWithGraph);
    }

    /**
     * Delete all data from a graph.
     * @param graphUri The graph to clear (null for default graph)
     * @param config Configuration for the destination
     * @throws IOException if deletion fails
     */
    void clearGraph(String graphUri, Map<String, Object> config) throws IOException;

    /**
     * Check if the destination is reachable.
     * @param config Configuration for the destination
     * @return true if the destination is reachable
     */
    boolean isAvailable(Map<String, Object> config);

    /**
     * Validate the configuration for this destination.
     * @param config The configuration to validate
     * @return Validation result with any errors
     */
    ValidationResult validateConfig(Map<String, Object> config);

    /**
     * Result of a publish operation.
     */
    record PublishResult(
        boolean success,
        long triplesPublished,
        String graphUri,
        Map<String, Object> metadata,
        String errorMessage
    ) {
        public static PublishResult success(long triplesPublished, String graphUri, Map<String, Object> metadata) {
            return new PublishResult(true, triplesPublished, graphUri, metadata, null);
        }

        public static PublishResult failure(String errorMessage) {
            return new PublishResult(false, 0, null, Map.of(), errorMessage);
        }
    }

    /**
     * Result of configuration validation.
     */
    record ValidationResult(
        boolean valid,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, java.util.List.of(), java.util.List.of());
        }

        public static ValidationResult invalid(java.util.List<String> errors) {
            return new ValidationResult(false, errors, java.util.List.of());
        }
    }
}
