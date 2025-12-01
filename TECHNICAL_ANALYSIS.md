# Cube Validator X - Technical Analysis Report

## Executive Summary

The **cube-validator-x** project is a comprehensive initiative to create a production-ready replacement for Zazuko's cube ecosystem (Cube Creator, Barnard59, cube-link validators) for the Swiss Federal Archives. The project contains **three main implementation approaches**:

1. **lindas-cube-creator**: Forked/adapted TypeScript-based Cube Creator from Zazuko
2. **lindas-barnard59**: Forked/adapted TypeScript-based Barnard59 pipeline engine from Zazuko
3. **rdf-forge**: A new **Java-based** unified platform designed to replace both systems

**Key Finding**: The project includes both TypeScript/JavaScript (existing Zazuko code) AND Java (new RDF Forge implementation), making this a **dual-stack architecture** during the transition period.

---

## Project Structure Overview

```
cube-validator-x/
├── lindas-cube-creator/       # TypeScript - Forked Zazuko Cube Creator
├── lindas-barnard59/          # JavaScript/TypeScript - Forked Barnard59 pipeline engine
├── rdf-forge/                 # Java - New unified platform (Spring Boot)
├── workfolder-cube-validator-x/ # Empty work folder
├── -p/                        # Unknown purpose
├── .claude/                   # Claude AI configuration
└── UNIFIED_RDF_PLATFORM_TASK.md # Project requirements document
```

---

## 1. lindas-cube-creator (TypeScript Fork)

### Overview
A fork of Zazuko's Cube Creator - a tool to create RDF data cubes from CSV files.

### Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | TypeScript | ~4.5.0 |
| **UI Framework** | Vue.js 3 | ^3.2.47 |
| **State Management** | Vuex | ^4.0.0 |
| **Router** | Vue Router | ^4.0.0 |
| **UI Components** | Oruga + Bulma | ^0.5.10 |
| **API Framework** | Express.js | ^4.20.0 |
| **API Protocol** | Hydra-box | ^0.6.6 |
| **Authentication** | OIDC (vuex-oidc) | ^3.10.1 |
| **RDF Libraries** | RDF/JS, Clownface, Alcaeus | Various |
| **File Upload** | Uppy | ^3.x |
| **Validation** | SHACL | Various |
| **Package Manager** | Yarn | Workspaces |
| **Testing** | Mocha, Chai, Cypress | Various |
| **Build Tool** | Vue CLI | ^5.0.1 |
| **Observability** | OpenTelemetry, Sentry | Various |

### Architecture

```
lindas-cube-creator/
├── ui/                    # Vue.js 3 SPA Frontend
│   ├── src/
│   │   ├── views/        # Page components
│   │   ├── components/   # Reusable components
│   │   ├── store/        # Vuex store modules
│   │   ├── api/          # API client
│   │   └── forms/        # Form components (SHACL-based)
│   └── tests/            # UI tests (Cypress E2E)
├── apis/
│   ├── core/             # Main Hydra API
│   ├── shared-dimensions/ # Shared dimensions API
│   └── errors/           # Error handling
├── cli/                   # Job Runner (uses Barnard59)
├── packages/
│   ├── core/             # Core utilities
│   ├── model/            # Data models
│   ├── express/          # Express middleware
│   ├── shacl-middleware/ # SHACL validation
│   └── testing/          # Test utilities
└── e2e-tests/            # API E2E tests (Hydra format)
```

### Key Features
- CSV file upload and management (S3 storage)
- CSVW-based column mapping
- Dimension metadata management
- RDF Cube generation
- SHACL-based form generation (Shaperone)
- Job execution with barnard59 pipelines
- Multi-triplestore support (Fuseki)
- OIDC authentication

### Testing Framework
- **Unit Tests**: Mocha + Chai + Sinon
- **E2E Tests (API)**: Hypertest with `.hydra` files
- **E2E Tests (UI)**: Cypress 8.3.0
- **Coverage**: c8 (Istanbul) with lcov output
- **Test Configuration**: `mocha-setup.js`

### Test Coverage Configuration
```yaml
# codecov.yml
ignore:
  - apis/*/lib/handlers  # Handlers excluded from coverage
```

---

## 2. lindas-barnard59 (JavaScript Pipeline Engine)

### Overview
A fork of Zazuko's Barnard59 - Linked Data pipeline engine based on RDF declarations.

### Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | JavaScript (ESM) + TypeScript | ^5.4.5 |
| **Runtime** | Node.js | ≥16 |
| **Package Structure** | npm Workspaces | Monorepo |
| **Testing** | Mocha | ^11.7.1 |
| **Coverage** | c8 | ^7.6.0 |
| **Linting** | ESLint | Various |

### Package Structure

```
lindas-barnard59/
├── packages/
│   ├── formats/          # RDF format parsers (CSVW, JSON-LD, N3, RDF-XML, XLSX)
│   ├── ftp/              # FTP/SFTP operations
│   ├── graph-store/      # SPARQL Graph Store Protocol
│   ├── http/             # HTTP GET/POST operations
│   ├── rdf/              # RDF utilities (metadata, imports, etc.)
│   ├── s3/               # S3 storage operations
│   ├── shacl/            # SHACL validation
│   ├── sparql/           # SPARQL query execution
│   └── validation/       # Pipeline validation
└── test/
    ├── support/          # Test support files
    └── e2e/              # End-to-end pipeline tests
```

### Key Packages & Features

| Package | Purpose | Test Files |
|---------|---------|------------|
| `barnard59-formats` | Parse CSVW, JSON-LD, N3, RDF-XML, XLSX | 4 tests |
| `barnard59-ftp` | FTP/SFTP read, write, list, move | 4 tests |
| `barnard59-graph-store` | Graph Store GET/POST/PUT | 4 tests |
| `barnard59-http` | HTTP requests | TypeScript |
| `barnard59-rdf` | RDF metadata, imports, matching | Various |
| `barnard59-s3` | S3 getObject, putObject | 4 tests |
| `barnard59-shacl` | SHACL validation | 3 tests |
| `barnard59-sparql` | SPARQL construct, select | 3 tests |
| `barnard59-validation` | Pipeline validation | 9 tests |

### Pipeline Definition Format
Pipelines are defined in RDF/Turtle format using manifest files:
- `manifest.ttl` - Operation declarations
- Pipeline steps reference EcmaScript modules

---

## 3. rdf-forge (Java Implementation)

### Overview
**This is the new Java-based unified platform** intended to replace both Cube Creator and Barnard59.

### Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Java | 21 |
| **Framework** | Spring Boot | 3.2.5 |
| **Build Tool** | Maven | Multi-module |
| **Cloud** | Spring Cloud | 2023.0.1 |
| **RDF Library** | Apache Jena | 5.0.0 |
| **Pipeline Engine** | Apache Camel | 4.5.0 |
| **Object Storage** | MinIO | 8.5.9 |
| **Database** | PostgreSQL + Flyway | 10.10.0 |
| **API Docs** | SpringDoc OpenAPI | 2.5.0 |
| **Code Generation** | Lombok + MapStruct | Various |

### Frontend Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Framework** | Vue.js 3 | ^3.4.21 |
| **State Management** | Pinia | ^2.1.7 |
| **Router** | Vue Router | ^4.3.0 |
| **UI Components** | PrimeVue | ^3.52.0 |
| **Pipeline Visualization** | Vue Flow | ^1.33.0 |
| **Code Editor** | Monaco Editor | ^0.47.0 |
| **Build Tool** | Vite | ^5.2.8 |
| **Testing** | Vitest | ^1.5.0 |
| **HTTP Client** | Axios | ^1.6.8 |
| **Authentication** | Keycloak | ^24.0.0 |

### Microservices Architecture

```
rdf-forge/
├── rdf-forge-common/           # Shared models and exceptions
├── rdf-forge-engine/           # Pipeline execution engine
├── rdf-forge-gateway/          # API Gateway (routing, auth, rate limiting)
├── rdf-forge-pipeline-service/ # Pipeline CRUD
├── rdf-forge-job-service/      # Job execution and monitoring
├── rdf-forge-data-service/     # File upload and management
├── rdf-forge-dimension-service/# Dimension and hierarchy management
├── rdf-forge-shacl-service/    # SHACL shape management
├── rdf-forge-triplestore-service/ # Triplestore connections
├── rdf-forge-cli/              # Command-line interface
└── rdf-forge-ui/               # Vue.js 3 frontend
```

### Service Details

| Service | Database | Key Features |
|---------|----------|--------------|
| Gateway | - | CORS, Rate Limiting, Auth, Routing |
| Pipeline Service | PostgreSQL | Pipeline CRUD, versioning |
| Job Service | PostgreSQL | Job execution, logs, monitoring |
| Data Service | PostgreSQL | File upload, S3 storage |
| Dimension Service | PostgreSQL | Dimensions, hierarchies, SKOS |
| SHACL Service | PostgreSQL | Shape management, validation |
| Triplestore Service | PostgreSQL | Multi-triplestore connections |

### Engine Operations

```
rdf-forge-engine/
├── operation/
│   ├── source/
│   │   ├── LoadCsvOperation.java
│   │   └── LoadJsonOperation.java
│   ├── transform/
│   │   └── MapToRdfOperation.java
│   ├── output/
│   │   └── GraphStorePutOperation.java
│   └── validation/
│       └── ValidateShaclOperation.java
├── pipeline/
│   └── PipelineExecutor.java
└── shacl/
    ├── ShaclValidator.java
    └── ShaclValidatorService.java
```

### Frontend Views

| View | Purpose |
|------|---------|
| Dashboard | Overview and metrics |
| PipelineDesigner | Visual pipeline builder (Vue Flow) |
| PipelineList | Pipeline management |
| CubeWizard | Guided cube creation |
| DataManager | File upload and management |
| DimensionManager | Dimension and hierarchy editing |
| JobMonitor | Real-time job status |
| JobList | Job history |
| ShaclStudio | SHACL shape design |
| ShapeEditor | Shape editing |
| TriplestoreBrowser | SPARQL and graph browsing |
| Settings | Configuration |

---

## 4. Component Mapping: Zazuko → cube-validator-x

### Cube Creator Replacement

| Zazuko Component | lindas-cube-creator | rdf-forge |
|------------------|---------------------|-----------|
| UI (Vue.js) | ✅ Forked | ✅ New (PrimeVue) |
| Core API (Hydra) | ✅ Forked | ✅ REST/OpenAPI |
| Shared Dimensions | ✅ Forked | ✅ Dimension Service |
| Job Runner (CLI) | ✅ Forked | ✅ Job Service |
| S3 Storage | ✅ Maintained | ✅ MinIO |
| OIDC Auth | ✅ Maintained | ✅ Keycloak |

### Barnard59 Replacement

| Zazuko Component | lindas-barnard59 | rdf-forge |
|------------------|------------------|-----------|
| Pipeline Engine | ✅ Forked | ✅ Apache Camel |
| CSVW Parser | ✅ Forked | ✅ Apache Jena |
| Graph Store | ✅ Forked | ✅ Triplestore Service |
| SHACL Validation | ✅ Forked | ✅ Jena SHACL |
| HTTP Operations | ✅ Forked | ✅ Spring WebClient |
| S3 Operations | ✅ Forked | ✅ MinIO Client |
| FTP Operations | ✅ Forked | 🔴 Not implemented |

---

## 5. Test Coverage Analysis

### lindas-cube-creator Tests

| Category | Framework | Location | Count |
|----------|-----------|----------|-------|
| API Unit Tests | Mocha/Chai | `apis/core/test/` | ~60 files |
| CLI Tests | Mocha/Chai | `cli/test/` | Multiple |
| UI Unit Tests | Jest | `ui/tests/` | Limited |
| UI E2E Tests | Cypress | `ui/tests/e2e/` | Multiple |
| API E2E Tests | Hypertest | `e2e-tests/` | ~40 scenarios |

### lindas-barnard59 Tests

| Package | Test Count | Files |
|---------|------------|-------|
| formats | 3 | `jsonld.test.js`, `n3.test.js`, `rdf-xml.test.js` |
| ftp | 4 | `list.test.js`, `move.test.js`, `read.test.js`, `write.test.js` |
| graph-store | 4 | `get.test.js`, `post.test.js`, `put.test.js`, `pipeline.test.js` |
| s3 | 4 | `getObject.test.js`, `putObject.test.js`, `getObjectStream.test.js`, `lib.test.js` |
| shacl | 3 | `validate.test.js`, `TermCounter.test.js`, `pipeline/validate.test.js` |
| sparql | 3 | `construct.test.js`, `select.test.js`, `inMemory.test.js` |
| validation | 9 | Various validation tests |
| e2e | 2 | `forEach.e2e.test.js`, `pipeline.e2e.test.js` |

### rdf-forge Tests

| Service | Test Type | Files |
|---------|-----------|-------|
| Common | Unit | `ExceptionTest.java`, `CubeTest.java`, `PipelineTest.java` |
| Data Service | Unit + Integration | 4 test classes |
| Dimension Service | Unit + Integration | 5 test classes |
| Gateway | Unit + Integration | 4 test classes |
| Job Service | Unit + Integration | 5 test classes |
| Pipeline Service | Unit + Integration | 4 test classes |
| SHACL Service | Unit + Integration | 4 test classes |
| Triplestore Service | Unit + Integration | 4 test classes |
| Engine | Unit | 6 test classes |

---

## 6. Gaps and Missing Features

### lindas-cube-creator Gaps
1. **Format Support**: Only CSV (no JSON, XML, Parquet, XLSX direct support)
2. **Visual Pipeline Designer**: No drag-and-drop pipeline builder
3. **Multi-format Output**: Limited export options
4. **GraphQL API**: Not implemented

### lindas-barnard59 Gaps
1. **UI**: No user interface (CLI only)
2. **Job Management**: No built-in job scheduling/monitoring
3. **Authentication**: No multi-user support
4. **Persistence**: No pipeline storage

### rdf-forge Gaps (Incomplete Implementation)
1. **FTP Operations**: Not implemented
2. **XML Parser**: Not implemented
3. **Parquet Support**: Not implemented
4. **XLSX Support**: Not implemented
5. **GraphQL API**: Not implemented
6. **Pipeline Templates**: Not implemented
7. **Scheduled Jobs**: Partial implementation
8. **Multi-tenancy**: Not implemented
9. **Audit Logging**: Not implemented
10. **Data Lineage**: Not implemented

### Critical Missing Components for Production

| Component | Status | Priority |
|-----------|--------|----------|
| Complete test coverage | 🟡 Partial | High |
| Performance benchmarks | 🔴 Missing | High |
| Security audit | 🔴 Missing | Critical |
| Load testing | 🔴 Missing | High |
| Documentation | 🟡 Partial | Medium |
| Migration tools | 🔴 Missing | High |
| Monitoring dashboards | 🟡 Partial | Medium |

---

## 7. Recommendations for Production Readiness

### Short-term (1-3 months)

1. **Consolidate Architecture Decision**: Choose between TypeScript (lindas-*) and Java (rdf-forge) paths
2. **Complete Core Tests**: Achieve >80% code coverage
3. **Security Audit**: OWASP compliance check
4. **Performance Testing**: Benchmark with large datasets (1M+ rows)
5. **Documentation**: API documentation, deployment guides

### Medium-term (3-6 months)

1. **Feature Parity**: Ensure rdf-forge matches all Cube Creator features
2. **Migration Tools**: Build data migration from existing systems
3. **Multi-triplestore Support**: Test with Stardog, GraphDB, Neptune
4. **CI/CD Pipeline**: Complete automated deployment
5. **Observability**: Full OpenTelemetry integration

### Long-term (6-12 months)

1. **Enterprise Features**: Multi-tenancy, RBAC, audit logs
2. **Advanced Features**: GraphQL, scheduled jobs, webhooks
3. **Format Extensions**: Parquet, Avro, XML
4. **Visual Pipeline Designer**: Complete Vue Flow integration
5. **User Training**: Documentation and tutorials

---

## 8. Technology Decision Matrix

### Recommendation: Hybrid Approach

Given the analysis, I recommend:

1. **Use rdf-forge (Java) for Backend**: 
   - Better enterprise support (Spring ecosystem)
   - Apache Jena is mature and well-maintained
   - Apache Camel provides robust pipeline orchestration
   - Easier to scale with microservices

2. **Use rdf-forge-ui (Vue.js 3) for Frontend**:
   - Modern stack (Vite, Pinia, PrimeVue)
   - Vue Flow for pipeline visualization
   - Monaco Editor for code editing
   - Better maintained UI components

3. **Keep lindas-barnard59 packages as reference**:
   - Port operation logic to Java equivalents
   - Maintain RDF pipeline definition compatibility

4. **Deprecate lindas-cube-creator UI**:
   - Old Vue CLI setup
   - Vuex (deprecated in favor of Pinia)
   - Oruga/Bulma less maintained than PrimeVue

---

## Appendix A: File Statistics

| Directory | Files | Lines of Code (est.) |
|-----------|-------|---------------------|
| lindas-cube-creator | ~500 | ~50,000 |
| lindas-barnard59 | ~150 | ~10,000 |
| rdf-forge | ~200 | ~15,000 |
| **Total** | ~850 | ~75,000 |

## Appendix B: Dependency Summary

### Java Dependencies (rdf-forge)
- Spring Boot 3.2.5
- Apache Jena 5.0.0
- Apache Camel 4.5.0
- MinIO 8.5.9
- Flyway 10.10.0

### Node.js Dependencies (lindas-*)
- Vue.js 3.x
- Express 4.x
- Barnard59 5.x
- RDF/JS ecosystem
- TypeScript 4.5/5.4

---

## Conclusion

The cube-validator-x project represents a comprehensive effort to create a production-ready RDF data platform. The presence of both TypeScript (lindas-*) and Java (rdf-forge) implementations indicates a strategic transition towards a more maintainable and scalable architecture.

**Key Takeaway**: The "Java drop-in replacement" mentioned refers to the **rdf-forge** directory, which is indeed a Java 21 + Spring Boot 3 implementation designed to replace the TypeScript-based Zazuko tools.

The project requires consolidation of the two approaches and focused effort on:
1. Completing the Java backend implementation
2. Achieving comprehensive test coverage
3. Security hardening
4. Performance optimization
5. Documentation and migration tooling