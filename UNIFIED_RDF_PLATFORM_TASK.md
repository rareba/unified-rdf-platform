# Task: Build Unified RDF Data Platform

## Executive Summary

Create a next-generation, unified platform that combines the best capabilities of **Cube Creator** (user-friendly RDF cube generation) and **Barnard59** (powerful ETL pipeline engine) into a single, high-performance, user-friendly system for converting tabular and document data into RDF format and managing RDF triplestores.

## Current State Analysis

### Cube Creator (lindas-cube-creator)

**Strengths:**
- Excellent user interface (Vue.js 3) with guided workflow
- Visual cube designer and dimension management
- Hydra-based declarative API
- Integration with OIDC authentication
- S3 storage for CSV files
- Job monitoring and status tracking
- SHACL-based form generation
- Support for shared dimensions and hierarchies
- Clear separation of concerns (API, UI, CLI)

**Weaknesses:**
- Limited to CSV input only
- Tightly coupled to specific workflow (upload → map → transform → publish)
- Heavy infrastructure requirements (multiple Docker containers)
- Pipeline logic embedded in Cube Creator CLI (not reusable)
- Limited extensibility for custom transformations
- No support for document formats (JSON, Parquet, XML, etc.)
- Complex deployment and configuration
- Performance bottlenecks with large datasets

**Technical Stack:**
- TypeScript, Vue.js 3, Hydra-box, Express
- Barnard59 (used internally for transform pipeline)
- RDF/JS, SPARQL, SHACL
- Docker, S3, Fuseki, Minio
- OpenTelemetry, Sentry

---

### Barnard59 (lindas-barnard59)

**Strengths:**
- Powerful, declarative pipeline definition (RDF-based)
- Stream-based processing for memory efficiency
- Highly extensible (plugin architecture)
- Rich set of operations (HTTP, S3, FTP, SPARQL, formats)
- Support for multiple formats (CSV, CSVW, JSON-LD, RDF/XML, N-Triples, XLSX)
- SHACL and cube validation
- OpenTelemetry integration
- Type-safe with TypeScript
- Modular package structure

**Weaknesses:**
- No user interface (CLI only)
- Steep learning curve (requires RDF knowledge)
- Pipeline definition requires writing RDF/Turtle
- No visual tools for pipeline creation
- Limited error reporting for non-technical users
- No built-in job management system
- No authentication or multi-user support
- No persistence of pipeline configurations

**Technical Stack:**
- TypeScript, Node.js 16+
- RDF/JS, Clownface, readable-stream
- CSVW, SHACL, Cube Schema
- Commander.js, Winston, OpenTelemetry

---

## Overlapping Functionality & Integration Points

### Common Areas
1. **RDF Processing**: Both use RDF/JS, Clownface, and SPARQL
2. **Transformation Pipelines**: Cube Creator uses Barnard59 internally
3. **CSV Handling**: Both process CSV files (Cube Creator via CSVW in Barnard59)
4. **SHACL Validation**: Both validate RDF data against shapes
5. **Cube Generation**: Both create Linked Data Cubes
6. **Graph Store Integration**: Both publish to SPARQL endpoints
7. **OpenTelemetry**: Both support observability
8. **TypeScript**: Both use TypeScript for type safety

### Synergies
- Cube Creator's UI + Barnard59's engine = Powerful visual ETL
- Cube Creator's metadata management + Barnard59's extensibility = Flexible data modeling
- Cube Creator's job system + Barnard59's pipeline validation = Robust execution
- Cube Creator's authentication + Barnard59's operations = Secure multi-user ETL

### Integration Opportunities
- Use Barnard59 as the **core engine** for all transformations
- Build Cube Creator's UI as a **visual pipeline designer** for Barnard59
- Extend Barnard59 with Cube Creator's dimension management
- Unify metadata storage using Hydra API patterns
- Consolidate authentication across all components
- Create unified job management system

---

## Vision: Unified RDF Data Platform

### Name Suggestion
**RDF Forge** or **Linked Data Studio** or **Semantic Data Hub**

### Core Principles
1. **User-Friendly**: Intuitive UI for all user levels (beginner to expert)
2. **Performant**: Stream-based, efficient processing of large datasets
3. **Extensible**: Plugin architecture for custom operations
4. **Declarative**: Pipeline-as-code with visual designer
5. **Standards-Compliant**: W3C standards (RDF, CSVW, SHACL, SPARQL)
6. **Multi-Format**: Support CSV, JSON, Parquet, XML, XLSX, and more
7. **Enterprise-Ready**: Authentication, multi-tenancy, audit logs
8. **Cloud-Native**: Containerized, scalable, S3-compatible storage

### Key Capabilities
1. **Visual Pipeline Designer**: Drag-and-drop interface for creating ETL pipelines
2. **Multi-Format Support**: Tabular (CSV, XLSX, Parquet) and document (JSON, XML) formats
3. **Smart Transformations**: Guided wizards for common tasks + advanced pipeline editor
4. **Dimension Management**: Shared dimensions, hierarchies, dictionaries
5. **Validation Engine**: SHACL, cube validation, custom rules
6. **Job Orchestration**: Schedule, monitor, retry, and debug jobs
7. **Triplestore Management**: Connect to Stardog, GraphDB, Fuseki, Neptune
8. **API-First**: RESTful + GraphQL APIs for programmatic access
9. **Collaborative**: Multi-user, role-based access, version control
10. **Observability**: Full tracing, metrics, and logging

---

## Technical Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      USER INTERFACES                         │
├──────────────┬──────────────┬──────────────┬────────────────┤
│  Web UI      │  CLI Tool    │  REST API    │  GraphQL API   │
│  (Vue.js 3)  │  (Commander) │  (Express)   │  (Apollo)      │
└──────────────┴──────────────┴──────────────┴────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      API GATEWAY LAYER                       │
├─────────────────────────────────────────────────────────────┤
│  • Authentication (OIDC, OAuth2, API Keys)                   │
│  • Authorization (RBAC)                                      │
│  • Rate Limiting                                             │
│  • Request Validation                                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                         │
├──────────────┬──────────────┬──────────────┬────────────────┤
│  Pipeline    │  Job         │  Metadata    │  Dimension     │
│  Service     │  Service     │  Service     │  Service       │
└──────────────┴──────────────┴──────────────┴────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    CORE ENGINE LAYER                         │
├─────────────────────────────────────────────────────────────┤
│  • Barnard59 Pipeline Engine (extended)                      │
│  • Stream Processing Framework                               │
│  • Operation Registry                                        │
│  • Plugin Manager                                            │
│  • Validation Engine                                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    DATA ACCESS LAYER                         │
├──────────────┬──────────────┬──────────────┬────────────────┤
│  Triplestore │  File        │  S3          │  External      │
│  Connector   │  System      │  Storage     │  APIs          │
└──────────────┴──────────────┴──────────────┴────────────────┘
```

### Component Breakdown

#### 1. Web UI (Vue.js 3 + TypeScript)

**Features:**
- **Dashboard**: Project overview, recent jobs, system health
- **Pipeline Designer**: Visual drag-and-drop pipeline builder
  - Operation palette (all Barnard59 operations)
  - Connection validation
  - Variable management
  - Preview mode
- **Data Source Management**: Upload, browse, preview data sources
- **Transformation Wizards**: Guided flows for common tasks
  - CSV to RDF Cube
  - JSON to RDF
  - Data enrichment
  - Validation
- **Dimension Studio**: Manage shared dimensions, hierarchies, dictionaries
- **Job Monitor**: Real-time status, logs, metrics, debugging
- **Triplestore Manager**: Browse, query, manage graphs
- **Settings**: Authentication, integrations, system configuration

**Technologies:**
- Vue.js 3 with Composition API
- Vuex or Pinia for state management
- Vue Router for routing
- Vue Flow or React Flow (via wrapper) for pipeline visualization
- Monaco Editor for code editing (RDF, SPARQL)
- Oruga + Bulma or Vuetify for UI components
- Axios for API calls
- Socket.io for real-time updates

#### 2. API Layer (Express + TypeScript)

**Design Pattern**: Hybrid Hydra + REST + GraphQL

**Endpoints:**

**Hydra Resources:**
- `/projects` - Project CRUD
- `/pipelines` - Pipeline definitions
- `/jobs` - Job execution and monitoring
- `/dimensions` - Shared dimension management
- `/data-sources` - File and data source management

**REST API:**
- `POST /api/v1/pipelines/validate` - Validate pipeline definition
- `POST /api/v1/pipelines/execute` - Execute pipeline immediately
- `GET /api/v1/jobs/{id}/logs` - Stream job logs
- `GET /api/v1/operations` - List available operations
- `POST /api/v1/data-sources/upload` - Upload files to S3
- `GET /api/v1/triplestores` - List connected triplestores
- `POST /api/v1/sparql` - Execute SPARQL queries

**GraphQL API:**
- Unified schema for querying projects, pipelines, jobs
- Subscriptions for real-time job status
- Mutations for CRUD operations

**Authentication:**
- OIDC/OAuth2 for Web UI
- API Keys for CLI and programmatic access
- JWT tokens for session management

#### 3. Pipeline Engine (Extended Barnard59)

**Core Extensions:**

1. **Enhanced Operation Registry**
   - Dynamic operation discovery
   - Versioned operations
   - Operation metadata (inputs, outputs, parameters)
   - Category and tag-based organization

2. **Pipeline Validation Service**
   - Schema validation (RDF structure)
   - Type compatibility checking
   - Circular dependency detection
   - Resource availability verification
   - Performance estimation

3. **Job Execution Framework**
   - Queue-based execution (Bull or BullMQ)
   - Retry logic with exponential backoff
   - Job prioritization
   - Parallel job execution
   - Resource limits (memory, CPU)
   - Cancellation support

4. **Variable Resolution System**
   - Environment variables
   - Secret management (Vault integration)
   - Dynamic variable computation
   - Variable validation

5. **Error Handling & Recovery**
   - Structured error messages
   - Error categorization (retryable vs fatal)
   - Partial result recovery
   - Checkpoint and resume support

6. **Monitoring & Observability**
   - OpenTelemetry integration
   - Metrics (throughput, latency, errors)
   - Distributed tracing
   - Performance profiling

#### 4. Data Services

**File Service:**
- Upload/download files
- S3-compatible storage (MinIO, AWS S3, Azure Blob)
- File versioning
- Automatic format detection
- Preview generation (first N rows)

**Metadata Service:**
- RDF metadata storage (triplestore)
- CSVW metadata management
- Dimension definitions
- Cube metadata
- Data lineage tracking

**Dimension Service:**
- Shared dimension CRUD
- Hierarchy management (SKOS)
- Dictionary management (PROV)
- Dimension value lookup and caching
- Dimension versioning

**Triplestore Connector:**
- Multi-triplestore support:
  - Stardog
  - GraphDB
  - Apache Fuseki
  - Amazon Neptune
  - Blazegraph
  - Virtuoso
- Connection pooling
- Query optimization
- Bulk loading strategies
- Transaction support

#### 5. CLI Tool (Commander.js)

**Commands:**

```bash
# Pipeline management
rdf-forge pipeline create <file>
rdf-forge pipeline validate <file>
rdf-forge pipeline run <file> [--var key=value]
rdf-forge pipeline list
rdf-forge pipeline export <id> --output <file>

# Job management
rdf-forge job run <pipeline-id> [--schedule cron]
rdf-forge job status <job-id>
rdf-forge job logs <job-id> [--follow]
rdf-forge job cancel <job-id>
rdf-forge job retry <job-id>

# Data source management
rdf-forge data upload <file> [--format csv|json|parquet]
rdf-forge data list
rdf-forge data preview <id>
rdf-forge data delete <id>

# Dimension management
rdf-forge dimension create <file>
rdf-forge dimension list
rdf-forge dimension export <id>

# Triplestore operations
rdf-forge store connect <url> --type stardog|graphdb|fuseki
rdf-forge store query <query-file> [--format json|csv|turtle]
rdf-forge store load <file> --graph <uri>

# System operations
rdf-forge config set <key> <value>
rdf-forge config get <key>
rdf-forge version
rdf-forge doctor  # System health check
```

---

## Data Flow & Processing Model

### 1. CSV to RDF Cube (Simplified Workflow)

```
User Uploads CSV
      │
      ▼
File Service (S3 Storage)
      │
      ▼
Auto-generate CSVW metadata
      │
      ▼
User Maps Columns (UI Wizard)
  • Select dimension properties
  • Choose literal vs reference
  • Configure datatypes
      │
      ▼
Create Pipeline Definition (RDF)
  • Load CSV step
  • Parse CSVW step
  • Map dimensions step
  • Create observations step
  • Validate SHACL step
  • Output to triplestore step
      │
      ▼
Job Service (Queue Execution)
      │
      ▼
Barnard59 Engine Execution
  • Stream CSV from S3
  • Apply transformations
  • Generate RDF quads
  • Validate
  • Batch upload to triplestore
      │
      ▼
Completion (Notification)
```

### 2. JSON to RDF (Custom Transformation)

```
User Uploads JSON
      │
      ▼
Pipeline Designer (Visual Editor)
  • Add "Load JSON" operation
  • Add "Parse JSON" operation
  • Add "Map to RDF" operation (custom)
  • Add "Validate SHACL" operation
  • Add "Graph Store PUT" operation
      │
      ▼
Configure Mapping Rules (UI Form)
  • JSON path → RDF property mapping
  • Type conversions
  • Nested object handling
      │
      ▼
Save Pipeline Definition
      │
      ▼
Execute Job
      │
      ▼
Stream Processing
  • Parse JSON (streaming)
  • Extract values
  • Generate RDF quads
  • Validate
  • Upload
      │
      ▼
Completion
```

### 3. Scheduled Data Sync

```
User Creates Pipeline
      │
      ▼
Configure Schedule (Cron)
      │
      ▼
Job Scheduler (Background Service)
      │
      ├─► Trigger at scheduled time
      │
      ▼
Execute Pipeline
  • Fetch data from external API (HTTP GET)
  • Transform to RDF
  • Update triplestore (SPARQL UPDATE)
      │
      ▼
Send Notification (Email/Webhook)
```

---

## Key Features & User Stories

### Feature 1: Visual Pipeline Designer

**User Story:**
> As a data engineer, I want to create ETL pipelines visually without writing code, so I can quickly prototype and iterate on data transformations.

**Acceptance Criteria:**
- Drag-and-drop operation nodes from palette
- Connect operations with visual edges
- Configure operation parameters via forms
- Validate pipeline in real-time
- Preview intermediate results
- Export pipeline as RDF/Turtle
- Import existing pipelines
- Version control for pipelines

**Technical Implementation:**
- Vue Flow for graph visualization
- Monaco Editor for code view
- Real-time validation service
- RDF/Turtle parser and serializer
- Operation metadata schema

---

### Feature 2: Smart CSV to Cube Wizard

**User Story:**
> As a non-technical user, I want to upload a CSV and automatically generate an RDF cube with minimal configuration.

**Acceptance Criteria:**
- Upload CSV via drag-and-drop
- Auto-detect column types and datatypes
- Suggest dimension vs measure classification
- Auto-generate observation identifiers
- Preview generated cube (first 10 observations)
- One-click publish to triplestore
- Download as N-Triples/Turtle/JSON-LD

**Technical Implementation:**
- CSVW metadata generation
- Machine learning for column classification (optional)
- Barnard59 cube pipeline template
- SHACL shape generation
- Preview rendering (table view)

---

### Feature 3: Multi-Format Support

**User Story:**
> As a data analyst, I want to convert JSON, Parquet, XML, and other formats to RDF, not just CSV.

**Acceptance Criteria:**
- Support formats: CSV, XLSX, JSON, JSON-LD, XML, Parquet, Avro
- Auto-detect file format
- Format-specific mapping UI
- Preserve data types and structures
- Handle nested data (JSON, XML)
- Stream large files efficiently

**Technical Implementation:**
- Extend Barnard59 with new format parsers:
  - `barnard59-parquet` package
  - `barnard59-xml` package
  - `barnard59-avro` package
- Format detection library (file-type)
- Streaming parsers for all formats
- Nested data flattening strategies

---

### Feature 4: Shared Dimension Management

**User Story:**
> As a data steward, I want to create and maintain shared dimensions that multiple cubes can reference, ensuring consistency across datasets.

**Acceptance Criteria:**
- Create dimensions with properties
- Define dimension hierarchies (SKOS)
- Import dimensions from external sources
- Version dimensions
- Track dimension usage across cubes
- Validate dimension references
- Export dimensions in SKOS/RDF

**Technical Implementation:**
- Dimension RDF model (extend Cube Creator)
- SKOS hierarchy editor
- Dimension versioning system
- Reference tracking service
- Validation rules engine

---

### Feature 5: Job Monitoring & Debugging

**User Story:**
> As a developer, I want detailed logs and metrics for pipeline executions so I can debug failures and optimize performance.

**Acceptance Criteria:**
- Real-time job status updates
- Structured logs with log levels
- Per-step execution metrics (duration, throughput)
- Error stack traces with context
- Retry failed jobs
- Cancel running jobs
- Download logs as file
- Visualize pipeline execution flow

**Technical Implementation:**
- WebSocket for real-time updates
- Winston logging with structured output
- OpenTelemetry tracing
- Job queue (BullMQ) with Redis
- Log storage (Elasticsearch or Loki)
- Grafana dashboards for metrics

---

### Feature 6: Triplestore Management

**User Story:**
> As an RDF administrator, I want to manage multiple triplestores (Stardog, GraphDB) from a single interface.

**Acceptance Criteria:**
- Connect to multiple triplestores
- List graphs in each store
- Execute SPARQL queries
- Visualize query results
- Bulk load RDF files
- Export graphs
- Monitor triplestore health
- Configure connection settings

**Technical Implementation:**
- Triplestore adapter pattern
- Connection pooling per store
- SPARQL query builder UI (YASGUI integration)
- Result formatters (table, graph, JSON)
- Bulk loader with batching
- Health check endpoints

---

### Feature 7: Data Validation & Quality

**User Story:**
> As a data quality manager, I want to validate RDF data against SHACL shapes and custom rules before publishing.

**Acceptance Criteria:**
- Define SHACL shapes visually or in Turtle
- Run validation as pipeline step
- Generate validation reports
- Fail pipeline on validation errors
- Custom validation rules (JavaScript functions)
- Batch validation for large datasets
- Validation result visualization

**Technical Implementation:**
- SHACL validation operation (barnard59-shacl)
- Shape editor UI (Monaco Editor with SHACL syntax)
- Validation report model (RDF)
- Custom rule execution framework
- Report rendering (violations table)

---

### Feature 8: API-First Design

**User Story:**
> As an application developer, I want to integrate the platform into my workflows using RESTful and GraphQL APIs.

**Acceptance Criteria:**
- RESTful API with OpenAPI documentation
- GraphQL API with schema introspection
- API authentication (API keys, OAuth2)
- Rate limiting per client
- Webhooks for job completion
- SDK libraries (JavaScript, Python)
- API playground (Swagger UI, GraphiQL)

**Technical Implementation:**
- Express routes with OpenAPI annotations
- Apollo Server for GraphQL
- API key management service
- Rate limiter middleware (express-rate-limit)
- Webhook delivery system
- Client SDK generators (OpenAPI Generator)

---

## Technology Stack Recommendations

### Backend
- **Runtime**: Node.js 20+ LTS
- **Language**: TypeScript 5+
- **API Framework**: Express.js 4+ or Fastify 4+
- **GraphQL**: Apollo Server 4+
- **Job Queue**: BullMQ + Redis
- **Authentication**: Passport.js (OIDC, OAuth2), jsonwebtoken
- **Validation**: Ajv (JSON Schema), Zod (TypeScript)
- **Logging**: Winston + Morgan
- **Observability**: OpenTelemetry, Prometheus, Grafana
- **Testing**: Mocha + Chai + Sinon, Supertest
- **Pipeline Engine**: Barnard59 (extended)
- **RDF Libraries**: RDF/JS, Clownface, SPARQL HTTP Client
- **Storage**: S3-compatible (MinIO, AWS S3), Redis

### Frontend
- **Framework**: Vue.js 3 (Composition API)
- **State**: Pinia (Vuex successor)
- **Router**: Vue Router 4
- **UI Components**: Vuetify 3 or PrimeVue
- **Pipeline Visualization**: Vue Flow or D3.js
- **Code Editor**: Monaco Editor
- **Charts**: Chart.js or ECharts
- **Forms**: VeeValidate + Yup
- **HTTP Client**: Axios
- **Real-time**: Socket.io-client
- **Testing**: Vitest + Vue Test Utils, Cypress
- **Build**: Vite

### Infrastructure
- **Containerization**: Docker + Docker Compose
- **Orchestration**: Kubernetes (optional, for production)
- **CI/CD**: GitHub Actions or GitLab CI
- **Triplestore**: Stardog, GraphDB, or Fuseki (configurable)
- **Object Storage**: MinIO (local), S3 (cloud)
- **Cache**: Redis
- **Monitoring**: Prometheus + Grafana + Jaeger

### Development
- **Package Manager**: npm or pnpm
- **Monorepo**: npm workspaces or Turborepo
- **Linting**: ESLint
- **Formatting**: Prettier
- **Git Hooks**: Husky + lint-staged
- **Changesets**: Changesets for versioning
- **Documentation**: Markdown + VitePress or Docusaurus

---

## Implementation Phases

### Phase 1: Foundation (Weeks 1-4)
**Goal**: Set up project structure and core engine

**Tasks:**
1. Initialize monorepo structure
2. Extract and extend Barnard59 core engine
3. Create unified RDF models for pipelines, jobs, dimensions
4. Set up API framework (Express + GraphQL)
5. Implement authentication and authorization
6. Set up development environment (Docker Compose)
7. Create CI/CD pipelines

**Deliverables:**
- Project repository with CI/CD
- Core API skeleton
- Extended Barnard59 engine
- Docker development environment

---

### Phase 2: Core Features (Weeks 5-10)
**Goal**: Implement essential functionality

**Tasks:**
1. Build File Service (S3 integration)
2. Implement Pipeline Service (CRUD, validation)
3. Create Job Service (queue, execution, monitoring)
4. Build Metadata Service (dimension management)
5. Implement Triplestore Connector (Stardog, GraphDB, Fuseki)
6. Create CLI tool with basic commands
7. Develop REST API endpoints
8. Implement OpenTelemetry observability

**Deliverables:**
- Functional API layer
- Working CLI tool
- Job execution system
- Triplestore integration
- Observability dashboard

---

### Phase 3: UI Development (Weeks 11-16)
**Goal**: Build user interface

**Tasks:**
1. Set up Vue.js 3 project structure
2. Implement authentication UI (OIDC login)
3. Build Dashboard view
4. Create Pipeline Designer (visual editor)
5. Build CSV to Cube Wizard
6. Implement Data Source Manager
7. Create Job Monitor view
8. Build Dimension Studio
9. Implement Settings and Configuration UI

**Deliverables:**
- Functional Web UI
- Visual pipeline designer
- Guided transformation wizards
- Job monitoring dashboard

---

### Phase 4: Advanced Features (Weeks 17-22)
**Goal**: Add advanced capabilities

**Tasks:**
1. Implement multi-format support (JSON, Parquet, XML)
2. Build SHACL shape editor and validator
3. Create GraphQL API
4. Implement webhook system
5. Build pipeline templates library
6. Add version control for pipelines
7. Implement scheduled job execution
8. Create SDK libraries (JavaScript, Python)
9. Build SPARQL query builder UI

**Deliverables:**
- Multi-format support
- Advanced validation
- GraphQL API
- Pipeline templates
- Scheduling system

---

### Phase 5: Enterprise Features (Weeks 23-28)
**Goal**: Production readiness

**Tasks:**
1. Implement multi-tenancy
2. Add role-based access control (RBAC)
3. Build audit logging system
4. Create backup and restore functionality
5. Implement data lineage tracking
6. Add performance monitoring and alerts
7. Create comprehensive documentation
8. Build admin dashboard
9. Implement enterprise connectors (LDAP, SAML)

**Deliverables:**
- Multi-tenant system
- RBAC and audit logs
- Backup/restore
- Enterprise authentication
- Production documentation

---

### Phase 6: Testing & Optimization (Weeks 29-32)
**Goal**: Quality assurance and performance optimization

**Tasks:**
1. Comprehensive unit testing (>80% coverage)
2. Integration testing
3. End-to-end testing (Cypress)
4. Performance testing and optimization
5. Security audit and penetration testing
6. Load testing (large datasets)
7. User acceptance testing (UAT)
8. Bug fixes and refinements

**Deliverables:**
- Test coverage >80%
- Performance benchmarks
- Security audit report
- Optimized system

---

### Phase 7: Deployment & Documentation (Weeks 33-36)
**Goal**: Production deployment and knowledge transfer

**Tasks:**
1. Create deployment guides (Docker, Kubernetes)
2. Write user documentation
3. Create video tutorials
4. Build example projects and templates
5. Set up staging environment
6. Deploy to production
7. Create migration guide from Cube Creator/Barnard59
8. Establish support processes

**Deliverables:**
- Deployment documentation
- User guides and tutorials
- Migration tools
- Production system
- Support infrastructure

---

## Success Criteria

### Functional Requirements
1. Support CSV, JSON, XLSX, Parquet, XML formats
2. Generate RDF cubes following Cube Schema
3. Visual pipeline designer with 50+ operations
4. Job execution with monitoring and retry
5. Multi-triplestore support (Stardog, GraphDB, Fuseki)
6. Dimension and hierarchy management
7. SHACL validation
8. RESTful and GraphQL APIs
9. CLI tool with all major operations
10. OIDC/OAuth2 authentication

### Non-Functional Requirements
1. **Performance**: Process 1M rows in <5 minutes
2. **Scalability**: Support 100+ concurrent jobs
3. **Reliability**: 99.9% uptime
4. **Security**: OWASP Top 10 compliance
5. **Usability**: 90% of users complete CSV-to-cube in <10 minutes
6. **Maintainability**: Code coverage >80%
7. **Documentation**: All APIs and features documented
8. **Compatibility**: Support Node.js 20+, modern browsers

### User Satisfaction Metrics
1. Time to first RDF cube: <15 minutes for new users
2. System responsiveness: <2 seconds for UI interactions
3. Error resolution: Clear error messages with actionable steps
4. Learning curve: Non-technical users productive in <1 hour

---

## Migration Strategy

### From Cube Creator to Unified Platform

**Step 1: Data Export**
- Export projects, pipelines, and dimension definitions as RDF
- Download CSV files from S3
- Export cube metadata and shapes

**Step 2: Import to New Platform**
- Import projects via REST API
- Upload CSV files to new S3 storage
- Import dimension definitions
- Convert Cube Creator pipelines to Barnard59 format

**Step 3: Validation**
- Re-run transformations in new platform
- Compare RDF output (diff tool)
- Validate SHACL shapes
- Verify triplestore content

**Step 4: Cutover**
- Switch DNS/routing to new platform
- Archive old Cube Creator instance
- Monitor for issues
- Provide rollback plan

### From Barnard59 CLI to Unified Platform

**Step 1: Pipeline Migration**
- Import existing Turtle pipeline definitions
- Register custom operations in new platform
- Update variable references
- Test pipelines in new engine

**Step 2: Workflow Adaptation**
- Create UI-based workflows for common patterns
- Convert CLI scripts to API calls
- Set up scheduled jobs
- Configure notifications

**Step 3: Gradual Adoption**
- Run both systems in parallel
- Migrate pipelines incrementally
- Train users on new UI
- Deprecate old CLI

---

## Risk Assessment & Mitigation

### Risk 1: Complexity
**Impact**: High | **Probability**: High

**Mitigation:**
- Start with MVP (minimal viable product)
- Incremental delivery (phases)
- Continuous user feedback
- Comprehensive testing

### Risk 2: Performance Degradation
**Impact**: Medium | **Probability**: Medium

**Mitigation:**
- Stream-based processing (Barnard59 core)
- Performance benchmarking at each phase
- Load testing with large datasets
- Optimize hot paths (profiling)

### Risk 3: User Adoption
**Impact**: High | **Probability**: Medium

**Mitigation:**
- Involve users early (design feedback)
- Provide migration tools
- Comprehensive documentation and training
- Support both systems during transition

### Risk 4: Technical Debt
**Impact**: Medium | **Probability**: Medium

**Mitigation:**
- Code reviews and pair programming
- Refactoring sprints
- Maintain >80% test coverage
- Regular dependency updates

### Risk 5: Security Vulnerabilities
**Impact**: High | **Probability**: Low

**Mitigation:**
- Security audit at each phase
- Dependency scanning (Snyk, Dependabot)
- Input validation and sanitization
- Regular penetration testing

---

## Appendix A: RDF Vocabularies & Standards

### Core Standards
- **RDF**: Resource Description Framework (W3C)
- **RDFS**: RDF Schema
- **OWL**: Web Ontology Language
- **SPARQL**: SPARQL Protocol and RDF Query Language
- **Turtle**: Terse RDF Triple Language
- **JSON-LD**: JSON for Linking Data
- **N-Triples**: Line-based RDF syntax

### Domain Standards
- **QB**: RDF Data Cube Vocabulary
- **Cube Schema**: Modern Linked Data Cubes
- **CSVW**: CSV on the Web
- **SHACL**: Shapes Constraint Language
- **PROV**: Provenance Vocabulary
- **SKOS**: Simple Knowledge Organization System
- **DCAT**: Data Catalog Vocabulary
- **VoID**: Vocabulary of Interlinked Datasets

### API Standards
- **Hydra**: Hypermedia-Driven Web APIs
- **GraphQL**: Query Language for APIs
- **OpenAPI**: RESTful API Specification
- **SPARQL Graph Store Protocol**: HTTP Protocol for RDF Graphs

---

## Appendix B: Example Pipeline Definitions

### CSV to RDF Cube Pipeline (Turtle)

```turtle
@prefix p: <https://pipeline.described.at/> .
@prefix code: <https://code.described.at/> .
@prefix rdf-forge: <https://rdf-forge.io/pipeline/> .

<#pipeline> a p:Pipeline, p:Readable ;
  rdfs:label "CSV to RDF Cube" ;
  p:variables [
    p:variable [ p:name "csvFile" ; p:value "data.csv" ] ;
    p:variable [ p:name "cubeUri" ; p:value "http://example.org/cube/1" ] ;
  ] ;
  p:steps [
    p:stepList (
      <#loadCsv>
      <#parseCsvw>
      <#mapDimensions>
      <#createObservations>
      <#validateShape>
      <#publishToStore>
    )
  ] .

<#loadCsv> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:barnard59-s3#getObject>
  ] ;
  code:arguments (
    [ code:name "bucket" ; code:value "data-sources" ]
    [ code:name "key" ; code:value "${csvFile}" ]
  ) .

<#parseCsvw> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:barnard59-formats/csvw#parse>
  ] ;
  code:arguments (
    [ code:name "metadata" ; code:value "metadata.json" ]
  ) .

<#mapDimensions> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:rdf-forge-transforms#mapDimensions>
  ] ;
  code:arguments (
    [ code:name "dimensionService" ; code:value "http://api/dimensions" ]
  ) .

<#createObservations> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:barnard59-cube#toObservation>
  ] ;
  code:arguments (
    [ code:name "cube" ; code:value "${cubeUri}" ]
  ) .

<#validateShape> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:barnard59-shacl#validate>
  ] ;
  code:arguments (
    [ code:name "shape" ; code:value "cube-shape.ttl" ]
    [ code:name "onViolation" ; code:value "error" ]
  ) .

<#publishToStore> a p:Step ;
  code:implementedBy [ a code:EcmaScriptModule ;
    code:link <node:barnard59-graph-store#put>
  ] ;
  code:arguments (
    [ code:name "endpoint" ; code:value "http://stardog:5820/database" ]
    [ code:name "graph" ; code:value "${cubeUri}" ]
    [ code:name "user" ; code:value "admin" ]
    [ code:name "password" ; code:value "${STARDOG_PASSWORD}" ]
  ) .
```

---

## Appendix C: API Examples

### REST API: Create Pipeline

```http
POST /api/v1/pipelines
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Sales Data Transformation",
  "description": "Convert quarterly sales CSV to RDF cube",
  "definition": {
    "type": "turtle",
    "content": "<pipeline turtle definition>"
  },
  "variables": {
    "csvFile": "sales-q1-2025.csv",
    "cubeUri": "http://example.org/cube/sales-q1-2025"
  },
  "tags": ["sales", "quarterly", "cube"]
}
```

**Response:**
```json
{
  "id": "pipeline-123",
  "name": "Sales Data Transformation",
  "status": "active",
  "createdAt": "2025-01-15T10:30:00Z",
  "createdBy": "user-456",
  "validation": {
    "valid": true,
    "warnings": []
  }
}
```

### GraphQL: Query Jobs

```graphql
query GetRecentJobs {
  jobs(limit: 10, status: [RUNNING, COMPLETED]) {
    id
    pipeline {
      id
      name
    }
    status
    progress
    startedAt
    completedAt
    metrics {
      rowsProcessed
      quadsGenerated
      duration
    }
    errors {
      message
      timestamp
      step
    }
  }
}
```

---

## Conclusion

This unified RDF data platform represents a significant advancement over the current separate systems. By combining Cube Creator's user-friendly interface with Barnard59's powerful engine and extending both with new capabilities, the platform will provide:

1. **Accessibility**: Non-technical users can create RDF data through guided wizards
2. **Power**: Advanced users can build complex ETL pipelines visually or via code
3. **Performance**: Stream-based processing handles datasets of any size efficiently
4. **Extensibility**: Plugin architecture allows custom operations and formats
5. **Enterprise-Ready**: Authentication, multi-tenancy, audit logs, and monitoring
6. **Standards-Compliant**: Full adherence to W3C semantic web standards

The phased implementation approach ensures incremental value delivery while managing risk. The comprehensive migration strategy allows smooth transition from existing systems with minimal disruption.

**Next Steps for Implementation Team:**
1. Review and refine this specification
2. Set up development environment and repository
3. Begin Phase 1 (Foundation) implementation
4. Establish regular stakeholder feedback loops
5. Create detailed technical design documents for each component

**Estimated Timeline:** 36 weeks (9 months)
**Team Size:** 4-6 full-stack developers + 1 UX designer + 1 DevOps engineer
**Budget Estimate:** Contact for detailed breakdown
