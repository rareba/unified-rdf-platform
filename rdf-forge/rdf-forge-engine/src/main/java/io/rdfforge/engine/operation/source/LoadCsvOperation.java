package io.rdfforge.engine.operation.source;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class LoadCsvOperation implements Operation {

    @Override
    public String getId() {
        return "load-csv";
    }

    @Override
    public String getName() {
        return "Load CSV";
    }

    @Override
    public String getDescription() {
        return "Load data from a CSV file";
    }

    @Override
    public OperationType getType() {
        return OperationType.SOURCE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "file", new ParameterSpec("file", "Path to CSV file or URL", String.class, true, null),
            "delimiter", new ParameterSpec("delimiter", "Column delimiter", Character.class, false, ','),
            "encoding", new ParameterSpec("encoding", "Character encoding", String.class, false, "UTF-8"),
            "hasHeader", new ParameterSpec("hasHeader", "First row is header", Boolean.class, false, true),
            "skipRows", new ParameterSpec("skipRows", "Number of rows to skip", Integer.class, false, 0),
            "quoteChar", new ParameterSpec("quoteChar", "Quote character", Character.class, false, '"'),
            "escapeChar", new ParameterSpec("escapeChar", "Escape character", Character.class, false, '\\')
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String filePath = (String) context.parameters().get("file");
        char delimiter = parseDelimiter(context.parameters().getOrDefault("delimiter", ','));
        String encoding = parseEncoding(context.parameters().getOrDefault("encoding", "UTF-8"));
        boolean hasHeader = parseBoolean(context.parameters().getOrDefault("hasHeader", true));
        int skipRows = parseInteger(context.parameters().getOrDefault("skipRows", 0));
        char quoteChar = parseChar(context.parameters().get("quoteChar"), '"');
        char escapeChar = parseChar(context.parameters().get("escapeChar"), '\\');

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new OperationException(getId(), "File path is required");
        }

        Path path = resolveFilePath(filePath);
        if (path == null || !Files.exists(path)) {
            throw new OperationException(getId(), "File not found: " + filePath);
        }

        final Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new OperationException(getId(),
                "Unsupported encoding '" + encoding + "' for file " + filePath, e);
        }

        // NOTE: the Reader/CSVReader are intentionally NOT closed in a try-with-resources
        // block here because ownership is transferred to the returned lazy Stream via
        // onClose(). Callers MUST close the Stream (try-with-resources or stream.close())
        // to release the underlying file descriptor. If construction fails before the
        // Stream is returned, we close the Reader explicitly in the catch block below.
        Reader reader = null;
        CSVReader csvReader = null;
        try {
            reader = Files.newBufferedReader(path, charset);
            // Wire the delimiter, quote, and escape chars through to the parser.
            // Previously the delimiter was parsed from params but never applied to
            // the CSVReaderBuilder, which broke TSV / pipe-delimited / European CSV.
            CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar(quoteChar)
                .withEscapeChar(escapeChar)
                .build();
            csvReader = new CSVReaderBuilder(reader)
                .withSkipLines(skipRows)
                .withCSVParser(parser)
                .build();

            String[] headers = hasHeader ? csvReader.readNext() : null;

            // Capture final references for the onClose lambda
            final CSVReader finalCsvReader = csvReader;
            Stream<Map<String, Object>> rowStream = StreamSupport.stream(
                new CsvRowSpliterator(finalCsvReader, headers),
                false
            ).onClose(() -> {
                try {
                    // CSVReader.close() also closes the underlying Reader
                    finalCsvReader.close();
                } catch (IOException e) {
                    log.warn("Error closing CSV reader", e);
                }
            });

            if (context.callback() != null) {
                context.callback().onLog("INFO", "Started reading CSV: " + filePath
                    + " (delimiter=" + describeChar(delimiter) + ", encoding=" + encoding + ")");
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filePath);
            metadata.put("headers", headers != null ? Arrays.asList(headers) : Collections.emptyList());
            metadata.put("delimiter", String.valueOf(delimiter));
            metadata.put("encoding", encoding);

            return new OperationResult(true, rowStream, null, metadata, null);

        } catch (IOException | CsvValidationException e) {
            // Stream was not returned to caller — close the reader(s) here to prevent FD leak.
            closeQuietly(csvReader);
            if (csvReader == null) {
                closeQuietly(reader);
            }
            throw new OperationException(getId(),
                "Error reading CSV '" + filePath + "' (delimiter=" + describeChar(delimiter)
                    + ", encoding=" + encoding + "): " + e.getMessage(), e);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            log.warn("Error closing CSV resource", e);
        }
    }

    private static class CsvRowSpliterator implements Spliterator<Map<String, Object>> {
        private final CSVReader reader;
        private final String[] headers;
        private long rowCount = 0;

        CsvRowSpliterator(CSVReader reader, String[] headers) {
            this.reader = reader;
            this.headers = headers;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super Map<String, Object>> action) {
            try {
                String[] row = reader.readNext();
                if (row == null) {
                    return false;
                }
                
                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("_rowNumber", ++rowCount);
                
                if (headers != null) {
                    for (int i = 0; i < headers.length && i < row.length; i++) {
                        rowMap.put(headers[i], row[i]);
                    }
                } else {
                    for (int i = 0; i < row.length; i++) {
                        rowMap.put("column" + i, row[i]);
                    }
                }
                
                action.accept(rowMap);
                return true;
            } catch (IOException | CsvValidationException e) {
                throw new RuntimeException("Error reading CSV row " + (rowCount + 1) + ": " + e.getMessage(), e);
            }
        }

        @Override
        public Spliterator<Map<String, Object>> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }
    }

    /**
     * Resolve file path - handles relative paths by checking multiple locations
     */
    private Path resolveFilePath(String filePath) {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        // Allowed base directories for file resolution
        String[] allowedDataDirs = {
            "demo-data",
            "docker/demo-data",
            "data",
            "src/main/resources/data",
            "src/test/resources/data"
        };

        // Try each allowed directory
        for (String dataDir : allowedDataDirs) {
            Path baseDir = workingDir.resolve(dataDir).normalize();
            Path resolved = baseDir.resolve(filePath).normalize();
            if (resolved.startsWith(baseDir) && Files.exists(resolved)) {
                return resolved;
            }
            // Also try just the filename
            String fileName = Path.of(filePath).getFileName().toString();
            Path fileNameResolved = baseDir.resolve(fileName).normalize();
            if (fileNameResolved.startsWith(baseDir) && Files.exists(fileNameResolved)) {
                return fileNameResolved;
            }
        }

        // Also try relative to working directory, but enforce containment
        Path resolved = workingDir.resolve(filePath).normalize();
        if (resolved.startsWith(workingDir) && Files.exists(resolved)) {
            return resolved;
        }

        // Return path under working dir for error message (guaranteed contained)
        return workingDir.resolve(Path.of(filePath).getFileName().toString());
    }

    /**
     * Parse delimiter from various input types (String, Character, char).
     *
     * <p>Supports escape-sequence literals for tab-separated value files:
     * the two-character strings {@code "\t"} (real tab) and {@code "\\t"}
     * (backslash-t literal, as it often arrives from JSON/YAML/CLI inputs)
     * are both normalised to the single tab character {@code '\t'}. The same
     * holds for {@code "\\n"}, {@code "\\r"}, and {@code "\\\\"}.
     *
     * <p>Package-private / static for unit testing.
     *
     * @param value user-supplied delimiter (may be null, String, Character, or other)
     * @return a single {@code char} delimiter; comma if input is null/empty/unrecognised
     */
    static char parseDelimiter(Object value) {
        if (value == null) {
            return ',';
        }
        if (value instanceof Character) {
            return (Character) value;
        }
        if (value instanceof String str) {
            return parseDelimiterString(str);
        }
        // Defensive: try to convert to string and normalise
        return parseDelimiterString(value.toString());
    }

    private static char parseDelimiterString(String str) {
        if (str == null || str.isEmpty()) {
            return ',';
        }
        // Normalise common escape-sequence literals that arrive as strings
        // from JSON / YAML / CLI input (e.g. "\\t" arrives as the two chars
        // '\\' and 't', which we map to the single tab character).
        switch (str) {
            case "\\t":
            case "\t":
                return '\t';
            case "\\n":
            case "\n":
                return '\n';
            case "\\r":
            case "\r":
                return '\r';
            case "\\\\":
                return '\\';
            default:
                return str.charAt(0);
        }
    }

    /**
     * Generic single-char parser for parameters like quoteChar / escapeChar.
     * Falls back to {@code fallback} for null/empty/unparseable input.
     */
    private static char parseChar(Object value, char fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Character) {
            return (Character) value;
        }
        String s = value.toString();
        return s.isEmpty() ? fallback : s.charAt(0);
    }

    /**
     * Parse encoding parameter, returning "UTF-8" if null/blank.
     * Name validation is deferred to {@link Charset#forName(String)} at the call site.
     */
    private static String parseEncoding(Object value) {
        if (value == null) {
            return StandardCharsets.UTF_8.name();
        }
        String s = value.toString();
        return s.isBlank() ? StandardCharsets.UTF_8.name() : s;
    }

    /**
     * Parse boolean from various input types (Boolean, String)
     */
    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return true;
    }

    /**
     * Parse integer from various input types (Integer, Number, String)
     */
    private int parseInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** Human-readable rendering of a delimiter char for log messages. */
    private static String describeChar(char c) {
        switch (c) {
            case '\t':
                return "\\t";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            default:
                return String.valueOf(c);
        }
    }
}
