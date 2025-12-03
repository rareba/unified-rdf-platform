package io.rdfforge.data.format;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interface for data format handlers.
 *
 * Each handler implementation must be a Spring component and will be
 * automatically discovered by the DataFormatRegistry.
 *
 * To add a new data format:
 * 1. Create a class implementing this interface
 * 2. Annotate it with @Component
 * 3. Implement getFormatInfo() with format metadata
 * 4. Implement the read/write methods as appropriate
 * 5. Return capabilities in supports() method
 *
 * The handler will be automatically registered and available via the API.
 */
public interface DataFormatHandler {

    /**
     * Get metadata about this format handler.
     * @return Format information including name, options, and capabilities
     */
    DataFormatInfo getFormatInfo();

    /**
     * Check if this handler supports a given file extension.
     * @param extension The file extension (without dot, lowercase)
     * @return true if this handler can process the extension
     */
    boolean supportsExtension(String extension);

    /**
     * Check if this handler supports a given MIME type.
     * @param mimeType The MIME type
     * @return true if this handler can process the MIME type
     */
    boolean supportsMimeType(String mimeType);

    /**
     * Preview the data without loading the entire file.
     * @param input The input stream
     * @param options Format-specific options
     * @param maxRows Maximum number of rows to return
     * @return Preview result with columns and sample rows
     */
    PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows);

    /**
     * Analyze the data structure without loading all content.
     * @param input The input stream
     * @param options Format-specific options
     * @return Analysis result with column types and statistics
     */
    AnalysisResult analyze(InputStream input, Map<String, Object> options);

    /**
     * Read data as an iterator for streaming processing.
     * @param input The input stream
     * @param options Format-specific options
     * @return Iterator over rows
     */
    Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options);

    /**
     * Read all data into memory.
     * @param input The input stream
     * @param options Format-specific options
     * @return List of rows
     */
    default List<Map<String, Object>> readAll(InputStream input, Map<String, Object> options) {
        Iterator<Map<String, Object>> iter = readIterator(input, options);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }

    /**
     * Write data to output stream.
     * @param data The data rows
     * @param columns The column names in order
     * @param output The output stream
     * @param options Format-specific options
     */
    void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options);

    /**
     * Get the format identifier.
     */
    default String getFormat() {
        return getFormatInfo().format();
    }

    /**
     * Result of a preview operation.
     */
    record PreviewResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        long totalRows,
        boolean hasMoreRows
    ) {}

    /**
     * Result of an analysis operation.
     */
    record AnalysisResult(
        List<ColumnInfo> columns,
        long totalRows,
        Map<String, Object> metadata
    ) {}

    /**
     * Information about a column.
     */
    record ColumnInfo(
        String name,
        String detectedType,
        long nullCount,
        long uniqueCount,
        List<Object> sampleValues,
        Map<String, Object> statistics
    ) {}
}
