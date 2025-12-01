package io.rdfforge.engine.operation.source;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
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
            "skipRows", new ParameterSpec("skipRows", "Number of rows to skip", Integer.class, false, 0)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String filePath = (String) context.parameters().get("file");
        char delimiter = (char) context.parameters().getOrDefault("delimiter", ',');
        String encoding = (String) context.parameters().getOrDefault("encoding", "UTF-8");
        boolean hasHeader = (boolean) context.parameters().getOrDefault("hasHeader", true);
        int skipRows = (int) context.parameters().getOrDefault("skipRows", 0);

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new OperationException(getId(), "File not found: " + filePath);
            }

            Reader reader = Files.newBufferedReader(path, Charset.forName(encoding));
            CSVReader csvReader = new CSVReaderBuilder(reader)
                .withSkipLines(skipRows)
                .build();

            String[] headers = hasHeader ? csvReader.readNext() : null;
            
            Stream<Map<String, Object>> rowStream = StreamSupport.stream(
                new CsvRowSpliterator(csvReader, headers),
                false
            ).onClose(() -> {
                try {
                    csvReader.close();
                } catch (IOException e) {
                    log.warn("Error closing CSV reader", e);
                }
            });

            if (context.callback() != null) {
                context.callback().onLog("INFO", "Started reading CSV: " + filePath);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filePath);
            metadata.put("headers", headers != null ? Arrays.asList(headers) : Collections.emptyList());

            return new OperationResult(true, rowStream, null, metadata, null);

        } catch (IOException | CsvValidationException e) {
            throw new OperationException(getId(), "Error reading CSV: " + e.getMessage(), e);
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
                throw new RuntimeException("Error reading CSV row", e);
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
}
