package io.rdfforge.triplestore.connector;

import java.util.List;
import java.util.Map;

/**
 * Metadata about a triplestore provider.
 * This record provides information about a triplestore type that can be used
 * by the UI and API to dynamically configure connections.
 */
public record TriplestoreProviderInfo(
    String type,
    String displayName,
    String description,
    String vendor,
    String documentationUrl,
    List<AuthTypeInfo> supportedAuthTypes,
    List<String> supportedRdfFormats,
    Map<String, ConfigField> configFields,
    List<String> capabilities
) {

    /**
     * Information about an authentication type.
     */
    public record AuthTypeInfo(
        String type,
        String displayName,
        String description,
        List<ConfigField> requiredFields
    ) {}

    /**
     * A configuration field for a provider.
     */
    public record ConfigField(
        String name,
        String displayName,
        String type,
        String description,
        boolean required,
        String defaultValue,
        String placeholder,
        List<String> options
    ) {
        public ConfigField(String name, String displayName, String type, String description, boolean required) {
            this(name, displayName, type, description, required, null, null, null);
        }

        public ConfigField(String name, String displayName, String type, String description, boolean required, String defaultValue) {
            this(name, displayName, type, description, required, defaultValue, null, null);
        }

        public ConfigField(String name, String displayName, String type, String description, boolean required, String defaultValue, String placeholder) {
            this(name, displayName, type, description, required, defaultValue, placeholder, null);
        }
    }

    /**
     * Standard capabilities that providers can support.
     */
    public static final String CAPABILITY_SPARQL_QUERY = "sparql-query";
    public static final String CAPABILITY_SPARQL_UPDATE = "sparql-update";
    public static final String CAPABILITY_GRAPH_STORE = "graph-store-protocol";
    public static final String CAPABILITY_NAMED_GRAPHS = "named-graphs";
    public static final String CAPABILITY_TRANSACTIONS = "transactions";
    public static final String CAPABILITY_FULL_TEXT_SEARCH = "full-text-search";
    public static final String CAPABILITY_GEOSPATIAL = "geospatial";
    public static final String CAPABILITY_REASONING = "reasoning";
    public static final String CAPABILITY_SHACL_VALIDATION = "shacl-validation";
    public static final String CAPABILITY_FEDERATED_QUERY = "federated-query";
}
