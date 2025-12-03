package io.rdfforge.triplestore.connector;

import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;

/**
 * Interface for triplestore providers.
 *
 * Each provider implementation must be a Spring component and will be
 * automatically discovered by the TriplestoreProviderRegistry.
 *
 * To add a new triplestore provider:
 * 1. Create a class implementing this interface
 * 2. Annotate it with @Component
 * 3. Implement getProviderInfo() with provider metadata
 * 4. Implement createConnector() to create connection instances
 * 5. Implement supports() to indicate which type this provider handles
 *
 * The provider will be automatically registered and available via the API.
 */
public interface TriplestoreProvider {

    /**
     * Get metadata about this provider.
     * @return Provider information including name, capabilities, and configuration options
     */
    TriplestoreProviderInfo getProviderInfo();

    /**
     * Create a connector instance for the given connection configuration.
     * @param connection The connection configuration from the database
     * @return A connector instance ready to communicate with the triplestore
     */
    TriplestoreConnector createConnector(TriplestoreConnectionEntity connection);

    /**
     * Check if this provider supports the given triplestore type.
     * @param type The triplestore type from the connection entity
     * @return true if this provider can handle the type
     */
    boolean supports(TriplestoreConnectionEntity.TriplestoreType type);

    /**
     * Get the unique type identifier for this provider.
     * @return The type identifier (e.g., "FUSEKI", "GRAPHDB", "STARDOG")
     */
    default String getType() {
        return getProviderInfo().type();
    }
}
