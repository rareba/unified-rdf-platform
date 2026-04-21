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
    List<String> capabilities,
    /**
     * True if this format is fully implemented and accepts uploads. False indicates a
     * stub/placeholder format that is known about (so the UI can surface it as "coming
     * soon") but must not be offered as a real upload option.
     */
    boolean available,
    /**
     * Optional reason string displayed to users when {@code available} is false.
     * Null / empty when the format is available.
     */
    String unavailableReason
) {

    /**
     * Convenience constructor for fully-available formats. Preserves compatibility
     * with existing handler implementations that do not yet specify availability.
     */
    public DataFormatInfo(
            String format,
            String displayName,
            String description,
            String mimeType,
            List<String> fileExtensions,
            boolean supportsPreview,
            boolean supportsAnalysis,
            boolean supportsStreaming,
            Map<String, FormatOption> options,
            List<String> capabilities) {
        this(format, displayName, description, mimeType, fileExtensions,
             supportsPreview, supportsAnalysis, supportsStreaming,
             options, capabilities, true, null);
    }

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

        public FormatOption(String name, String displayName, String type, String description, Object defaultValue, List<Object> allowedValues) {
            this(name, displayName, type, description, false, defaultValue, allowedValues);
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
