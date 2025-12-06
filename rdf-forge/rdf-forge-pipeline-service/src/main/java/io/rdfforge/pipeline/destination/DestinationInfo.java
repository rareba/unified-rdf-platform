package io.rdfforge.pipeline.destination;

import java.util.List;
import java.util.Map;

/**
 * Metadata about a destination provider.
 *
 * Provides information about where RDF data can be published,
 * including configuration options and capabilities.
 */
public record DestinationInfo(
    String type,
    String displayName,
    String description,
    String category,
    Map<String, ConfigField> configFields,
    List<String> capabilities,
    List<String> supportedFormats
) {
    /**
     * A configuration field for a destination.
     */
    public record ConfigField(
        String name,
        String displayName,
        String type,
        String description,
        boolean required,
        Object defaultValue,
        List<Object> allowedValues,
        boolean sensitive
    ) {
        public ConfigField(String name, String displayName, String type, String description, boolean required) {
            this(name, displayName, type, description, required, null, null, false);
        }

        public ConfigField(String name, String displayName, String type, String description, boolean required, Object defaultValue) {
            this(name, displayName, type, description, required, defaultValue, null, false);
        }

        public ConfigField(String name, String displayName, String type, String description, boolean required, boolean sensitive) {
            this(name, displayName, type, description, required, null, null, sensitive);
        }
    }

    /**
     * Destination categories.
     */
    public static final String CATEGORY_TRIPLESTORE = "triplestore";
    public static final String CATEGORY_FILE = "file";
    public static final String CATEGORY_CLOUD_STORAGE = "cloud-storage";
    public static final String CATEGORY_API = "api";
    public static final String CATEGORY_CICD = "ci-cd";

    /**
     * Standard capabilities for destinations.
     */
    public static final String CAPABILITY_NAMED_GRAPHS = "named-graphs";
    public static final String CAPABILITY_TRANSACTIONS = "transactions";
    public static final String CAPABILITY_APPEND = "append";
    public static final String CAPABILITY_REPLACE = "replace";
    public static final String CAPABILITY_DELETE = "delete";
    public static final String CAPABILITY_BATCH = "batch";
    public static final String CAPABILITY_STREAMING = "streaming";
}
