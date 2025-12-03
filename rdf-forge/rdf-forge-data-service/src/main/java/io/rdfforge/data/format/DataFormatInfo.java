package io.rdfforge.data.format;

import java.util.List;
import java.util.Map;

/**
 * Metadata about a data format handler.
 *
 * This record provides information about a data format that can be used
 * by the UI and API to dynamically show available formats and their options.
 */
public record DataFormatInfo(
    String format,
    String displayName,
    String description,
    String mimeType,
    List<String> fileExtensions,
    boolean supportsPreview,
    boolean supportsAnalysis,
    boolean supportsStreaming,
    Map<String, FormatOption> options,
    List<String> capabilities
) {

    /**
     * A configuration option for a format handler.
     */
    public record FormatOption(
        String name,
        String displayName,
        String type,
        String description,
        boolean required,
        Object defaultValue,
        List<Object> allowedValues
    ) {
        public FormatOption(String name, String displayName, String type, String description, Object defaultValue) {
            this(name, displayName, type, description, false, defaultValue, null);
        }

        public FormatOption(String name, String displayName, String type, String description, boolean required, Object defaultValue) {
            this(name, displayName, type, description, required, defaultValue, null);
        }
    }

    /**
     * Standard capabilities for format handlers.
     */
    public static final String CAPABILITY_READ = "read";
    public static final String CAPABILITY_WRITE = "write";
    public static final String CAPABILITY_ANALYZE = "analyze";
    public static final String CAPABILITY_PREVIEW = "preview";
    public static final String CAPABILITY_STREAMING = "streaming";
    public static final String CAPABILITY_SCHEMA_INFERENCE = "schema-inference";
    public static final String CAPABILITY_TYPE_DETECTION = "type-detection";
}
