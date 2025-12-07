package io.rdfforge.data.storage;

import java.util.List;
import java.util.Map;

/**
 * Metadata about a storage provider.
 *
 * This record provides information about a storage provider that can be used
 * by the UI and API to dynamically show available storage options and their configuration.
 */
public record StorageProviderInfo(
    String type,
    String displayName,
    String description,
    String vendor,
    String documentationUrl,
    Map<String, ConfigField> configFields,
    List<String> capabilities
) {
    /**
     * A configuration field for a storage provider.
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

        public ConfigField(String name, String displayName, String type, String description, boolean required, Object defaultValue, List<Object> allowedValues) {
            this(name, displayName, type, description, required, defaultValue, allowedValues, false);
        }
    }

    /**
     * Standard capabilities for storage providers.
     */
    public static final String CAPABILITY_UPLOAD = "upload";
    public static final String CAPABILITY_DOWNLOAD = "download";
    public static final String CAPABILITY_DELETE = "delete";
    public static final String CAPABILITY_LIST = "list";
    public static final String CAPABILITY_PRESIGNED_URL = "presigned-url";
    public static final String CAPABILITY_MULTIPART_UPLOAD = "multipart-upload";
    public static final String CAPABILITY_VERSIONING = "versioning";
    public static final String CAPABILITY_ENCRYPTION = "encryption";
    public static final String CAPABILITY_LIFECYCLE = "lifecycle";
}
