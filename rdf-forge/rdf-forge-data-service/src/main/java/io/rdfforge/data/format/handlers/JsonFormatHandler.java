package io.rdfforge.data.format.handlers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatInfo.FormatOption;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.rdfforge.data.format.DataFormatInfo.*;

/**
 * Handler for JSON format.
 *
 * Supports:
 * - JSON arrays of objects (tabular data)
 * - JSON objects with array properties
 * - JSONPath for extracting nested arrays
 * - Streaming for large files
 */
@Component
public class JsonFormatHandler implements DataFormatHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "json",
        "JSON (JavaScript Object Notation)",
        "Flexible data format supporting nested structures. Best for array of objects.",
        "application/json",
        List.of("json"),
        true,
        true,
        true,
        Map.of(
            "jsonPath", new FormatOption("jsonPath", "JSON Path", "string",
                "Path to array of records (e.g., $.data or $.results[*])", "$"),
            "flattenNested", new FormatOption("flattenNested", "Flatten Nested", "boolean",
                "Flatten nested objects into dot-notation columns", false),
            "arrayHandling", new FormatOption("arrayHandling", "Array Handling", "select",
                "How to handle array values in columns", "stringify",
                List.of("stringify", "first", "join"))
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
        return "json".equalsIgnoreCase(extension);
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && (
            mimeType.equalsIgnoreCase("application/json") ||
            mimeType.equalsIgnoreCase("text/json")
        );
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        try {
            String jsonPath = getOption(options, "jsonPath", "$");
            boolean flattenNested = getOption(options, "flattenNested", false);

            JsonNode root = objectMapper.readTree(input);
            JsonNode dataNode = extractDataNode(root, jsonPath);

            if (!dataNode.isArray()) {
                throw new RuntimeException("JSON path must point to an array");
            }

            ArrayNode array = (ArrayNode) dataNode;
            Set<String> allColumns = new LinkedHashSet<>();
            List<Map<String, Object>> rows = new ArrayList<>();

            // Collect all column names and preview rows
            int count = 0;
            for (JsonNode item : array) {
                if (item.isObject()) {
                    Map<String, Object> row = jsonNodeToMap((ObjectNode) item, flattenNested, "", options);
                    allColumns.addAll(row.keySet());
                    if (count < maxRows) {
                        rows.add(row);
                    }
                    count++;
                }
            }

            // Ensure all rows have all columns
            List<String> columns = new ArrayList<>(allColumns);
            for (Map<String, Object> row : rows) {
                for (String col : columns) {
                    row.putIfAbsent(col, null);
                }
            }

            return new PreviewResult(columns, rows, count, count > maxRows);

        } catch (Exception e) {
            throw new RuntimeException("Failed to preview JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        try {
            String jsonPath = getOption(options, "jsonPath", "$");
            boolean flattenNested = getOption(options, "flattenNested", false);

            JsonNode root = objectMapper.readTree(input);
            JsonNode dataNode = extractDataNode(root, jsonPath);

            if (!dataNode.isArray()) {
                throw new RuntimeException("JSON path must point to an array");
            }

            ArrayNode array = (ArrayNode) dataNode;
            Map<String, ColumnAnalysis> columnAnalyses = new LinkedHashMap<>();
            long totalRows = 0;

            for (JsonNode item : array) {
                if (item.isObject()) {
                    Map<String, Object> row = jsonNodeToMap((ObjectNode) item, flattenNested, "", options);

                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        columnAnalyses.computeIfAbsent(entry.getKey(), k -> new ColumnAnalysis())
                            .addValue(entry.getValue());
                    }

                    totalRows++;
                }
            }

            // Build column info
            List<ColumnInfo> columns = new ArrayList<>();
            for (Map.Entry<String, ColumnAnalysis> entry : columnAnalyses.entrySet()) {
                ColumnAnalysis analysis = entry.getValue();
                columns.add(new ColumnInfo(
                    entry.getKey(),
                    analysis.getDetectedType(),
                    analysis.getNullCount(),
                    analysis.getUniqueCount(),
                    analysis.getSampleValues(),
                    Map.of()
                ));
            }

            return new AnalysisResult(columns, totalRows, Map.of("format", "json"));

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        try {
            String jsonPath = getOption(options, "jsonPath", "$");
            boolean flattenNested = getOption(options, "flattenNested", false);

            // For streaming, we use Jackson's streaming API
            JsonParser parser = objectMapper.getFactory().createParser(input);

            // Navigate to the array
            if (!"$".equals(jsonPath)) {
                // For non-root paths, we need to load the full tree and extract
                JsonNode root = objectMapper.readTree(parser);
                JsonNode dataNode = extractDataNode(root, jsonPath);

                if (!dataNode.isArray()) {
                    throw new RuntimeException("JSON path must point to an array");
                }

                ArrayNode array = (ArrayNode) dataNode;
                final Iterator<JsonNode> nodeIterator = array.iterator();

                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return nodeIterator.hasNext();
                    }

                    @Override
                    public Map<String, Object> next() {
                        JsonNode node = nodeIterator.next();
                        if (node.isObject()) {
                            return jsonNodeToMap((ObjectNode) node, flattenNested, "", options);
                        }
                        return Map.of();
                    }
                };
            }

            // For root array, stream directly
            // Find the start of the array
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new RuntimeException("Expected JSON array at root");
            }

            return new Iterator<>() {
                private Map<String, Object> nextItem = null;
                private boolean done = false;

                @Override
                public boolean hasNext() {
                    if (done) return false;
                    if (nextItem != null) return true;

                    try {
                        JsonToken token = parser.nextToken();
                        if (token == JsonToken.END_ARRAY || token == null) {
                            done = true;
                            return false;
                        }

                        if (token == JsonToken.START_OBJECT) {
                            ObjectNode node = objectMapper.readTree(parser);
                            nextItem = jsonNodeToMap(node, flattenNested, "", options);
                            return true;
                        }

                        return hasNext();
                    } catch (Exception e) {
                        done = true;
                        return false;
                    }
                }

                @Override
                public Map<String, Object> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    Map<String, Object> result = nextItem;
                    nextItem = null;
                    return result;
                }
            };

        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();

            for (Map<String, Object> row : data) {
                ObjectNode objectNode = objectMapper.createObjectNode();
                for (String column : columns) {
                    Object value = row.get(column);
                    if (value == null) {
                        objectNode.putNull(column);
                    } else if (value instanceof Number) {
                        if (value instanceof Integer) {
                            objectNode.put(column, (Integer) value);
                        } else if (value instanceof Long) {
                            objectNode.put(column, (Long) value);
                        } else if (value instanceof Double) {
                            objectNode.put(column, (Double) value);
                        } else {
                            objectNode.put(column, ((Number) value).doubleValue());
                        }
                    } else if (value instanceof Boolean) {
                        objectNode.put(column, (Boolean) value);
                    } else {
                        objectNode.put(column, value.toString());
                    }
                }
                arrayNode.add(objectNode);
            }

            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(output, arrayNode);

        } catch (Exception e) {
            throw new RuntimeException("Failed to write JSON: " + e.getMessage(), e);
        }
    }

    private JsonNode extractDataNode(JsonNode root, String jsonPath) {
        if (jsonPath == null || jsonPath.equals("$") || jsonPath.isEmpty()) {
            return root;
        }

        // Simple JSONPath implementation for basic paths like $.data or $.results
        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        JsonNode current = root;
        for (String part : parts) {
            // Handle array notation like items[*]
            if (part.endsWith("[*]")) {
                part = part.substring(0, part.length() - 3);
            }

            if (current.has(part)) {
                current = current.get(part);
            } else {
                throw new RuntimeException("Path not found: " + jsonPath);
            }
        }

        return current;
    }

    private Map<String, Object> jsonNodeToMap(ObjectNode node, boolean flatten, String prefix, Map<String, Object> options) {
        String arrayHandling = getOption(options, "arrayHandling", "stringify");
        Map<String, Object> result = new LinkedHashMap<>();

        node.fields().forEachRemaining(entry -> {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isNull()) {
                result.put(key, null);
            } else if (value.isTextual()) {
                result.put(key, value.asText());
            } else if (value.isNumber()) {
                if (value.isInt()) {
                    result.put(key, value.asInt());
                } else if (value.isLong()) {
                    result.put(key, value.asLong());
                } else {
                    result.put(key, value.asDouble());
                }
            } else if (value.isBoolean()) {
                result.put(key, value.asBoolean());
            } else if (value.isObject() && flatten) {
                result.putAll(jsonNodeToMap((ObjectNode) value, true, key, options));
            } else if (value.isArray()) {
                result.put(key, handleArray((ArrayNode) value, arrayHandling));
            } else {
                result.put(key, value.toString());
            }
        });

        return result;
    }

    private Object handleArray(ArrayNode array, String handling) {
        if (array.isEmpty()) return null;

        switch (handling) {
            case "first":
                JsonNode first = array.get(0);
                if (first.isTextual()) return first.asText();
                if (first.isNumber()) return first.numberValue();
                if (first.isBoolean()) return first.asBoolean();
                return first.toString();

            case "join":
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < array.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(array.get(i).asText());
                }
                return sb.toString();

            case "stringify":
            default:
                return array.toString();
        }
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

        return (T) value;
    }

    /**
     * Helper class for column analysis.
     */
    private static class ColumnAnalysis {
        private final Set<Object> uniqueValues = new HashSet<>();
        private final List<Object> sampleValues = new ArrayList<>();
        private long nullCount = 0;
        private String detectedType = null;

        void addValue(Object value) {
            if (value == null) {
                nullCount++;
                return;
            }

            uniqueValues.add(value);

            if (sampleValues.size() < 5) {
                sampleValues.add(value);
            }

            String type = detectType(value);
            if (detectedType == null) {
                detectedType = type;
            } else if (!detectedType.equals(type)) {
                detectedType = "string";
            }
        }

        String getDetectedType() {
            return detectedType != null ? detectedType : "string";
        }

        long getNullCount() {
            return nullCount;
        }

        long getUniqueCount() {
            return uniqueValues.size();
        }

        List<Object> getSampleValues() {
            return new ArrayList<>(sampleValues);
        }

        private String detectType(Object value) {
            if (value instanceof Integer || value instanceof Long) return "integer";
            if (value instanceof Number) return "decimal";
            if (value instanceof Boolean) return "boolean";
            return "string";
        }
    }
}
