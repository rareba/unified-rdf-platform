# Plugin Development Guide

This guide explains how to create custom operations and destination providers for RDF Forge.

## Operation Plugin System

RDF Forge uses Spring's component scanning to automatically discover operations. Any class that:
1. Implements `io.rdfforge.engine.operation.Operation`
2. Is annotated with `@Component`

Will be automatically registered and available in the Pipeline Designer.

## Creating an Operation

### Interface Methods

```java
public interface Operation {
    String getId();              // Unique identifier (e.g., "my-operation")
    String getName();            // Display name (e.g., "My Operation")
    String getDescription();     // Description for UI
    OperationType getType();     // Category in palette
    Map<String, ParameterSpec> getParameters();  // Configuration schema
    OperationResult execute(OperationContext context) throws OperationException;
}
```

### Parameter Specification

```java
record ParameterSpec(
    String name,           // Parameter key
    String description,    // Help text
    Class<?> type,         // Java type (String.class, Integer.class, Map.class)
    boolean required,      // Is required?
    Object defaultValue    // Default if not provided
) {}
```

### Operation Context

Your operation receives:
- `parameters` - User-configured values
- `inputStream` - Previous operation's output (for transforms)
- `inputModel` - RDF Model from previous step
- `variables` - Pipeline-level variables
- `callback` - For progress reporting

### Operation Result

Return:
- `success` - Did it work?
- `outputStream` - Data stream for next operation
- `outputModel` - RDF Model for next operation
- `metadata` - Metrics (rows processed, etc.)
- `errorMessage` - Error details if failed

## Complete Example: CSV Filter Operation

```java
package io.rdfforge.engine.operation.transform;

import io.rdfforge.engine.operation.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Stream;

@Component
public class FilterOperation implements Operation {

    @Override
    public String getId() { return "filter"; }

    @Override
    public String getName() { return "Filter Rows"; }

    @Override
    public String getDescription() { 
        return "Filter rows based on column value"; 
    }

    @Override
    public OperationType getType() { return OperationType.TRANSFORM; }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "column", new ParameterSpec("column", "Column to filter on", 
                String.class, true, null),
            "operator", new ParameterSpec("operator", "Comparison operator", 
                String.class, true, "equals"),
            "value", new ParameterSpec("value", "Value to compare", 
                String.class, true, null)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult execute(OperationContext context) 
            throws OperationException {
        
        String column = (String) context.parameters().get("column");
        String operator = (String) context.parameters().get("operator");
        String value = (String) context.parameters().get("value");

        Stream<?> input = context.inputStream();
        if (input == null) {
            throw new OperationException(getId(), "No input stream");
        }

        Stream<?> filtered = input.filter(item -> {
            if (item instanceof Map) {
                Map<String, Object> row = (Map<String, Object>) item;
                Object cellValue = row.get(column);
                return matches(cellValue, operator, value);
            }
            return true;
        });

        return new OperationResult(true, filtered, null, Map.of(), null);
    }

    private boolean matches(Object cellValue, String operator, String value) {
        if (cellValue == null) return false;
        String strValue = cellValue.toString();
        return switch (operator) {
            case "equals" -> strValue.equals(value);
            case "contains" -> strValue.contains(value);
            case "startsWith" -> strValue.startsWith(value);
            default -> true;
        };
    }
}
```

## Operation Categories

| Type | Icon in UI | Purpose |
|------|------------|---------|
| SOURCE | Database icon | Load data (CSV, JSON, HTTP, S3) |
| TRANSFORM | Arrows icon | Transform data structure |
| CUBE | Cube icon | RDF Data Cube operations |
| VALIDATION | Checkmark | SHACL/custom validation |
| OUTPUT | Export icon | Write to destination |

## Testing Your Operation

```java
@SpringBootTest
class MyOperationTest {
    
    @Autowired
    private OperationRegistry registry;
    
    @Test
    void operationRegistered() {
        assertThat(registry.get("my-operation")).isPresent();
    }
    
    @Test
    void executesCorrectly() throws OperationException {
        Operation op = registry.getOrThrow("my-operation");
        
        OperationContext ctx = new OperationContext(
            Map.of("param1", "value1"),
            Stream.of(Map.of("col1", "data")),
            null,
            Map.of(),
            null
        );
        
        OperationResult result = op.execute(ctx);
        assertThat(result.success()).isTrue();
    }
}
```

## Destination Providers

See [CONTRIBUTING.md](./CONTRIBUTING.md) for destination provider examples.

## API Endpoints

Once registered, operations appear in:
- `GET /api/v1/operations` - List all operations
- `GET /api/v1/operations/{id}` - Get operation details
- Pipeline Designer UI - Automatically in operations palette
