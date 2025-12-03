package io.rdfforge.data.format;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for data format handlers.
 *
 * This component automatically discovers all DataFormatHandler implementations
 * at startup and provides a unified API for accessing them.
 *
 * Adding a new format is as simple as:
 * 1. Creating a new class implementing DataFormatHandler
 * 2. Annotating it with @Component
 *
 * The registry will automatically find and register it.
 */
@Component
@Slf4j
public class DataFormatRegistry {

    private final List<DataFormatHandler> handlers;
    private final Map<String, DataFormatHandler> handlersByFormat = new ConcurrentHashMap<>();
    private final Map<String, DataFormatHandler> handlersByExtension = new ConcurrentHashMap<>();
    private final Map<String, DataFormatHandler> handlersByMimeType = new ConcurrentHashMap<>();

    public DataFormatRegistry(List<DataFormatHandler> handlers) {
        this.handlers = handlers != null ? handlers : Collections.emptyList();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing DataFormatRegistry with {} handlers", handlers.size());

        for (DataFormatHandler handler : handlers) {
            DataFormatInfo info = handler.getFormatInfo();
            String format = info.format().toLowerCase();

            handlersByFormat.put(format, handler);

            // Map extensions to handlers
            for (String ext : info.fileExtensions()) {
                handlersByExtension.put(ext.toLowerCase(), handler);
            }

            // Map MIME type to handler
            if (info.mimeType() != null) {
                handlersByMimeType.put(info.mimeType().toLowerCase(), handler);
            }

            log.info("Registered format handler '{}' for extensions: {}",
                info.displayName(), info.fileExtensions());
        }

        log.info("DataFormatRegistry initialized. Available formats: {}",
            handlersByFormat.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    /**
     * Get all available format handlers.
     * @return List of all registered handlers
     */
    public List<DataFormatHandler> getAllHandlers() {
        return new ArrayList<>(handlers);
    }

    /**
     * Get info for all available formats.
     * @return List of format information
     */
    public List<DataFormatInfo> getAvailableFormats() {
        return handlers.stream()
            .map(DataFormatHandler::getFormatInfo)
            .collect(Collectors.toList());
    }

    /**
     * Get a handler by format name.
     * @param format The format identifier (e.g., "csv", "json")
     * @return Optional containing the handler if found
     */
    public Optional<DataFormatHandler> getHandler(String format) {
        return Optional.ofNullable(handlersByFormat.get(format.toLowerCase()));
    }

    /**
     * Get a handler by file extension.
     * @param extension The file extension (without dot)
     * @return Optional containing the handler if found
     */
    public Optional<DataFormatHandler> getHandlerByExtension(String extension) {
        return Optional.ofNullable(handlersByExtension.get(extension.toLowerCase()));
    }

    /**
     * Get a handler by MIME type.
     * @param mimeType The MIME type
     * @return Optional containing the handler if found
     */
    public Optional<DataFormatHandler> getHandlerByMimeType(String mimeType) {
        return Optional.ofNullable(handlersByMimeType.get(mimeType.toLowerCase()));
    }

    /**
     * Detect format from filename.
     * @param filename The filename
     * @return Optional containing the detected format
     */
    public Optional<String> detectFormat(String filename) {
        if (filename == null) return Optional.empty();

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) return Optional.empty();

        String extension = filename.substring(dotIndex + 1).toLowerCase();
        return getHandlerByExtension(extension)
            .map(h -> h.getFormatInfo().format());
    }

    /**
     * Get format info for a specific format.
     * @param format The format identifier
     * @return Optional containing the format info if found
     */
    public Optional<DataFormatInfo> getFormatInfo(String format) {
        return getHandler(format).map(DataFormatHandler::getFormatInfo);
    }

    /**
     * Check if a format is supported.
     * @param format The format identifier
     * @return true if the format is supported
     */
    public boolean isSupported(String format) {
        return handlersByFormat.containsKey(format.toLowerCase());
    }

    /**
     * Check if an extension is supported.
     * @param extension The file extension
     * @return true if the extension is supported
     */
    public boolean isExtensionSupported(String extension) {
        return handlersByExtension.containsKey(extension.toLowerCase());
    }
}
