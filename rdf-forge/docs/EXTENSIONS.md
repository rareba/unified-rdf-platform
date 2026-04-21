# RDF Forge — Extensions / Plugins Guide

RDF Forge ships a set of runtime registries that each service populates with
implementations at startup. Every extension eventually surfaces in the
**Extension Catalog** UI (`/extensions`) so operators and integrators can see
what is installed and what is missing.

This document explains each extension kind, how to add a new implementation,
and the testing / packaging conventions.

---

## Extension kinds

| Kind                  | Registry class                                                         | Hosted in                  | Java interface / SPI                                             |
|-----------------------|------------------------------------------------------------------------|----------------------------|------------------------------------------------------------------|
| `OPERATION`           | `io.rdfforge.engine.operation.OperationRegistry`                       | `rdf-forge-engine` (booted in `pipeline-service`) | `io.rdfforge.engine.operation.Operation`                         |
| `FORMAT`              | `io.rdfforge.data.format.DataFormatRegistry`                           | `rdf-forge-data-service`   | `io.rdfforge.data.format.DataFormatHandler`                      |
| `STORAGE_PROVIDER`    | `io.rdfforge.data.storage.StorageProviderRegistry`                     | `rdf-forge-data-service`   | `io.rdfforge.data.storage.StorageProvider`                       |
| `DESTINATION`         | `io.rdfforge.pipeline.destination.DestinationRegistry`                 | `rdf-forge-pipeline-service` | `io.rdfforge.pipeline.destination.DestinationProvider`          |
| `TRIPLESTORE_PROVIDER`| `io.rdfforge.triplestore.connector.TriplestoreProviderRegistry`        | `rdf-forge-triplestore-service` | `io.rdfforge.triplestore.connector.TriplestoreProvider`     |
| `MATCHER`             | Phase 8 matcher registry (optional, under `triplestore-service`)       | `rdf-forge-triplestore-service` | Phase-8 matcher interface                                  |
| `VALIDATOR`           | `io.rdfforge.shacl.service.ProfileValidationService`                   | `rdf-forge-shacl-service`  | Profile config resource (TTL bundle)                             |
| `CUBE_PROFILE`        | alias of VALIDATOR for cube-link profiles                              | `rdf-forge-shacl-service`  | same                                                             |

Each registry publishes its entries as **`ExtensionDescriptor`** records at
`GET /api/v1/extensions/<kind>` on the owning service. The auth-service
`MetaController` fans out and returns the union at
`GET /api/v1/admin/extensions`.

---

## Adding a new extension

### 1. OPERATION (pipeline steps)

Operations are the unit of work in a pipeline — sources, transforms, cube
builders, validations, outputs.

1. Implement `io.rdfforge.engine.operation.Operation`.
2. Annotate with `@Component` and (optionally) `@PluginInfo` for metadata.
3. Drop the class anywhere on the classpath of `rdf-forge-engine` (or as
   a runtime dependency of `pipeline-service`). `OperationRegistry.init()`
   auto-discovers every `Operation` bean.

```java
@Component
@PluginInfo(author = "acme", version = "1.2.0", tags = {"transform","csv"},
            documentation = "https://acme.example.com/docs/custom-split")
public class CustomSplitOperation implements Operation {
    @Override public String getId() { return "acme.csv-split"; }
    @Override public String getName() { return "CSV Split"; }
    @Override public String getDescription() { return "Splits one CSV row into many"; }
    @Override public OperationType getType() { return OperationType.TRANSFORM; }
    @Override public Map<String, ParameterSpec> getParameters() { return Map.of(
        "separator", new ParameterSpec("separator", "Column delimiter", String.class, false, ",")
    ); }
    @Override public OperationResult execute(OperationContext ctx) { /* ... */ }
}
```

**Lifecycle hooks:** none beyond the standard Spring bean lifecycle.
`OperationRegistry.register()` is called during its `@PostConstruct`.

### 2. FORMAT (data format handlers)

Implement `DataFormatHandler` in `rdf-forge-data-service`:

```java
@Component
public class AvroFormatHandler implements DataFormatHandler {
    public DataFormatInfo getFormatInfo() {
        return new DataFormatInfo("avro", "Avro", "Apache Avro",
            "application/avro", List.of("avro"), true, true, true,
            Map.of(), List.of(CAPABILITY_READ, CAPABILITY_SCHEMA_INFERENCE));
    }
    // parse / preview / analyze methods
}
```

`DataFormatRegistry.init()` auto-registers the bean and wires extension/MIME
lookups. Use `available=false` in `DataFormatInfo` to advertise a format as
"coming soon" without silently accepting uploads.

### 3. STORAGE_PROVIDER (object storage)

Implement `StorageProvider` in `rdf-forge-data-service`:

```java
@Component
@ConditionalOnProperty(prefix = "storage.acme", name = "enabled", havingValue = "true")
public class AcmeStorageProvider implements StorageProvider { /* ... */ }
```

Use `@ConditionalOnProperty` so admins can enable/disable providers via
configuration.

Activate one provider at runtime via `storage.provider=<type>` (see
`application.yml`).

### 4. DESTINATION (publish targets)

Implement `DestinationProvider` in `rdf-forge-pipeline-service`:

```java
@Component
public class SolidPodDestination implements DestinationProvider { /* ... */ }
```

`DestinationRegistry` groups them by `category()` (triplestore / file /
cloud-storage / api / ci-cd).

### 5. TRIPLESTORE_PROVIDER

Implement `TriplestoreProvider` in `rdf-forge-triplestore-service`:

```java
@Component
public class VirtuosoProvider implements TriplestoreProvider {
    public boolean supports(TriplestoreConnectionEntity.TriplestoreType t) {
        return t == TriplestoreConnectionEntity.TriplestoreType.VIRTUOSO;
    }
    public TriplestoreConnector createConnector(TriplestoreConnectionEntity conn) {
        return new VirtuosoConnector(conn);
    }
}
```

### 6. MATCHER (Phase 8)

Phase-8 TODO: once the matcher SPI lands, matchers will be discovered the
same way via `@Component` beans implementing the matcher interface.

### 7. VALIDATOR / CUBE_PROFILE

Add a new TTL profile to `rdf-forge-shacl-service/src/main/resources/profiles/`
and register the id in `ProfileValidationService.AVAILABLE_PROFILES`. The
profile will be loaded on startup and exposed at
`/api/v1/extensions/validators`.

---

## Testing conventions

Every plugin SHOULD ship a JUnit 5 test in the owning service's `src/test/java`:

```java
@SpringBootTest
class AvroFormatHandlerTest {
    @Autowired DataFormatRegistry registry;

    @Test void isRegistered() {
        assertThat(registry.isSupported("avro")).isTrue();
    }

    @Test void previewReturnsRows() { /* ... */ }
}
```

Use `@ConditionalOnProperty` + a Spring profile (`--spring.profiles.active=avro`)
if the plugin has heavy runtime requirements.

---

## Packaging and distribution

Currently every plugin ships in-tree as part of its service module. To add
plugins as separate artifacts without rebuilding services, the recommended
approach is:

1. Publish a jar containing the plugin `@Component` classes.
2. Add it as a Maven dependency of the owning service (`pipeline-service`
   for operations, `data-service` for formats / storage, etc.).
3. Rebuild that service image (`mvn -pl rdf-forge-pipeline-service package`).
4. Spring Boot component-scan picks up the plugin at startup.

**Classpath / isolated plugin loading** (hot-swappable without rebuild) is a
Phase-9 follow-up — see the TODO in `OperationRegistry.register()`. It will
require a dedicated `PluginClassLoader` and a manifest describing the set of
beans to register.

---

## Catalog API

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/extensions/operations`            | Operations registered in pipeline-service |
| `GET /api/v1/extensions/formats`               | Data formats in data-service |
| `GET /api/v1/extensions/storage-providers`     | Storage providers in data-service |
| `GET /api/v1/extensions/destinations`          | Destinations in pipeline-service |
| `GET /api/v1/extensions/triplestore-providers` | Triplestore providers in triplestore-service |
| `GET /api/v1/extensions/matchers`              | Phase-8 matchers (may be empty) |
| `GET /api/v1/extensions/validators`            | SHACL cube profiles |
| `GET /api/v1/extensions/cube-profiles`         | Cube-link profiles (alias) |
| `GET /api/v1/admin/extensions`                 | Aggregated list across every service |
| `GET /api/v1/admin/extensions?kind=FORMAT`     | Filter aggregated list |
| `GET /api/v1/admin/extensions/summary`         | Counts per kind |

Every endpoint returns `List<ExtensionDescriptor>` with the shape:

```json
{
  "id": "csv",
  "kind": "FORMAT",
  "name": "CSV",
  "version": "1.0",
  "description": "Comma-separated values",
  "capabilities": ["preview","analyze","streaming","ext:csv"],
  "parameters": {"delimiter": "string — Column delimiter default=,"},
  "providedBy": "rdf-forge-data-service",
  "docUrl": null,
  "available": true
}
```

---

## See also

- `docs/plugin-development.md` — legacy plugin notes (merged into this guide)
- `rdf-forge-engine/src/main/java/io/rdfforge/engine/operation/OperationRegistry.java`
- `rdf-forge-data-service/src/main/java/io/rdfforge/data/format/DataFormatRegistry.java`
- `rdf-forge-data-service/src/main/java/io/rdfforge/data/storage/StorageProviderRegistry.java`
- `rdf-forge-pipeline-service/src/main/java/io/rdfforge/pipeline/destination/DestinationRegistry.java`
- `rdf-forge-triplestore-service/src/main/java/io/rdfforge/triplestore/connector/TriplestoreProviderRegistry.java`
