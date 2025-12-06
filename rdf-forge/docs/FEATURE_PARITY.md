# Feature Parity Analysis: RDF Forge vs Reference Implementations

This document analyzes feature parity between RDF Forge (cube-validator-x) and the reference implementations:
- **cube-creator** - LINDAS Cube Creator for Swiss Federal Statistical Office
- **cube-link** - RDF Cube Schema and validation profiles
- **barnard59** - RDF ETL pipeline engine

---

## Executive Summary

| Category | Cube Creator | Cube Link | Barnard59 | RDF Forge Status |
|----------|-------------|-----------|-----------|------------------|
| CSV to RDF Conversion | Full | N/A | Full | Implemented |
| Column Mapping | Full CSVW | N/A | CSVW | Implemented |
| Cube Schema | Full | Full | Full | Implemented |
| SHACL Validation | Yes | 7 Profiles | Yes | Implemented |
| Shared Dimensions | Yes | Yes | N/A | Partial |
| Hierarchies | Yes | Yes | N/A | Planned |
| Pipeline Engine | Barnard59 | N/A | Native | Custom |
| Authentication | OIDC | N/A | N/A | OIDC + PAT |
| Job Management | Full | N/A | CLI | Full |
| Import/Export | Full | N/A | N/A | Planned |

---

## 1. Cube Creator Feature Comparison

### 1.1 Core Data Processing

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| CSV Upload | Yes | Yes | S3/MinIO storage |
| CSV Preview | Yes | Yes | First N rows |
| CSV Replacement | Yes | Yes | Version management |
| CSV Header Detection | Auto | Auto | - |
| CSVW Mapping Builder | Full | Planned | URI templates |
| Multi-table Support | Yes | Planned | Related tables |

### 1.2 Column Mapping

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| Literal Columns | Yes | Yes | Property + datatype |
| Reference Columns | Yes | Planned | Cross-table references |
| Dimension Assignment | Yes | Yes | Key/Measure/Attribute |
| URI Templates | Full | Basic | Auto-generation |
| Language Tags | Yes | Planned | Multi-lingual |
| Default Values | Yes | Planned | Fill missing |
| Datatype Selection | Full XSD | Full XSD | - |
| Scale of Measure (QUDT) | Yes | Planned | Nominal/Ordinal/Ratio |

### 1.3 Dimension Management

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| Dimension Metadata | Full | Basic | Labels, descriptions |
| Shared Dimensions | Yes (API) | Planned | Cross-cube reuse |
| Dimension Import | Yes | Planned | From existing cubes |
| Hierarchy Support | Full | Planned | Multi-level |
| Term Collections | Yes | Planned | Code lists |

### 1.4 Cube Design

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| Cube Metadata | Full | Full | Title, description, etc. |
| Data Structure Definition | Auto | Auto | Generated from mapping |
| Observation Generation | Yes | Yes | From CSV rows |
| Cube Preview | Live | Basic | Sample observations |
| Multiple Data Sources | Yes | Planned | Per project |

### 1.5 Validation

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| SHACL Validation | Full | Full | Shape-based |
| Datatype Validation | Yes | Yes | XSD types |
| Constraint Validation | Yes | Yes | SHACL constraints |
| Validation Reports | Yes | Yes | Error details |
| Custom Shapes | Yes | Yes | User-defined |

### 1.6 Job Management

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| Transform Jobs | Yes | Yes | CSV to RDF |
| Publish Jobs | Yes | Yes | To triplestore |
| Job History | Yes | Yes | With logs |
| Job Status Tracking | Yes | Yes | Real-time |
| Activity Logging | Yes | Yes | Audit trail |
| Retry Failed Jobs | Yes | Planned | - |

### 1.7 Import/Export

| Feature | Cube Creator | RDF Forge | Notes |
|---------|-------------|-----------|-------|
| Project Export | Full RDF | Planned | Portable format |
| Project Import | Full RDF | Planned | With validation |
| Cube Export | N-Quads | Yes | Multiple formats |
| Pipeline Export | RDF | Yes | YAML format |

---

## 2. Cube Link Schema Comparison

### 2.1 Core Cube Schema

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| cube:Cube | Yes | Yes | Core class |
| cube:Observation | Yes | Yes | Data points |
| cube:ObservationSet | Yes | Yes | Observation container |
| cube:Constraint | Yes | Yes | SHACL shapes |
| cube:Undefined | Yes | Planned | NULL handling |

### 2.2 Dimension Types

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| cube:KeyDimension | Yes | Yes | Key dimensions |
| cube:MeasureDimension | Yes | Yes | Measure dimensions |
| meta:SharedDimension | Yes | Planned | Reusable dimensions |

### 2.3 Validation Profiles

| Profile | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| basic-cube-constraint | Yes | Yes | Minimum structure |
| standalone-cube-constraint | Yes | Yes | Standard profile |
| profile-visualize | Yes | Planned | Visualize.admin.ch |
| profile-opendataswiss | Yes | Planned | DCAT-AP CH |
| profile-opendataswiss-lindas | Yes | Planned | Extended profile |

### 2.4 Dimension Features

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| sh:path | Yes | Yes | Property IRI |
| sh:datatype | Yes | Yes | XSD types |
| sh:nodeKind | Yes | Yes | IRI/Literal |
| sh:in | Yes | Planned | Code lists |
| sh:minCount/maxCount | Yes | Yes | Cardinality |
| qudt:scaleType | Yes | Planned | Scale of measure |
| qudt:hasUnit | Yes | Planned | Units |

### 2.5 Dimension Relations

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| relation:StandardError | Yes | Planned | Statistical relations |
| relation:StandardDeviation | Yes | Planned | - |
| relation:Confidence | Yes | Planned | - |
| meta:dimensionRelation | Yes | Planned | Relationship links |

### 2.6 Hierarchy Support

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| meta:inHierarchy | Yes | Planned | Hierarchy membership |
| meta:nextInHierarchy | Yes | Planned | Hierarchy levels |
| Multiple Hierarchies | Yes | Planned | Per dimension |

### 2.7 Metadata Extensions

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| schema:name | Yes | Yes | Multilingual |
| schema:description | Yes | Yes | Multilingual |
| schema:publisher | Yes | Planned | Publisher info |
| schema:datePublished | Yes | Yes | Publication date |
| schema:expires | Yes | Planned | Version expiry |
| schema:CreativeWorkStatus | Yes | Planned | Draft/Published |

### 2.8 Data Kind Specifications

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| schema:GeoCoordinates | Yes | Planned | Lat/Long |
| schema:GeoShape | Yes | Planned | Geometries |
| time:GeneralDateTimeDescription | Yes | Planned | Temporal types |

### 2.9 Annotations

| Feature | Cube Link | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| meta:Limit | Yes | Planned | Target values |
| meta:annotationContext | Yes | Planned | Value ranges |
| meta:applicationIgnores | Yes | Planned | App filtering |

---

## 3. Barnard59 Feature Comparison

### 3.1 Pipeline Definition

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| RDF Pipeline Format | Turtle/JSON-LD | YAML + RDF | Custom format |
| Variable Substitution | Yes | Yes | Runtime params |
| Step Chaining | RDF Lists | YAML nodes | - |
| CLI Execution | Yes | Yes | Job service |

### 3.2 Data Sources

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| CSV (CSVW) | Yes | Yes | Full support |
| Excel (XLSX) | Yes | Planned | Via CSVW |
| JSON/JSON-LD | Yes | Planned | Parsing |
| RDF Files | Yes | Yes | Multiple formats |
| SPARQL Endpoints | Yes | Yes | Query + update |
| HTTP/HTTPS | Yes | Yes | Fetch data |
| FTP/SFTP | Yes | Planned | Remote files |
| S3 Storage | Yes | Yes | MinIO |
| stdin | Yes | N/A | CLI mode |

### 3.3 Transformations

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| Map | Yes | Yes | Transform items |
| Filter | Yes | Yes | Select items |
| Batch | Yes | Yes | Group items |
| Flatten | Yes | Planned | Ungroup |
| forEach | Yes | Planned | Sub-pipelines |
| Combine | Yes | Planned | Merge streams |
| Limit/Offset | Yes | Planned | Pagination |
| Custom JavaScript | Yes | No | Security |

### 3.4 RDF Operations

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| Parse RDF | Yes | Yes | All formats |
| Serialize RDF | Yes | Yes | All formats |
| Set Graph | Yes | Yes | Named graphs |
| SPARQL CONSTRUCT | Yes | Yes | Query |
| SPARQL SELECT | Yes | Yes | Query |
| toObservation | Yes | Yes | Cube creation |
| buildCubeShape | Yes | Planned | Auto-generate |

### 3.5 Output Destinations

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| File System | Yes | Yes | Local files |
| SPARQL Graph Store | Yes | Yes | POST/PUT |
| S3 Storage | Yes | Yes | MinIO |
| stdout | Yes | N/A | CLI mode |
| nul (dev/null) | Yes | N/A | - |
| GitLab CI/CD | No | Yes | New feature |
| GitHub Actions | No | Planned | New feature |

### 3.6 Validation

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| SHACL Validation | Yes | Yes | Full support |
| Validation Reports | Yes | Yes | Standard format |
| Report Summary | Yes | Yes | Human-readable |
| Max Errors Config | Yes | Yes | - |
| Custom Callbacks | Yes | No | Security |
| Pipeline Validation | Yes | Planned | Structure check |

### 3.7 Cube Operations

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| fetch-cube | Yes | Yes | Full cube |
| fetch-metadata | Yes | Yes | Metadata only |
| fetch-observations | Yes | Yes | Data only |
| check-metadata | Yes | Yes | Profile validation |
| check-observations | Yes | Yes | Constraint check |
| Batch Processing | Yes | Planned | Large datasets |

### 3.8 Observability

| Feature | Barnard59 | RDF Forge | Notes |
|---------|-----------|-----------|-------|
| OpenTelemetry | Yes | Planned | Tracing |
| Metrics | Yes | Planned | Prometheus |
| Logging | Winston | SLF4J | - |
| Buffer Monitoring | Yes | Planned | Memory |

---

## 4. Features Unique to RDF Forge

### 4.1 New Capabilities

| Feature | Description | Status |
|---------|-------------|--------|
| GitLab CI/CD Integration | Trigger pipelines, push data | Implemented |
| GitOps Configuration Sync | Sync pipelines/shapes via Git | Implemented |
| Visual Pipeline Designer | Drag-and-drop node editor | Implemented |
| Admin Panel | User/role management | Implemented |
| Settings Service | Centralized configuration | Implemented |
| Multiple Triplestore Support | GraphDB, Fuseki, Stardog | Implemented |
| Personal Access Tokens | API authentication | Implemented |

### 4.2 Architecture Advantages

| Feature | Description |
|---------|-------------|
| Microservices | Independent scaling |
| API Gateway | Single entry point |
| Redis Caching | Performance optimization |
| PostgreSQL | Relational data storage |
| S3/MinIO | File storage |
| Keycloak | Enterprise SSO |
| Angular UI | Modern SPA |

---

## 5. Priority Implementation Roadmap

### Phase 1: Critical Parity (High Priority)
1. [ ] Full CSVW Builder with URI Templates
2. [ ] Shared Dimensions API
3. [ ] Hierarchy Support (meta:inHierarchy)
4. [ ] cube:Undefined handling
5. [ ] Profile-based validation (visualize, opendataswiss)

### Phase 2: Enhanced Features (Medium Priority)
1. [ ] Dimension Relations
2. [ ] Data Kind Specifications (Geo, Temporal)
3. [ ] Annotations and Limits
4. [ ] Project Import/Export
5. [ ] Excel (XLSX) Support

### Phase 3: Advanced Capabilities (Lower Priority)
1. [ ] FTP/SFTP Data Sources
2. [ ] Pipeline Validation (structure check)
3. [ ] OpenTelemetry Integration
4. [ ] Custom JavaScript Transformations (sandboxed)
5. [ ] GitHub Actions Integration

---

## 6. Conclusion

RDF Forge provides a solid foundation with unique capabilities like visual pipeline design, GitLab CI/CD integration, and GitOps configuration management. Key areas for improvement include:

1. **Full CSVW support** - Complete URI template builder
2. **Shared dimensions** - Cross-cube dimension reuse
3. **Hierarchies** - Multi-level dimension hierarchies
4. **Advanced validation profiles** - Swiss-specific profiles

The microservices architecture and modern UI provide advantages for enterprise deployment and user experience.
