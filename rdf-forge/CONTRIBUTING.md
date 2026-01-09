# Contributing to RDF Forge

Thank you for your interest in contributing to RDF Forge! This guide will help you get started.

## Quick Start

```bash
# Clone the repository
git clone https://github.com/your-org/rdf-forge.git
cd rdf-forge

# Start development environment
docker-compose -f docker-compose.standalone.yml up -d

# Build backend
mvn clean install -DskipTests

# Start frontend
cd rdf-forge-ui && npm install && npm run start
```

## Architecture

RDF Forge uses a microservices architecture:
- **rdf-forge-gateway** - API gateway (port 8000)
- **rdf-forge-pipeline-service** - Pipeline management
- **rdf-forge-job-service** - Job execution
- **rdf-forge-shacl-service** - SHACL validation
- **rdf-forge-data-service** - Data file management
- **rdf-forge-dimension-service** - Cube dimensions
- **rdf-forge-triplestore-service** - Triplestore connections
- **rdf-forge-engine** - Core ETL processing library
- **rdf-forge-ui** - Angular frontend

## Creating Custom Operations (Plugins)

The simplest way to extend RDF Forge is by creating custom pipeline operations.

### Step 1: Create Operation Class

```java
package io.rdfforge.engine.operation.custom;

import io.rdfforge.engine.operation.*;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MyCustomOperation implements Operation {

    @Override
    public String getId() {
        return "my-custom-op";
    }

    @Override
    public String getName() {
        return "My Custom Operation";
    }

    @Override
    public String getDescription() {
        return "Does something custom with the data";
    }

    @Override
    public OperationType getType() {
        return OperationType.TRANSFORM;  // SOURCE, TRANSFORM, CUBE, VALIDATION, OUTPUT
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "myParam", new ParameterSpec(
                "myParam", 
                "Description of parameter", 
                String.class, 
                true,   // required
                null    // default value
            )
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String myParam = (String) context.parameters().get("myParam");
        
        // Process the input stream or model
        // Return result with output stream/model
        
        return new OperationResult(
            true,                    // success
            context.inputStream(),   // output stream
            context.inputModel(),    // output model
            Map.of(),                // metadata
            null                     // error message
        );
    }
}
```

### Step 2: Register Automatically

Operations annotated with `@Component` are automatically discovered by Spring's component scanning. No additional configuration needed!

### Operation Types

| Type | Purpose | Example |
|------|---------|---------|
| `SOURCE` | Load data from external sources | LoadCsvOperation, HttpGetOperation |
| `TRANSFORM` | Transform data | MapToRdfOperation, FilterOperation |
| `CUBE` | Cube-specific operations | CubeBuilder (planned) |
| `VALIDATION` | Validate data | ShaclValidationOperation |
| `OUTPUT` | Write data to destinations | WriteRdfOperation |

## Creating Custom Destination Providers

You can add new publishing destinations (e.g., cloud storage, APIs).

### Step 1: Create Provider Class

```java
package io.rdfforge.pipeline.destination.providers;

import io.rdfforge.pipeline.destination.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MyCloudProvider implements DestinationProvider {

    @Override
    public DestinationInfo getDestinationInfo() {
        return new DestinationInfo(
            "my-cloud",                              // type
            "My Cloud Storage",                      // displayName
            "Publish RDF to My Cloud",               // description
            DestinationInfo.CATEGORY_CLOUD_STORAGE,  // category
            Map.of(
                "bucket", new DestinationInfo.ConfigField(
                    "bucket", "Bucket Name", "string", 
                    "Target bucket", true
                ),
                "apiKey", new DestinationInfo.ConfigField(
                    "apiKey", "API Key", "string",
                    "Authentication key", true, true  // sensitive
                )
            ),
            List.of(DestinationInfo.CAPABILITY_APPEND, DestinationInfo.CAPABILITY_REPLACE),
            List.of("turtle", "json-ld", "n-triples")
        );
    }

    @Override
    public PublishResult publish(Model model, Map<String, Object> config) {
        // Implement publishing logic
        return PublishResult.success(model.size(), null, Map.of());
    }

    @Override
    public void clearGraph(String graphUri, Map<String, Object> config) {
        // Implement clear logic
    }

    @Override
    public boolean isAvailable(Map<String, Object> config) {
        return true; // Check connectivity
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        if (!config.containsKey("bucket")) {
            return ValidationResult.invalid(List.of("Bucket is required"));
        }
        return ValidationResult.success();
    }
}
```

## Code Style

- Java 21+ features welcomed
- Use Lombok for boilerplate reduction
- Follow existing package structure
- Write unit tests for new operations

## Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-operation`
3. Implement your changes with tests
4. Run `mvn verify` to ensure tests pass
5. Submit a pull request

## Questions?

Open an issue on GitHub or check existing documentation in `/docs`.
