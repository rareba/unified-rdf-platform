package io.rdfforge.data.format.handlers;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatInfo.FormatOption;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

import static io.rdfforge.data.format.DataFormatInfo.*;

/**
 * Handler for CSV (Comma-Separated Values) format.
 *
 * Supports various CSV dialects including:
 * - Standard comma-separated
 * - Tab-separated (TSV)
 * - Semicolon-separated (European style)
 * - Custom delimiters
 */
@Component
public class CsvFormatHandler implements DataFormatHandler {

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "csv",
        "CSV (Comma-Separated Values)",
        "Tabular data format with rows and columns. Supports various delimiters and encodings.",
        "text/csv",
        List.of("csv", "tsv", "txt"),
        true,
        true,
        true,
        Map.of(
            "delimiter", new FormatOption("delimiter", "Delimiter", "string",
                "Column separator character", ","),
            "encoding", new FormatOption("encoding", "Encoding", "select",
                "Character encoding", "UTF-8", List.of("UTF-8", "ISO-8859-1", "Windows-1252")),
            "hasHeader", new FormatOption("hasHeader", "Has Header Row", "boolean",
                "First row contains column names", true),
            "quoteChar", new FormatOption("quoteChar", "Quote Character", "string",
                "Character used to quote fields", "\""),
            "escapeChar", new FormatOption("escapeChar", "Escape Character", "string",
                "Character used to escape special characters", "\\"),
            "skipRows", new FormatOption("skipRows", "Skip Rows", "number",
                "Number of rows to skip at the beginning", 0),
            "trimWhitespace", new FormatOption("trimWhitespace", "Trim Whitespace", "boolean",
                "Remove leading/trailing whitespace from values", true)
        ),
        List.of(
            CAPABILITY_READ,
            CAPABILITY_WRITE,
            CAPABILITY_ANALYZE,
            CAPABILITY_PREVIEW,
            CAPABILITY_STREAMING,
            CAPABILITY_SCHEMA_INFERENCE,
            CAPABILITY_TYPE_DETECTION
        )
    );

    @Override
    public DataFormatInfo getFormatInfo() {
        return INFO;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return extension != null && List.of("csv", "tsv", "txt").contains(extension.toLowerCase());
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && (
            mimeType.equalsIgnoreCase("text/csv") ||
            mimeType.equalsIgnoreCase("text/tab-separated-values") ||
            mimeType.equalsIgnoreCase("text/plain")
        );
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        try {
            CSVReader reader = createReader(input, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            // Skip initial rows
            for (int i = 0; i < skipRows; i++) {
                reader.readNext();
            }

            // Read header or generate column names
            String[] headerRow = reader.readNext();
            List<String> columns;
            List<Map<String, Object>> rows = new ArrayList<>();

            if (hasHeader && headerRow != null) {
                columns = Arrays.asList(headerRow);
            } else {
                columns = generateColumnNames(headerRow != null ? headerRow.length : 0);
                if (headerRow != null) {
                    rows.add(rowToMap(columns, headerRow, options));
                }
            }

            // Read data rows
            String[] row;
            int count = rows.size();
            boolean hasMore = false;

            while ((row = reader.readNext()) != null && count < maxRows) {
                rows.add(rowToMap(columns, row, options));
                count++;
            }

            // Check if there are more rows
            if (reader.readNext() != null) {
                hasMore = true;
            }

            // Count remaining rows for total
            long totalRows = count;
            if (hasMore) {
                while (reader.readNext() != null) {
                    totalRows++;
                }
            }

            reader.close();
            return new PreviewResult(columns, rows, totalRows, hasMore);

        } catch (Exception e) {
            throw new RuntimeException("Failed to preview CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        try {
            CSVReader reader = createReader(input, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            // Skip initial rows
            for (int i = 0; i < skipRows; i++) {
                reader.readNext();
            }

            // Read header or generate column names
            String[] headerRow = reader.readNext();
            List<String> columns;

            if (hasHeader && headerRow != null) {
                columns = Arrays.asList(headerRow);
            } else {
                columns = generateColumnNames(headerRow != null ? headerRow.length : 0);
            }

            // Initialize analysis structures
            int numColumns = columns.size();
            List<List<String>> sampleValues = new ArrayList<>();
            long[] nullCounts = new long[numColumns];
            Set<String>[] uniqueValues = new HashSet[numColumns];
            String[] detectedTypes = new String[numColumns];

            for (int i = 0; i < numColumns; i++) {
                sampleValues.add(new ArrayList<>());
                uniqueValues[i] = new HashSet<>();
                detectedTypes[i] = null;
            }

            // If header row was actually data, process it
            if (!hasHeader && headerRow != null) {
                processRowForAnalysis(headerRow, sampleValues, nullCounts, uniqueValues, detectedTypes);
            }

            // Process all rows
            String[] row;
            long totalRows = hasHeader ? 0 : 1;

            while ((row = reader.readNext()) != null) {
                processRowForAnalysis(row, sampleValues, nullCounts, uniqueValues, detectedTypes);
                totalRows++;
            }

            reader.close();

            // Build column info
            List<ColumnInfo> columnInfos = new ArrayList<>();
            for (int i = 0; i < numColumns; i++) {
                columnInfos.add(new ColumnInfo(
                    columns.get(i),
                    detectedTypes[i] != null ? detectedTypes[i] : "string",
                    nullCounts[i],
                    uniqueValues[i].size(),
                    new ArrayList<>(sampleValues.get(i)),
                    Map.of()
                ));
            }

            return new AnalysisResult(columnInfos, totalRows, Map.of());

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        try {
            CSVReader reader = createReader(input, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            // Skip initial rows
            for (int i = 0; i < skipRows; i++) {
                reader.readNext();
            }

            // Read header or generate column names
            String[] headerRow = reader.readNext();
            List<String> columns;
            String[] firstDataRow = null;

            if (hasHeader && headerRow != null) {
                columns = Arrays.asList(headerRow);
            } else {
                columns = generateColumnNames(headerRow != null ? headerRow.length : 0);
                firstDataRow = headerRow;
            }

            final List<String> finalColumns = columns;
            final String[] finalFirstDataRow = firstDataRow;

            return new Iterator<>() {
                private String[] nextRow = finalFirstDataRow;
                private boolean hasNext = (nextRow != null);
                private boolean checkedNext = (nextRow != null);

                @Override
                public boolean hasNext() {
                    if (!checkedNext) {
                        try {
                            nextRow = reader.readNext();
                            hasNext = (nextRow != null);
                            checkedNext = true;
                        } catch (Exception e) {
                            hasNext = false;
                        }
                    }
                    return hasNext;
                }

                @Override
                public Map<String, Object> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    Map<String, Object> result = rowToMap(finalColumns, nextRow, options);
                    checkedNext = false;
                    return result;
                }
            };

        } catch (Exception e) {
            throw new RuntimeException("Failed to read CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        try {
            String delimiter = getOption(options, "delimiter", ",");
            String encoding = getOption(options, "encoding", "UTF-8");
            char delimiterChar = delimiter.isEmpty() ? ',' : delimiter.charAt(0);

            OutputStreamWriter writer = new OutputStreamWriter(output, Charset.forName(encoding));
            CSVWriter csvWriter = new CSVWriter(writer, delimiterChar, '"', '\\', "\n");

            // Write header
            csvWriter.writeNext(columns.toArray(new String[0]));

            // Write data
            for (Map<String, Object> row : data) {
                String[] values = columns.stream()
                    .map(col -> row.get(col) != null ? row.get(col).toString() : "")
                    .toArray(String[]::new);
                csvWriter.writeNext(values);
            }

            csvWriter.close();

        } catch (Exception e) {
            throw new RuntimeException("Failed to write CSV: " + e.getMessage(), e);
        }
    }

    private CSVReader createReader(InputStream input, Map<String, Object> options) throws Exception {
        String delimiter = getOption(options, "delimiter", ",");
        String encoding = getOption(options, "encoding", "UTF-8");
        String quoteChar = getOption(options, "quoteChar", "\"");
        String escapeChar = getOption(options, "escapeChar", "\\");

        char delimiterChar = delimiter.isEmpty() ? ',' : delimiter.charAt(0);
        char quoteCharValue = quoteChar.isEmpty() ? '"' : quoteChar.charAt(0);
        char escapeCharValue = escapeChar.isEmpty() ? '\\' : escapeChar.charAt(0);

        CSVParser parser = new CSVParserBuilder()
            .withSeparator(delimiterChar)
            .withQuoteChar(quoteCharValue)
            .withEscapeChar(escapeCharValue)
            .build();

        Reader reader = new InputStreamReader(input, Charset.forName(encoding));
        return new CSVReaderBuilder(reader)
            .withCSVParser(parser)
            .build();
    }

    private Map<String, Object> rowToMap(List<String> columns, String[] row, Map<String, Object> options) {
        boolean trimWhitespace = getOption(options, "trimWhitespace", true);
        Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i < columns.size(); i++) {
            String value = (i < row.length) ? row[i] : null;
            if (value != null && trimWhitespace) {
                value = value.trim();
            }
            map.put(columns.get(i), value);
        }

        return map;
    }

    private List<String> generateColumnNames(int count) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns.add("column_" + (i + 1));
        }
        return columns;
    }

    private void processRowForAnalysis(String[] row, List<List<String>> sampleValues,
                                       long[] nullCounts, Set<String>[] uniqueValues, String[] detectedTypes) {
        for (int i = 0; i < row.length && i < sampleValues.size(); i++) {
            String value = row[i] != null ? row[i].trim() : null;

            if (value == null || value.isEmpty()) {
                nullCounts[i]++;
            } else {
                uniqueValues[i].add(value);

                // Collect sample values (up to 5)
                if (sampleValues.get(i).size() < 5) {
                    sampleValues.get(i).add(value);
                }

                // Detect type
                String type = detectType(value);
                if (detectedTypes[i] == null) {
                    detectedTypes[i] = type;
                } else if (!detectedTypes[i].equals(type)) {
                    // If types conflict, fall back to string
                    if (!detectedTypes[i].equals("string") && !type.equals("string")) {
                        detectedTypes[i] = "string";
                    }
                }
            }
        }
    }

    private String detectType(String value) {
        if (value == null || value.isEmpty()) return "string";

        // Check integer
        if (Pattern.matches("-?\\d+", value)) {
            return "integer";
        }

        // Check decimal
        if (Pattern.matches("-?\\d+\\.\\d+", value)) {
            return "decimal";
        }

        // Check boolean
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return "boolean";
        }

        // Check date (YYYY-MM-DD)
        if (Pattern.matches("\\d{4}-\\d{2}-\\d{2}", value)) {
            return "date";
        }

        // Check datetime
        if (Pattern.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2})?.*", value)) {
            return "datetime";
        }

        return "string";
    }

    @SuppressWarnings("unchecked")
    private <T> T getOption(Map<String, Object> options, String key, T defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value == null) return defaultValue;

        if (defaultValue instanceof Boolean && value instanceof String) {
            return (T) Boolean.valueOf((String) value);
        }
        if (defaultValue instanceof Integer && value instanceof String) {
            return (T) Integer.valueOf((String) value);
        }

        return (T) value;
    }
}
