# Feature Translation Guide: Zazuko Tools to Java Spring Boot/Angular

This document provides a comprehensive analysis of Zazuko's cube-creator, barnard59, cube-validator, and cube-link tools, and how their features should be implemented in the RDF Forge Java Spring Boot/Angular platform.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Cube Creator Feature Analysis](#2-cube-creator-feature-analysis)
3. [Barnard59 Feature Analysis](#3-barnard59-feature-analysis)
4. [Cube-Link (Cube Schema) Feature Analysis](#4-cube-link-cube-schema-feature-analysis)
5. [Cube Validator Feature Analysis](#5-cube-validator-feature-analysis)
6. [Current RDF Forge Implementation Status](#6-current-rdf-forge-implementation-status)
7. [Gap Analysis](#7-gap-analysis)
8. [Implementation Roadmap](#8-implementation-roadmap)
9. [API Mapping](#9-api-mapping)
10. [Database Schema Additions](#10-database-schema-additions)

---

## 1. Executive Summary

### Source Projects Overview

| Project | Purpose | Technology | License |
|---------|---------|------------|---------|
| **cube-creator** | Web UI for creating RDF cubes from CSV | TypeScript, Vue.js, Hydra API | AGPL-3.0 |
| **barnard59** | RDF pipeline ETL toolkit | JavaScript/Node.js | MIT |
| **cube-link** | Cube Schema specification + SHACL shapes | RDF/SHACL | CC-BY-4.0 |
| **rdf-validate-shacl** | SHACL validation library | JavaScript | MIT |

### Target Platform

| Component | Technology |
|-----------|-----------|
| Backend | Java 21, Spring Boot 3.2.5 |
| RDF Processing | Apache Jena 5.0.0 |
| ETL Engine | Custom PipelineExecutor + Apache Camel 4.5.0 |
| Frontend | Angular 20, Material Design |
| Database | PostgreSQL 16 + Flyway |
| Identity | Keycloak 24.0 |
| Object Storage | MinIO |

---

## 2. Cube Creator Feature Analysis

### 2.1 Core Features

#### 2.1.1 Project Management
- **Zazuko**: Projects contain sources, cubes, and metadata
- **RDF Forge Translation**:
  ```java
  // Existing: ProjectEntity in common module
  // Status: PARTIALLY IMPLEMENTED
  // Gap: Need project-level access control
  ```

#### 2.1.2 CSV Source Management

**Zazuko Feature:**
- Upload CSV files
- Column type detection (string, integer, date, etc.)
- Sample data preview
- Encoding detection

**RDF Forge Translation:**
```java
// Service: rdf-forge-data-service
// Controller: DataController.java

@PostMapping("/api/v1/data/upload")
public ResponseEntity<DataSourceDto> uploadFile(
    @RequestParam("file") MultipartFile file,
    @RequestParam(required = false) String encoding,
    @RequestParam(required = false) String delimiter
);

@GetMapping("/api/v1/data/{id}/preview")
public ResponseEntity<DataPreviewDto> preview(
    @PathVariable UUID id,
    @RequestParam(defaultValue = "100") int limit
);

@GetMapping("/api/v1/data/{id}/columns")
public ResponseEntity<List<ColumnInfoDto>> getColumns(@PathVariable UUID id);
```

**ColumnInfoDto:**
```java
public record ColumnInfoDto(
    String name,
    String detectedType,      // STRING, INTEGER, DECIMAL, DATE, DATETIME, BOOLEAN
    int nullCount,
    int uniqueCount,
    List<String> sampleValues,
    boolean isRequired
) {}
```

**Implementation Status:** PARTIALLY IMPLEMENTED
**Gap:** Column type detection, encoding detection

#### 2.1.3 Column Mapping

**Zazuko Feature:**
- Map CSV columns to RDF properties
- Define column roles: Dimension, Measure, Attribute
- URI template configuration for resources
- Datatype assignment

**RDF Forge Translation:**
```java
// Entity: CubeEntity.metadata field (JSONB)
// Current structure in cube-wizard.ts

interface ColumnMapping {
  columnName: string;
  property: string;           // RDF property URI
  role: 'dimension' | 'measure' | 'attribute' | 'identifier';
  datatype?: string;          // xsd:string, xsd:integer, etc.
  uriTemplate?: string;       // For IRI generation
  isRequired: boolean;
  defaultValue?: string;
}
```

**Backend Service:**
```java
@Service
public class ColumnMappingService {

    public List<ColumnMapping> detectMappings(DataSource source) {
        // Analyze column names, suggest RDF properties
        // Detect appropriate roles based on data types
    }

    public String generateUriTemplate(String columnName, String baseUri) {
        // Generate URI template like: baseUri + "/{columnName}"
    }

    public void validateMappings(List<ColumnMapping> mappings, CubeConstraint constraint) {
        // Validate mappings against cube constraint
    }
}
```

**Implementation Status:** PARTIALLY IMPLEMENTED (metadata JSONB exists)
**Gap:** Mapping suggestion engine, URI template builder UI

#### 2.1.4 Dimension Management

**Zazuko Feature:**
- Shared dimensions across cubes
- Dimension hierarchies
- Dimension values with labels
- Time dimension support

**RDF Forge Translation:**
```java
// Service: rdf-forge-dimension-service
// Entities: DimensionEntity, DimensionValueEntity, HierarchyEntity

@Entity
@Table(name = "dimensions")
public class DimensionEntity {
    UUID id;
    UUID projectId;
    String uri;                    // RDF URI
    String name;
    String description;
    DimensionType type;            // REGULAR, TIME, GEO, CODED
    String rangeClass;             // Target class for values
    @OneToMany
    List<DimensionValueEntity> values;
    @OneToMany
    List<HierarchyEntity> hierarchies;
}

public enum DimensionType {
    REGULAR,      // Standard dimension
    TIME,         // Temporal dimension (xsd:date, etc.)
    GEO,          // Geographic dimension
    CODED         // Coded list (SKOS concepts)
}
```

**Implementation Status:** IMPLEMENTED
**Gap:** Time dimension special handling, Geographic dimension support

#### 2.1.5 Cube Metadata Editor

**Zazuko Feature:**
- Cube title, description in multiple languages
- Publisher, creator, license
- Temporal/spatial coverage
- Keywords/themes
- Contact information

**RDF Forge Translation:**
```java
// CubeEntity.metadata JSONB structure

{
    "titles": {
        "en": "Swiss Population by Canton",
        "de": "Schweizer Bevölkerung nach Kanton"
    },
    "descriptions": {
        "en": "Population statistics...",
        "de": "Bevölkerungsstatistik..."
    },
    "publisher": {
        "uri": "https://example.org/org/bfs",
        "name": "Federal Statistical Office"
    },
    "creator": {...},
    "license": "https://creativecommons.org/licenses/by/4.0/",
    "temporalCoverage": {
        "start": "2020-01-01",
        "end": "2023-12-31"
    },
    "spatialCoverage": ["Switzerland"],
    "keywords": ["population", "demographics"],
    "themes": ["http://publications.europa.eu/resource/authority/data-theme/SOCI"],
    "contactPoint": {
        "name": "Data Team",
        "email": "data@example.org"
    },
    "version": "1.0.0",
    "versionNotes": "Initial release"
}
```

**Implementation Status:** PARTIALLY IMPLEMENTED
**Gap:** Multi-language UI support, full DCAT metadata fields

### 2.2 Pipeline Features

#### 2.2.1 Transform Pipeline

**Zazuko Feature:**
- CSVW mapping for transformation
- Pipeline execution with progress
- Error handling and logs

**RDF Forge Translation:**
```java
// Service: rdf-forge-engine

// CsvwMappingOperation - Parse CSVW metadata and apply to CSV
@Operation(type = OperationType.TRANSFORM, name = "csvw-mapping")
public class CsvwMappingOperation implements Operation {
    @Override
    public OperationResult execute(OperationContext context) {
        String csvwMetadata = (String) context.parameters().get("csvwMetadata");
        Stream<Row> input = (Stream<Row>) context.inputStream();

        // Apply CSVW transformations
        // Generate RDF triples
        return new OperationResult(true, rdfStream, model, metadata, null);
    }
}
```

**Implementation Status:** PARTIALLY IMPLEMENTED
**Gap:** Full CSVW specification support, CSVW metadata editor

#### 2.2.2 Publish Pipeline

**Zazuko Feature:**
- Publish to SPARQL endpoints
- Graph Store Protocol support
- Named graph management

**RDF Forge Translation:**
```java
// Already implemented: GraphStorePutOperation
// Service: rdf-forge-triplestore-service

@Operation(type = OperationType.OUTPUT, name = "graph-store-put")
public class GraphStorePutOperation implements Operation {
    @Override
    public OperationResult execute(OperationContext context) {
        String triplestoreId = (String) context.parameters().get("triplestoreId");
        String graphUri = (String) context.parameters().get("graphUri");
        Model model = context.inputModel();

        // Use Graph Store Protocol to publish
        triplestoreService.publishGraph(triplestoreId, graphUri, model);

        return OperationResult.success(metadata);
    }
}
```

**Implementation Status:** IMPLEMENTED

---

## 3. Barnard59 Feature Analysis

### 3.1 Pipeline Architecture

**Zazuko Barnard59:**
- Pipelines defined in RDF (Turtle)
- Stream-based processing
- Modular operations (steps)
- Unix-like pipeline chaining

**RDF Forge Translation:**
- Pipelines defined in JSON/YAML (stored in PostgreSQL)
- Apache Jena Model-based processing
- Modular Operation interface
- DAG-based execution with PipelineExecutor

### 3.2 Core Packages Mapping

| Barnard59 Package | RDF Forge Equivalent | Status |
|-------------------|---------------------|--------|
| barnard59-core | rdf-forge-engine/pipeline | IMPLEMENTED |
| barnard59-base | rdf-forge-engine/operation | IMPLEMENTED |
| barnard59-formats | rdf-forge-engine/format | PARTIAL |
| barnard59-rdf | rdf-forge-engine/rdf | IMPLEMENTED |
| barnard59-cube | rdf-forge-engine/cube | PARTIAL |
| barnard59-shacl | rdf-forge-shacl-service | IMPLEMENTED |
| barnard59-sparql | rdf-forge-triplestore-service | IMPLEMENTED |
| barnard59-graph-store | rdf-forge-triplestore-service | IMPLEMENTED |
| barnard59-ftp | NOT IMPLEMENTED | GAP |
| barnard59-http | rdf-forge-engine/http | IMPLEMENTED |
| barnard59-s3 | rdf-forge-engine/s3 | IMPLEMENTED |

### 3.3 Barnard59-Cube Operations

#### 3.3.1 toObservation

**Zazuko:**
```javascript
// Transforms input chunks to cube:Observation instances
// Adds rdf:type cube:Observation
// Generates observation URIs
// Validates dimension completeness
```

**RDF Forge Implementation:**
```java
// rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/CreateObservationOperation.java

@Operation(
    type = OperationType.CUBE,
    name = "create-observation",
    description = "Create RDF Cube observations from tabular data"
)
@PluginInfo(
    author = "RDF Forge",
    version = "1.0.0",
    tags = {"cube", "observation", "rdf"},
    builtIn = true
)
public class CreateObservationOperation implements Operation {

    private static final String CUBE_NS = "https://cube.link/";
    private static final Resource OBSERVATION_TYPE =
        ResourceFactory.createResource(CUBE_NS + "Observation");

    @Override
    public OperationResult execute(OperationContext context) {
        String cubeUri = (String) context.parameters().get("cubeUri");
        String observationSetUri = (String) context.parameters().get("observationSetUri");
        List<Map<String, Object>> columnMappings =
            (List<Map<String, Object>>) context.parameters().get("columnMappings");
        String observationUriTemplate =
            (String) context.parameters().getOrDefault("observationUriTemplate",
                cubeUri + "/observation/{_rowIndex}");

        Model model = ModelFactory.createDefaultModel();
        Stream<Map<String, Object>> rows = (Stream<Map<String, Object>>) context.inputStream();

        AtomicInteger rowIndex = new AtomicInteger(0);

        rows.forEach(row -> {
            // Generate observation URI
            String obsUri = processUriTemplate(observationUriTemplate, row, rowIndex.get());
            Resource observation = model.createResource(obsUri);

            // Add observation type
            model.add(observation, RDF.type, OBSERVATION_TYPE);

            // Add to observation set
            Resource obsSet = model.createResource(observationSetUri);
            model.add(obsSet, model.createProperty(CUBE_NS, "observation"), observation);

            // Process each column mapping
            for (Map<String, Object> mapping : columnMappings) {
                String columnName = (String) mapping.get("columnName");
                String propertyUri = (String) mapping.get("property");
                String datatype = (String) mapping.get("datatype");

                Object value = row.get(columnName);
                if (value != null) {
                    Property prop = model.createProperty(propertyUri);
                    RDFNode valueNode = createTypedLiteral(model, value, datatype);
                    model.add(observation, prop, valueNode);
                }
            }

            rowIndex.incrementAndGet();
        });

        return new OperationResult(true, null, model,
            Map.of("observationCount", rowIndex.get()), null);
    }

    private RDFNode createTypedLiteral(Model model, Object value, String datatype) {
        if (datatype != null && datatype.startsWith("http")) {
            return model.createTypedLiteral(value.toString(),
                TypeMapper.getInstance().getSafeTypeByName(datatype));
        }
        return model.createLiteral(value.toString());
    }
}
```

**Implementation Status:** IMPLEMENTED
**Gap:** URI template variable support, undefined value handling

#### 3.3.2 buildCubeShape

**Zazuko:**
```javascript
// Analyzes stream of observations
// Collects dimension/measure metadata
// Generates SHACL shape describing the cube
```

**RDF Forge Implementation:**
```java
// NEW: rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/BuildCubeShapeOperation.java

@Operation(
    type = OperationType.CUBE,
    name = "build-cube-shape",
    description = "Build SHACL shape from observation stream"
)
public class BuildCubeShapeOperation implements Operation {

    @Override
    public OperationResult execute(OperationContext context) {
        Model observations = context.inputModel();
        String cubeUri = (String) context.parameters().get("cubeUri");
        String constraintUri = (String) context.parameters().getOrDefault(
            "constraintUri", cubeUri + "/constraint");

        // Analyze observations to extract structure
        Map<String, PropertyStats> propertyStats = new HashMap<>();

        // Query all observations
        String query = """
            PREFIX cube: <https://cube.link/>
            SELECT ?obs ?prop ?value WHERE {
                ?obs a cube:Observation ;
                     ?prop ?value .
                FILTER(?prop != rdf:type)
            }
            """;

        QueryExecution qe = QueryExecutionFactory.create(query, observations);
        ResultSet results = qe.execSelect();

        while (results.hasNext()) {
            QuerySolution sol = results.next();
            String prop = sol.get("prop").asResource().getURI();
            RDFNode value = sol.get("value");

            propertyStats.computeIfAbsent(prop, k -> new PropertyStats())
                .addValue(value);
        }

        // Generate SHACL shape
        Model shapeModel = generateShaclShape(constraintUri, propertyStats);

        return new OperationResult(true, null, shapeModel,
            Map.of("propertyCount", propertyStats.size()), null);
    }

    private Model generateShaclShape(String constraintUri, Map<String, PropertyStats> stats) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("sh", SHACL.NS);
        model.setNsPrefix("cube", "https://cube.link/");

        Resource shape = model.createResource(constraintUri);
        model.add(shape, RDF.type, SHACL.NodeShape);
        model.add(shape, RDF.type,
            model.createResource("https://cube.link/Constraint"));
        model.add(shape, SHACL.targetClass,
            model.createResource("https://cube.link/Observation"));

        for (Map.Entry<String, PropertyStats> entry : stats.entrySet()) {
            Resource propShape = model.createResource();
            model.add(shape, SHACL.property, propShape);
            model.add(propShape, SHACL.path, model.createResource(entry.getKey()));
            model.add(propShape, SHACL.minCount, model.createTypedLiteral(1));
            model.add(propShape, SHACL.maxCount, model.createTypedLiteral(1));

            PropertyStats pstats = entry.getValue();
            if (pstats.hasConsistentDatatype()) {
                model.add(propShape, SHACL.datatype,
                    model.createResource(pstats.getDatatype()));
            }
            if (pstats.hasEnumeratedValues() && pstats.getUniqueCount() < 50) {
                // Add sh:in for small value sets
                RDFList valueList = model.createList(
                    pstats.getValues().stream()
                        .map(v -> (RDFNode) model.createLiteral(v))
                        .iterator()
                );
                model.add(propShape, SHACL.in, valueList);
            }
        }

        return model;
    }
}

class PropertyStats {
    private Set<String> datatypes = new HashSet<>();
    private Set<String> values = new HashSet<>();
    private int count = 0;

    void addValue(RDFNode value) {
        count++;
        if (value.isLiteral()) {
            Literal lit = value.asLiteral();
            datatypes.add(lit.getDatatypeURI());
            values.add(lit.getString());
        }
    }

    boolean hasConsistentDatatype() {
        return datatypes.size() == 1;
    }

    String getDatatype() {
        return datatypes.iterator().next();
    }

    boolean hasEnumeratedValues() {
        return values.size() < 100;
    }

    int getUniqueCount() {
        return values.size();
    }

    Set<String> getValues() {
        return values;
    }
}
```

**Implementation Status:** NOT IMPLEMENTED
**Priority:** HIGH

#### 3.3.3 Fetch Operations

**Zazuko:**
- `fetch-cube` - Retrieve complete cube from SPARQL
- `fetch-metadata` - Retrieve only cube metadata
- `fetch-observations` - Retrieve only observations
- `fetch-constraint` - Retrieve constraint shape

**RDF Forge Implementation:**
```java
// NEW: rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/FetchCubeOperation.java

@Operation(type = OperationType.SOURCE, name = "fetch-cube")
public class FetchCubeOperation implements Operation {

    @Override
    public OperationResult execute(OperationContext context) {
        String sparqlEndpoint = (String) context.parameters().get("sparqlEndpoint");
        String cubeUri = (String) context.parameters().get("cubeUri");
        String graphUri = (String) context.parameters().get("graphUri");

        String query = String.format("""
            CONSTRUCT {
                <%s> ?p ?o .
                ?obsSet ?op ?oo .
                ?obs ?obsp ?obsv .
            } WHERE {
                GRAPH <%s> {
                    <%s> ?p ?o .
                    OPTIONAL {
                        <%s> <https://cube.link/observationSet> ?obsSet .
                        ?obsSet ?op ?oo .
                        OPTIONAL {
                            ?obsSet <https://cube.link/observation> ?obs .
                            ?obs ?obsp ?obsv .
                        }
                    }
                }
            }
            """, cubeUri, graphUri, cubeUri, cubeUri);

        Model result = executeConstruct(sparqlEndpoint, query);

        return new OperationResult(true, null, result,
            Map.of("tripleCount", result.size()), null);
    }
}

@Operation(type = OperationType.SOURCE, name = "fetch-metadata")
public class FetchMetadataOperation implements Operation {
    // Fetch cube without observations
}

@Operation(type = OperationType.SOURCE, name = "fetch-observations")
public class FetchObservationsOperation implements Operation {
    // Fetch only observations
}

@Operation(type = OperationType.SOURCE, name = "fetch-constraint")
public class FetchConstraintOperation implements Operation {
    // Fetch constraint shape
}
```

**Implementation Status:** NOT IMPLEMENTED
**Priority:** MEDIUM

### 3.4 Barnard59-Formats

#### 3.4.1 Format Parsers

| Format | Zazuko | RDF Forge Status |
|--------|--------|-----------------|
| CSV | csvw.js | LoadCsvOperation (basic) |
| CSVW | csvw.js | CsvwParseOperation (partial) |
| JSON-LD | jsonld.js | JsonLdParseOperation |
| N-Triples | ntriples.js | Built into Jena |
| Turtle | n3.js | Built into Jena |
| RDF/XML | rdf-xml.js | Built into Jena |
| XLSX | xlsx.js | NOT IMPLEMENTED |

**XLSX Support Implementation:**
```java
// NEW: rdf-forge-engine/src/main/java/io/rdfforge/engine/format/XlsxParseOperation.java

@Operation(type = OperationType.SOURCE, name = "load-xlsx")
public class XlsxParseOperation implements Operation {

    @Override
    public OperationResult execute(OperationContext context) {
        String sourceId = (String) context.parameters().get("sourceId");
        String sheetName = (String) context.parameters().get("sheetName");
        int headerRow = (int) context.parameters().getOrDefault("headerRow", 0);

        try (InputStream is = dataService.getFileStream(sourceId)) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = sheetName != null
                ? workbook.getSheet(sheetName)
                : workbook.getSheetAt(0);

            // Extract header row
            Row headers = sheet.getRow(headerRow);
            List<String> columnNames = new ArrayList<>();
            for (Cell cell : headers) {
                columnNames.add(cell.getStringCellValue());
            }

            // Stream data rows
            Stream<Map<String, Object>> rows = StreamSupport
                .stream(sheet.spliterator(), false)
                .skip(headerRow + 1)
                .map(row -> rowToMap(row, columnNames));

            return new OperationResult(true, rows, null,
                Map.of("columns", columnNames), null);
        }
    }
}
```

**Implementation Status:** CSV, JSON-LD, RDF formats implemented; XLSX NOT IMPLEMENTED
**Priority:** MEDIUM

---

## 4. Cube-Link (Cube Schema) Feature Analysis

### 4.1 Core Vocabulary

**Namespace:** `https://cube.link/`

| Class | Description | RDF Forge Usage |
|-------|-------------|-----------------|
| `cube:Cube` | Container for observation sets | CubeEntity.uri |
| `cube:ObservationSet` | Group of observations | Generated in CreateObservationOperation |
| `cube:Observation` | Single data point | Generated per row |
| `cube:Constraint` | SHACL shape for validation | ShapeEntity |

### 4.2 Property Types

**Key Properties:**
```turtle
cube:observationSet       # Links Cube to ObservationSet
cube:observation          # Links ObservationSet to Observation
cube:observationConstraint # Links Cube to Constraint
cube:observedBy           # Agent creating observations
```

**Dimension Classes (UX Extension):**
```java
// NEW: rdf-forge-common/src/main/java/io/rdfforge/common/model/DimensionRole.java

public enum DimensionRole {
    KEY_DIMENSION("https://cube.link/KeyDimension"),
    MEASURE_DIMENSION("https://cube.link/MeasureDimension"),
    ATTRIBUTE("https://schema.org/Property");

    private final String uri;

    DimensionRole(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
```

### 4.3 SHACL Constraint Patterns

**cube:Constraint Structure:**
```turtle
ex:constraint a cube:Constraint, sh:NodeShape ;
    sh:targetClass cube:Observation ;
    sh:property [
        sh:path ex:dimension1 ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:in ( "value1" "value2" "value3" ) ;
    ] ;
    sh:property [
        sh:path ex:measure1 ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:decimal ;
    ] .
```

**RDF Forge ConstraintBuilder:**
```java
// NEW: rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/ConstraintBuilder.java

public class ConstraintBuilder {

    private final Model model;
    private final Resource constraint;
    private final List<Resource> properties = new ArrayList<>();

    public ConstraintBuilder(String constraintUri) {
        this.model = ModelFactory.createDefaultModel();
        model.setNsPrefix("cube", "https://cube.link/");
        model.setNsPrefix("sh", SHACL.NS);
        model.setNsPrefix("schema", "https://schema.org/");

        this.constraint = model.createResource(constraintUri);
        model.add(constraint, RDF.type,
            model.createResource("https://cube.link/Constraint"));
        model.add(constraint, RDF.type, SHACL.NodeShape);
        model.add(constraint, SHACL.targetClass,
            model.createResource("https://cube.link/Observation"));
    }

    public ConstraintBuilder addDimension(DimensionSpec spec) {
        Resource propShape = model.createResource();
        model.add(constraint, SHACL.property, propShape);
        model.add(propShape, SHACL.path, model.createResource(spec.propertyUri()));
        model.add(propShape, SHACL.minCount, model.createTypedLiteral(1));
        model.add(propShape, SHACL.maxCount, model.createTypedLiteral(1));

        // Add dimension role
        if (spec.role() == DimensionRole.KEY_DIMENSION) {
            model.add(model.createResource(spec.propertyUri()), RDF.type,
                model.createResource("https://cube.link/KeyDimension"));
        } else if (spec.role() == DimensionRole.MEASURE_DIMENSION) {
            model.add(model.createResource(spec.propertyUri()), RDF.type,
                model.createResource("https://cube.link/MeasureDimension"));
        }

        // Add datatype constraint
        if (spec.datatype() != null) {
            model.add(propShape, SHACL.datatype,
                model.createResource(spec.datatype()));
        }

        // Add value enumeration for coded dimensions
        if (spec.allowedValues() != null && !spec.allowedValues().isEmpty()) {
            RDFList values = model.createList(
                spec.allowedValues().stream()
                    .map(v -> (RDFNode) model.createLiteral(v))
                    .iterator()
            );
            model.add(propShape, SHACL.in, values);
        }

        // Add metadata
        Resource prop = model.createResource(spec.propertyUri());
        if (spec.name() != null) {
            model.add(prop,
                model.createProperty("https://schema.org/", "name"),
                model.createLiteral(spec.name()));
        }
        if (spec.description() != null) {
            model.add(prop,
                model.createProperty("https://schema.org/", "description"),
                model.createLiteral(spec.description()));
        }

        properties.add(propShape);
        return this;
    }

    public Model build() {
        return model;
    }

    public String buildTurtle() {
        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");
        return writer.toString();
    }
}

public record DimensionSpec(
    String propertyUri,
    DimensionRole role,
    String datatype,
    String name,
    String description,
    List<String> allowedValues,
    String unit,
    Integer order
) {}
```

### 4.4 Hierarchy Support

**Zazuko Hierarchy Structure:**
```turtle
ex:dimension meta:inHierarchy [
    meta:hierarchyRoot ex:topConcept ;
    meta:nextInHierarchy [
        sh:path skos:broader ;
        sh:targetClass ex:Level1 ;
        meta:nextInHierarchy [
            sh:path skos:broader ;
            sh:targetClass ex:Level2 ;
        ] ;
    ] ;
] .
```

**RDF Forge Implementation:**
```java
// EXISTING: rdf-forge-dimension-service/entity/HierarchyEntity.java

@Entity
@Table(name = "hierarchies")
public class HierarchyEntity {
    @Id
    private UUID id;

    @ManyToOne
    private DimensionEntity dimension;

    @Column(name = "hierarchy_uri")
    private String uri;

    @Column(name = "hierarchy_name")
    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_value_id")
    private DimensionValueEntity parentValue;

    @ManyToOne
    @JoinColumn(name = "child_value_id")
    private DimensionValueEntity childValue;

    @Column(name = "hierarchy_level")
    private Integer level;

    @Column(name = "relationship_property")
    private String relationshipProperty;  // e.g., skos:broader
}

// NEW: HierarchyService addition
@Service
public class HierarchyService {

    public Model exportHierarchyAsRdf(UUID dimensionId) {
        DimensionEntity dimension = dimensionRepository.findById(dimensionId)
            .orElseThrow();

        Model model = ModelFactory.createDefaultModel();
        Resource dimResource = model.createResource(dimension.getUri());

        // Build meta:inHierarchy structure
        List<HierarchyEntity> hierarchies =
            hierarchyRepository.findByDimensionIdOrderByLevel(dimensionId);

        // Group by level and build nested structure
        Map<Integer, List<HierarchyEntity>> byLevel = hierarchies.stream()
            .collect(Collectors.groupingBy(HierarchyEntity::getLevel));

        // Build RDF hierarchy...

        return model;
    }
}
```

**Implementation Status:** PARTIALLY IMPLEMENTED
**Gap:** RDF export of hierarchies, meta:inHierarchy structure

### 4.5 Validation Profiles

**Available Profiles:**

| Profile | URI | Purpose |
|---------|-----|---------|
| basic-cube-constraint | cube.link/.../basic-cube-constraint | Minimum cube |
| standalone-cube-constraint | cube.link/.../standalone-cube-constraint | Standard minimal metadata |
| standalone-constraint-constraint | cube.link/.../standalone-constraint-constraint | Dimension metadata |
| profile-visualize | cube.link/.../profile-visualize | Visualization platform |
| profile-opendataswiss | cube.link/.../profile-opendataswiss | Swiss Open Data |
| profile-opendataswiss-lindas | cube.link/.../profile-opendataswiss-lindas | LINDAS platform |

**RDF Forge Profile Service:**
```java
// ENHANCE: rdf-forge-shacl-service/service/ProfileValidationService.java

@Service
public class ProfileValidationService {

    private static final Map<String, String> PROFILES = Map.of(
        "basic", "https://cube.link/latest/shape/basic-cube-constraint",
        "standalone", "https://cube.link/latest/shape/standalone-cube-constraint",
        "visualize", "https://cube.link/latest/shape/profile-visualize",
        "opendataswiss", "https://cube.link/latest/shape/profile-opendataswiss"
    );

    @Cacheable("profiles")
    public Model loadProfile(String profileName) {
        String profileUri = PROFILES.get(profileName);
        if (profileUri == null) {
            throw new IllegalArgumentException("Unknown profile: " + profileName);
        }

        // Fetch profile from cube.link
        Model profile = ModelFactory.createDefaultModel();
        try (InputStream is = new URL(profileUri).openStream()) {
            profile.read(is, null, "TURTLE");
        }
        return profile;
    }

    public ValidationReport validateAgainstProfile(Model data, String profileName) {
        Model profile = loadProfile(profileName);
        Shapes shapes = Shapes.parse(profile.getGraph());

        return ShaclValidator.get().validate(shapes, data.getGraph());
    }

    public Map<String, ValidationReport> validateAgainstAllProfiles(Model data) {
        Map<String, ValidationReport> results = new HashMap<>();
        for (String profileName : PROFILES.keySet()) {
            try {
                results.put(profileName, validateAgainstProfile(data, profileName));
            } catch (Exception e) {
                // Log and continue
            }
        }
        return results;
    }
}
```

**Implementation Status:** PARTIALLY IMPLEMENTED (ProfileValidationService exists)
**Gap:** Profile caching, all profile URIs, profile fetching

---

## 5. Cube Validator Feature Analysis

### 5.1 Validation Types

#### 5.1.1 Metadata Validation

**Zazuko Command:**
```bash
b59 cube check-metadata --profile <profile-uri> < cube.nt
```

**RDF Forge Implementation:**
```java
// ENHANCE: rdf-forge-shacl-service/controller/ShapeController.java

@PostMapping("/api/v1/shapes/validate-cube-metadata")
public ResponseEntity<ValidationReportDto> validateCubeMetadata(
    @RequestBody ValidateCubeMetadataRequest request
) {
    // 1. Extract metadata (exclude observations)
    Model metadata = extractMetadata(request.getCubeData());

    // 2. Validate against profile
    String profile = request.getProfile() != null
        ? request.getProfile()
        : "standalone";

    ValidationReport report = profileValidationService
        .validateAgainstProfile(metadata, profile);

    return ResponseEntity.ok(mapToDto(report));
}

private Model extractMetadata(String cubeData) {
    Model full = parseRdf(cubeData);
    Model metadata = ModelFactory.createDefaultModel();

    // Copy everything except observations
    String query = """
        CONSTRUCT {
            ?s ?p ?o
        } WHERE {
            ?s ?p ?o .
            FILTER NOT EXISTS { ?s a <https://cube.link/Observation> }
        }
        """;

    Query q = QueryFactory.create(query);
    try (QueryExecution qe = QueryExecutionFactory.create(q, full)) {
        return qe.execConstruct();
    }
}
```

#### 5.1.2 Observation Validation

**Zazuko Command:**
```bash
b59 cube check-observations --constraint cube.nt < cube.nt
```

**RDF Forge Implementation:**
```java
// ENHANCE: rdf-forge-shacl-service/controller/ShapeController.java

@PostMapping("/api/v1/shapes/validate-cube-observations")
public ResponseEntity<ValidationReportDto> validateCubeObservations(
    @RequestBody ValidateObservationsRequest request
) {
    // 1. Extract constraint from cube or use provided
    Model constraint;
    if (request.getConstraintData() != null) {
        constraint = parseRdf(request.getConstraintData());
    } else {
        constraint = extractConstraint(request.getCubeData());
    }

    // 2. Extract observations
    Model observations = extractObservations(request.getCubeData());

    // 3. Validate in batches
    int batchSize = request.getBatchSize() != null ? request.getBatchSize() : 50;
    ValidationReport report = validateInBatches(observations, constraint, batchSize);

    return ResponseEntity.ok(mapToDto(report));
}

private ValidationReport validateInBatches(Model observations, Model constraint, int batchSize) {
    Shapes shapes = Shapes.parse(constraint.getGraph());
    List<ValidationResult> allResults = new ArrayList<>();
    boolean conforms = true;

    // Query observations
    String query = """
        SELECT DISTINCT ?obs WHERE {
            ?obs a <https://cube.link/Observation> .
        }
        """;

    List<Resource> allObs = new ArrayList<>();
    try (QueryExecution qe = QueryExecutionFactory.create(query, observations)) {
        ResultSet rs = qe.execSelect();
        while (rs.hasNext()) {
            allObs.add(rs.next().getResource("obs"));
        }
    }

    // Process in batches
    for (int i = 0; i < allObs.size(); i += batchSize) {
        List<Resource> batch = allObs.subList(i,
            Math.min(i + batchSize, allObs.size()));

        Model batchModel = extractBatch(observations, batch);
        ValidationReport batchReport = ShaclValidator.get()
            .validate(shapes, batchModel.getGraph());

        if (!batchReport.conforms()) {
            conforms = false;
        }
        allResults.addAll(batchReport.getEntries());
    }

    return new ValidationReport(conforms, allResults);
}
```

### 5.2 CLI Commands Mapping

| Barnard59 Command | RDF Forge API | Status |
|-------------------|---------------|--------|
| `b59 cube check-metadata` | `POST /api/v1/shapes/validate-cube-metadata` | TO IMPLEMENT |
| `b59 cube check-observations` | `POST /api/v1/shapes/validate-cube-observations` | TO IMPLEMENT |
| `b59 cube fetch-cube` | `POST /api/v1/cubes/fetch` | TO IMPLEMENT |
| `b59 cube fetch-metadata` | `POST /api/v1/cubes/fetch-metadata` | TO IMPLEMENT |
| `b59 cube fetch-observations` | `POST /api/v1/cubes/fetch-observations` | TO IMPLEMENT |
| `b59 cube fetch-constraint` | `POST /api/v1/cubes/fetch-constraint` | TO IMPLEMENT |

### 5.3 Validation Report Format

**Zazuko SHACL Report:**
```turtle
[] a sh:ValidationReport ;
   sh:conforms false ;
   sh:result [
       a sh:ValidationResult ;
       sh:resultSeverity sh:Violation ;
       sh:focusNode ex:observation1 ;
       sh:resultPath ex:dimension1 ;
       sh:resultMessage "Missing required property" ;
       sh:sourceConstraintComponent sh:MinCountConstraintComponent ;
   ] .
```

**RDF Forge DTO:**
```java
// rdf-forge-common/src/main/java/io/rdfforge/common/dto/ValidationReportDto.java

public record ValidationReportDto(
    boolean conforms,
    int totalViolations,
    int totalWarnings,
    int totalInfos,
    List<ValidationResultDto> results,
    Map<String, Integer> violationsByProperty,
    Map<String, Integer> violationsBySeverity
) {}

public record ValidationResultDto(
    String severity,           // Violation, Warning, Info
    String focusNode,
    String resultPath,
    String value,
    String message,
    String sourceConstraint,
    String sourceShape
) {}
```

---

## 6. Current RDF Forge Implementation Status

### 6.1 Implemented Features

| Feature | Module | Status |
|---------|--------|--------|
| Pipeline Execution Engine | rdf-forge-engine | COMPLETE |
| Operation Registry | rdf-forge-engine | COMPLETE |
| CreateObservationOperation | rdf-forge-engine/cube | COMPLETE |
| SHACL Validation | rdf-forge-shacl-service | COMPLETE |
| Shape Management | rdf-forge-shacl-service | COMPLETE |
| Cube CRUD | rdf-forge-dimension-service | COMPLETE |
| Dimension Management | rdf-forge-dimension-service | COMPLETE |
| Hierarchy Support | rdf-forge-dimension-service | PARTIAL |
| Pipeline CRUD | rdf-forge-pipeline-service | COMPLETE |
| Job Execution | rdf-forge-job-service | COMPLETE |
| Triplestore Connections | rdf-forge-triplestore-service | COMPLETE |
| Graph Store Protocol | rdf-forge-triplestore-service | COMPLETE |
| Data Upload | rdf-forge-data-service | COMPLETE |
| Cube Wizard UI | rdf-forge-ui | PARTIAL |
| Pipeline Designer UI | rdf-forge-ui | COMPLETE |
| SHACL Studio UI | rdf-forge-ui | COMPLETE |

### 6.2 Missing Features

| Feature | Priority | Effort |
|---------|----------|--------|
| BuildCubeShapeOperation | HIGH | Medium |
| FetchCubeOperation | MEDIUM | Low |
| Cube Metadata Validation | HIGH | Medium |
| Observation Validation | HIGH | Medium |
| Profile Validation | HIGH | Low |
| XLSX Support | MEDIUM | Low |
| Full CSVW Support | MEDIUM | High |
| Column Type Detection | MEDIUM | Medium |
| URI Template Builder | LOW | Low |
| Hierarchy RDF Export | LOW | Medium |
| Multi-language Metadata | LOW | Medium |

---

## 7. Gap Analysis

### 7.1 Critical Gaps (Must Have)

#### 7.1.1 Cube Validation Suite
**Gap:** No dedicated cube validation API endpoints
**Impact:** Cannot validate cubes against cube-link profiles
**Solution:** Add `CubeValidatorController` with endpoints for metadata and observation validation

#### 7.1.2 BuildCubeShape Operation
**Gap:** Cannot automatically generate SHACL shapes from observation streams
**Impact:** Manual shape creation required
**Solution:** Implement `BuildCubeShapeOperation` in rdf-forge-engine

#### 7.1.3 Profile-based Validation
**Gap:** Cannot validate against cube.link profiles
**Impact:** Cannot ensure cube-link compliance
**Solution:** Implement profile fetching and caching in `ProfileValidationService`

### 7.2 Important Gaps (Should Have)

#### 7.2.1 Cube Fetch Operations
**Gap:** Cannot fetch cubes from SPARQL endpoints
**Impact:** Cannot integrate with existing cube repositories
**Solution:** Implement `FetchCubeOperation`, `FetchMetadataOperation`, etc.

#### 7.2.2 Column Type Detection
**Gap:** No automatic column type detection
**Impact:** Manual configuration of datatypes
**Solution:** Add `ColumnAnalyzerService` for statistical type detection

#### 7.2.3 XLSX Support
**Gap:** Cannot import Excel files
**Impact:** Limited data source support
**Solution:** Add `XlsxParseOperation` using Apache POI

### 7.3 Nice-to-Have Gaps

#### 7.3.1 Full CSVW Support
**Gap:** Basic CSVW parsing only
**Impact:** Advanced CSVW features not supported
**Solution:** Enhance `CsvwParseOperation` with full W3C CSVW spec

#### 7.3.2 Multi-language Metadata UI
**Gap:** UI doesn't support multilingual metadata entry
**Impact:** International deployment limited
**Solution:** Add language selector and multi-value fields in Cube Wizard

---

## 8. Implementation Roadmap

### Phase 1: Cube Validation (Priority: HIGH)

**New Files to Create:**

1. `rdf-forge-shacl-service/src/main/java/io/rdfforge/shacl/controller/CubeValidatorController.java`
2. `rdf-forge-shacl-service/src/main/java/io/rdfforge/shacl/service/CubeValidationService.java`
3. `rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/BuildCubeShapeOperation.java`
4. `rdf-forge-common/src/main/java/io/rdfforge/common/dto/CubeValidationRequest.java`

**API Endpoints:**
```
POST /api/v1/cubes/validate/metadata
POST /api/v1/cubes/validate/observations
POST /api/v1/cubes/validate/full
GET  /api/v1/cubes/validate/profiles
```

### Phase 2: Cube Fetch Operations (Priority: MEDIUM)

**New Files to Create:**

1. `rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/FetchCubeOperation.java`
2. `rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/FetchMetadataOperation.java`
3. `rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/FetchObservationsOperation.java`
4. `rdf-forge-engine/src/main/java/io/rdfforge/engine/cube/FetchConstraintOperation.java`

### Phase 3: Enhanced Data Import (Priority: MEDIUM)

**New Files to Create:**

1. `rdf-forge-engine/src/main/java/io/rdfforge/engine/format/XlsxParseOperation.java`
2. `rdf-forge-data-service/src/main/java/io/rdfforge/data/service/ColumnAnalyzerService.java`

**Dependencies to Add:**
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

### Phase 4: UI Enhancements (Priority: LOW)

**Files to Modify:**

1. `rdf-forge-ui/src/app/features/cube/cube-wizard/cube-wizard.ts` - Multi-language support
2. `rdf-forge-ui/src/app/core/components/validation-report/` - New component

---

## 9. API Mapping

### 9.1 Cube Creator API → RDF Forge API

| Cube Creator (Hydra) | RDF Forge | Notes |
|---------------------|-----------|-------|
| GET /project | GET /api/v1/projects | Existing |
| POST /csv-source | POST /api/v1/data/upload | Existing |
| GET /source/:id/columns | GET /api/v1/data/:id/columns | To enhance |
| POST /cube | POST /api/v1/cubes | Existing |
| PUT /cube/:id/metadata | PUT /api/v1/cubes/:id | Existing |
| POST /cube/:id/dimension-mapping | PUT /api/v1/cubes/:id (metadata.columnMappings) | Existing |
| POST /cube/:id/publish | POST /api/v1/jobs + pipeline execution | Existing |

### 9.2 Barnard59 CLI → RDF Forge API

| Barnard59 Command | RDF Forge API | Status |
|-------------------|---------------|--------|
| b59 run pipeline.ttl | POST /api/v1/jobs | EXISTING |
| b59 cube check-metadata | POST /api/v1/cubes/validate/metadata | TO ADD |
| b59 cube check-observations | POST /api/v1/cubes/validate/observations | TO ADD |
| b59 cube fetch-cube | POST /api/v1/cubes/fetch | TO ADD |
| b59 shacl validate | POST /api/v1/shapes/validate | EXISTING |

---

## 10. Database Schema Additions

### 10.1 New Tables

```sql
-- V6__add_validation_profiles.sql

CREATE TABLE validation_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    source_uri VARCHAR(500) NOT NULL,
    content TEXT,  -- Cached SHACL content
    content_format VARCHAR(20) DEFAULT 'turtle',
    is_active BOOLEAN DEFAULT true,
    is_builtin BOOLEAN DEFAULT false,
    cache_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert built-in profiles
INSERT INTO validation_profiles (name, display_name, description, source_uri, is_builtin) VALUES
('basic', 'Basic Cube', 'Minimum cube structure', 'https://cube.link/latest/shape/basic-cube-constraint', true),
('standalone', 'Standalone Cube', 'Standard cube with minimal metadata', 'https://cube.link/latest/shape/standalone-cube-constraint', true),
('visualize', 'Visualize Profile', 'Cube for visualization platforms', 'https://cube.link/latest/shape/profile-visualize', true),
('opendataswiss', 'OpenData.swiss', 'Swiss Open Data portal profile', 'https://cube.link/latest/shape/profile-opendataswiss', true);

-- Validation results history
CREATE TABLE validation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cube_id UUID REFERENCES cubes(id) ON DELETE CASCADE,
    profile_name VARCHAR(100),
    validation_type VARCHAR(50) NOT NULL,  -- metadata, observations, full
    conforms BOOLEAN NOT NULL,
    violation_count INTEGER DEFAULT 0,
    warning_count INTEGER DEFAULT 0,
    report_data JSONB,  -- Full validation report
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

CREATE INDEX idx_validation_results_cube ON validation_results(cube_id);
CREATE INDEX idx_validation_results_created ON validation_results(created_at DESC);
```

### 10.2 Schema Modifications

```sql
-- V7__enhance_cubes_metadata.sql

-- Add validation status to cubes
ALTER TABLE cubes ADD COLUMN validation_status VARCHAR(50) DEFAULT 'not_validated';
-- Values: not_validated, valid, invalid, partial

ALTER TABLE cubes ADD COLUMN last_validation_id UUID REFERENCES validation_results(id);

-- Add profile preference
ALTER TABLE cubes ADD COLUMN default_profile VARCHAR(100) DEFAULT 'standalone';

-- Add observation URI template
ALTER TABLE cubes ADD COLUMN observation_uri_template VARCHAR(500);
```

---

## Appendix A: Cube-Link Vocabulary Reference

```turtle
@prefix cube: <https://cube.link/> .
@prefix meta: <https://cube.link/meta/> .
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix schema: <https://schema.org/> .
@prefix qudt: <http://qudt.org/schema/qudt/> .

# Classes
cube:Cube              # Container for observation sets
cube:ObservationSet    # Group of observations
cube:Observation       # Single data point
cube:Constraint        # SHACL validation shape
cube:KeyDimension      # Dimension identifying observations
cube:MeasureDimension  # Measured/counted values
cube:Undefined         # Datatype for undefined values

# Properties
cube:observationSet           # Cube → ObservationSet
cube:observation              # ObservationSet → Observation
cube:observationConstraint    # Cube → Constraint
cube:observedBy               # Observation → Agent

# Meta vocabulary
meta:inHierarchy       # Dimension hierarchy
meta:hierarchyRoot     # Root concept
meta:nextInHierarchy   # Next hierarchy level
meta:dataKind          # Temporal/Spatial marker
meta:applicationIgnores # Hide dimension per app

# Required metadata
schema:name            # Multilingual name
schema:description     # Multilingual description
qudt:hasUnit           # Measurement unit
qudt:scaleType         # Nominal/Ordinal/Interval/Ratio
sh:order               # Display order
```

---

## Appendix B: Validation Profile Comparison

| Constraint | Basic | Standalone | Visualize | OpenData.swiss |
|------------|-------|------------|-----------|----------------|
| cube:Cube exists | ✓ | ✓ | ✓ | ✓ |
| schema:name | | ✓ | ✓ | ✓ |
| schema:description | | ✓ | ✓ | ✓ |
| dcat:publisher | | | | ✓ |
| dcat:contactPoint | | | | ✓ |
| dct:identifier | | | | ✓ |
| schema:datePublished | | | | ✓ |
| Dimension labels | | ✓ | ✓ | ✓ |
| qudt:scaleType | | | ✓ | |
| sh:order on dims | | | ✓ | |

---

*Document Version: 1.0*
*Last Updated: 2024*
*Author: RDF Forge Development Team*
