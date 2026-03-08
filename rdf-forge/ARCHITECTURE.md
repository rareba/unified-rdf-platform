# RDF Forge - System Architecture

## Overview

RDF Forge is a comprehensive RDF data transformation and publishing platform designed for creating, managing, and publishing Linked Data cubes. The architecture follows a microservices pattern with clear separation of concerns, enabling scalability, maintainability, and extensibility.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Angular SPA (RDF Forge UI)                    │   │
│  │  - Dashboard, Pipeline Designer, Cube Wizard, Data Manager      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTPS/WebSocket
┌───────────────────────────────────▼─────────────────────────────────────┐
│                           Gateway Layer                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              Spring Cloud Gateway (Port 8000)                    │   │
│  │  - Routing, Load Balancing, Authentication, Rate Limiting       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ Internal Network
┌───────────────────────────────────▼─────────────────────────────────────┐
│                         Microservices Layer                              │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │   Pipeline   │  │    SHACL     │  │     Job      │  │    Data     │ │
│  │   Service    │  │   Service    │  │   Service    │  │   Service   │ │
│  │   :8001      │  │    :8002     │  │    :8003     │  │    :8004    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘ │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │  Dimension   │  │ Triplestore  │  │     Auth     │                   │
│  │   Service    │  │   Service    │  │   Service    │                   │
│  │   :8005      │  │    :8006     │  │   :8086      │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                          Data Layer                                      │
│                                                                          │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐             │
│  │   PostgreSQL   │  │     Redis      │  │     MinIO      │             │
│  │   (Primary)    │  │   (Caching)    │  │  (Object Store)│             │
│  └────────────────┘  └────────────────┘  └────────────────┘             │
│                                                                          │
│  ┌────────────────┐  ┌────────────────┐                                  │
│  │    Fuseki      │  │    Keycloak    │                                  │
│  │ (Triplestore)  │  │     (Auth)     │                                  │
│  └────────────────┘  └────────────────┘                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Service Descriptions

### Gateway Service (Port 8000)
**Technology:** Spring Cloud Gateway

**Responsibilities:**
- Single entry point for all client requests
- JWT token validation
- Request routing to appropriate microservices
- Load balancing
- CORS configuration
- Rate limiting

**Key Components:**
- `SecurityConfig`: Security configuration
- `PatAuthenticationFilter`: Personal Access Token authentication
- `AuthenticationFilter`: JWT validation

---

### Pipeline Service (Port 8001)
**Technology:** Spring Boot

**Responsibilities:**
- Pipeline CRUD operations
- Pipeline versioning
- Operation registry
- Pipeline validation
- Pipeline execution triggers

**Key Entities:**
- `Pipeline`: Pipeline definition and metadata
- `PipelineVersion`: Version history
- `Operation`: Available operations registry

---

### SHACL Service (Port 8002)
**Technology:** Spring Boot, Apache Jena

**Responsibilities:**
- SHACL shape management
- RDF validation
- Shape templates
- Validation reporting

**Key Components:**
- `ShaclValidator`: Validation engine
- `ShapeController`: Shape management API

---

### Job Service (Port 8003)
**Technology:** Spring Boot, Redis, WebSocket

**Responsibilities:**
- Job execution management
- Real-time log streaming (WebSocket)
- Job scheduling (cron)
- Job monitoring and metrics

**Key Entities:**
- `Job`: Job execution instance
- `JobLog`: Log entries
- `JobSchedule`: Scheduled job configuration

**WebSocket:**
- Endpoint: `/ws`
- STOMP protocol
- Topics: `/topic/jobs/{jobId}/logs`

---

### Data Service (Port 8004)
**Technology:** Spring Boot, MinIO, PostgreSQL

**Responsibilities:**
- File upload/download
- Data preview
- Format conversion (CSV, JSON, Parquet)
- Object storage management

**Key Components:**
- `DataSource`: Metadata about uploaded files
- `StorageService`: MinIO integration

---

### Dimension Service (Port 8005)
**Technology:** Spring Boot

**Responsibilities:**
- Dimension management
- Cube dimension mapping
- Dimensional analysis
- Integration with SHACL and Pipeline services

---

### Triplestore Service (Port 8006)
**Technology:** Spring Boot, Apache Jena

**Responsibilities:**
- Triplestore connection management
- SPARQL query execution
- Graph management
- Publishing to external triplestores

**Supported Triplestores:**
- Apache Fuseki
- GraphDB
- Stardog
- Virtuoso
- BlazeGraph

---

### Auth Service (Port 8086)
**Technology:** Spring Boot, Keycloak

**Responsibilities:**
- Personal Access Token (PAT) management
- User authentication
- Role management
- Token validation

---

## Data Flow

### Pipeline Execution Flow

```
User -> Gateway -> Pipeline Service -> Job Service
                                      |
                                      v
Redis (Job Queue) -> Pipeline Engine -> Triplestore Service
                                             |
                                             v
                                        Data Service
                                             |
                                             v
                                          MinIO
```

### Data Upload Flow

```
User -> Gateway -> Data Service -> MinIO (Store file)
                     |
                     v
               PostgreSQL (Metadata)
```

### Cube Creation Flow

```
User -> Cube Wizard -> Pipeline Service -> Pipeline Definition
                            |
                            v
                     Job Service (Execute)
                            |
                            v
                     Data Service (Load Data)
                            |
                            v
                     Triplestore Service (Publish)
```

---

## Technology Stack

### Backend
| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.x |
| Language | Java 21 (LTS) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Object Storage | MinIO |
| Authentication | Keycloak |
| Message Broker | Redis Pub/Sub |
| Build Tool | Maven 3.9 |

### Frontend
| Component | Technology |
|-----------|------------|
| Framework | Angular 18 |
| Language | TypeScript 5.x |
| UI Library | Angular Material |
| Styling | SCSS, Tailwind CSS |
| State | Angular Signals |
| Testing | Karma, Jasmine |
| E2E Testing | Playwright |

### Infrastructure
| Component | Technology |
|-----------|------------|
| Container | Docker |
| Orchestration | Docker Compose / Kubernetes |
| Gateway | Spring Cloud Gateway |
| Monitoring | Prometheus, Grafana |
| Logging | ELK Stack |

---

## Database Schema

### Core Tables

#### pipelines
- `id`: UUID (PK)
- `name`: VARCHAR(255)
- `description`: TEXT
- `definition`: JSONB
- `status`: ENUM('draft', 'active', 'archived')
- `created_by`: UUID
- `created_at`: TIMESTAMP
- `updated_at`: TIMESTAMP

#### jobs
- `id`: UUID (PK)
- `pipeline_id`: UUID (FK)
- `status`: ENUM('pending', 'running', 'completed', 'failed', 'cancelled')
- `progress`: INTEGER
- `variables`: JSONB
- `metrics`: JSONB
- `created_at`: TIMESTAMP
- `started_at`: TIMESTAMP
- `completed_at`: TIMESTAMP

#### job_logs
- `id`: UUID (PK)
- `job_id`: UUID (FK)
- `level`: ENUM('debug', 'info', 'warn', 'error')
- `message`: TEXT
- `step`: VARCHAR(255)
- `timestamp`: TIMESTAMP

#### data_sources
- `id`: UUID (PK)
- `name`: VARCHAR(255)
- `original_filename`: VARCHAR(255)
- `format`: VARCHAR(50)
- `size_bytes`: BIGINT
- `storage_path`: VARCHAR(1000)
- `column_metadata`: JSONB
- `uploaded_by`: UUID
- `uploaded_at`: TIMESTAMP

---

## API Design

### REST API Standards
- Base URL: `/api/v1`
- Content-Type: `application/json`
- Authentication: Bearer token (JWT)
- Pagination: Page-based with `page` and `size` parameters

### WebSocket API
- Endpoint: `/ws`
- Protocol: STOMP over SockJS
- Authentication: Token passed in connection headers

### Example Endpoints

#### Pipelines
```
GET    /api/v1/pipelines          # List pipelines
POST   /api/v1/pipelines          # Create pipeline
GET    /api/v1/pipelines/{id}     # Get pipeline
PUT    /api/v1/pipelines/{id}     # Update pipeline
DELETE /api/v1/pipelines/{id}     # Delete pipeline
POST   /api/v1/pipelines/{id}/run # Run pipeline
```

#### Jobs
```
GET    /api/v1/jobs               # List jobs
GET    /api/v1/jobs/{id}          # Get job
DELETE /api/v1/jobs/{id}          # Cancel job
POST   /api/v1/jobs/{id}/retry    # Retry job
GET    /api/v1/jobs/{id}/logs     # Get job logs
GET    /api/v1/jobs/{id}/metrics  # Get job metrics
```

---

## Security Architecture

### Authentication Flow
```
User -> Keycloak (Login)
           |
           v
      JWT Token
           |
           v
User -> Gateway (Validate JWT)
           |
           v
      Route to Service
```

### Authorization
- Role-based access control (RBAC)
- Roles: `admin`, `editor`, `viewer`
- Resource-level permissions
- Personal Access Tokens (PAT) for API access

### Data Protection
- TLS 1.3 for all communications
- Database encryption at rest
- Password hashing (bcrypt)
- Secrets management (Docker secrets)

---

## Scalability

### Horizontal Scaling
- All services are stateless
- Services can be replicated
- Load balancing via Gateway
- Session stored in Redis

### Vertical Scaling
- JVM heap size configurable
- Database connection pooling (HikariCP)
- Resource limits in Docker

### Performance Optimizations
- Database indexes on frequently queried columns
- Redis caching for job status
- Batch processing for large datasets
- Streaming for file uploads

---

## Deployment Architecture

### Docker Compose (Development)
- All services in single compose file
- Local development setup
- Hot reloading for UI

### Docker Compose (Production)
- Multi-replica services
- Resource limits
- Health checks
- Read-only filesystems
- Non-root users

### Kubernetes (Optional)
- Helm charts provided
- Horizontal Pod Autoscaling
- Rolling updates
- Secrets management

---

## Monitoring & Observability

### Metrics
- JVM metrics (Micrometer)
- Database connection pool metrics
- HTTP request metrics
- Custom business metrics

### Health Checks
- `/actuator/health`: Overall health
- `/actuator/health/liveness`: Liveness probe
- `/actuator/health/readiness`: Readiness probe

### Logging
- Structured logging (JSON)
- Correlation IDs
- Log aggregation (ELK)

---

## Future Enhancements

### Planned Features
1. **GraphQL API**: Alternative to REST
2. **Federation**: Join data across pipelines
3. **ML Integration**: Auto-suggest mappings
4. **Collaboration**: Real-time collaboration in designer
5. **Plugins**: Third-party operation support

### Architecture Improvements
1. **Event Sourcing**: For pipeline execution
2. **CQRS**: Separate read/write models
3. **Service Mesh**: Istio for advanced traffic management
4. **Multi-cloud**: Support for AWS/GCP/Azure

---

## Conclusion

RDF Forge's microservices architecture provides a robust, scalable foundation for RDF data transformation and publishing. The clear separation of concerns enables independent development and deployment of services, while the shared data layer ensures consistency across the platform.

For questions or contributions, please refer to the [Contributing Guide](CONTRIBUTING.md).
