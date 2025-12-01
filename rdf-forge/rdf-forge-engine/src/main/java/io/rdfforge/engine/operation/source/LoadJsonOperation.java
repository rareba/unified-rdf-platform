package io.rdfforge.engine.operation.source;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class LoadJsonOperation implements Operation {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return "load-json";
    }

    @Override
    public String getName() {
        return "Load JSON";
    }

    @Override
    public String getDescription() {
        return "Load data from a JSON file or array";
    }

    @Override
    public OperationType getType() {
        return OperationType.SOURCE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "file", new ParameterSpec("file", "Path to JSON file", String.class, true, null),
            "jsonPath", new ParameterSpec("jsonPath", "JSON path to array (e.g., $.data)", String.class, false, null),
            "streaming", new ParameterSpec("streaming", "Use streaming parser for large files", Boolean.class, false, false)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String filePath = (String) context.parameters().get("file");
        String jsonPath = (String) context.parameters().get("jsonPath");
        boolean streaming = (boolean) context.parameters().getOrDefault("streaming", false);

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new OperationException(getId(), "File not found: " + filePath);
            }

            Stream<Map<String, Object>> dataStream;

            if (streaming) {
                dataStream = streamJsonArray(path);
            } else {
                JsonNode root = objectMapper.readTree(path.toFile());
                
                JsonNode dataNode = root;
                if (jsonPath != null && !jsonPath.isEmpty()) {
                    dataNode = navigateJsonPath(root, jsonPath);
                }

                if (dataNode.isArray()) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (JsonNode node : dataNode) {
                        items.add(objectMapper.convertValue(node, Map.class));
                    }
                    dataStream = items.stream();
                } else {
                    Map<String, Object> singleItem = objectMapper.convertValue(dataNode, Map.class);
                    dataStream = Stream.of(singleItem);
                }
            }

            if (context.callback() != null) {
                context.callback().onLog("INFO", "Started reading JSON: " + filePath);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filePath);
            metadata.put("jsonPath", jsonPath);

            return new OperationResult(true, dataStream, null, metadata, null);

        } catch (IOException e) {
            throw new OperationException(getId(), "Error reading JSON: " + e.getMessage(), e);
        }
    }

    private Stream<Map<String, Object>> streamJsonArray(Path path) throws IOException {
        JsonParser parser = objectMapper.getFactory().createParser(path.toFile());
        
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected JSON array at root");
        }

        Iterator<Map<String, Object>> iterator = new Iterator<>() {
            private Map<String, Object> next = null;
            private boolean hasNext = false;
            private boolean closed = false;

            @Override
            public boolean hasNext() {
                if (closed) return false;
                if (hasNext) return true;
                
                try {
                    JsonToken token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY || token == null) {
                        close();
                        return false;
                    }
                    next = objectMapper.readValue(parser, Map.class);
                    hasNext = true;
                    return true;
                } catch (IOException e) {
                    close();
                    throw new RuntimeException("Error reading JSON", e);
                }
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) throw new NoSuchElementException();
                hasNext = false;
                return next;
            }

            private void close() {
                if (!closed) {
                    closed = true;
                    try {
                        parser.close();
                    } catch (IOException e) {
                        log.warn("Error closing JSON parser", e);
                    }
                }
            }
        };

        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
            false
        );
    }

    private JsonNode navigateJsonPath(JsonNode root, String jsonPath) {
        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");
        
        JsonNode current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current.has(part)) {
                current = current.get(part);
            } else {
                return root;
            }
        }
        return current;
    }
}
