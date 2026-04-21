# RDF Forge - System Architecture

> **Source of truth (2026-04).** This document describes what is actually
> built and running today. Older design docs (`ARCHITECTURE_ANALYSIS.md`,
> `MIGRATION_ANALYSIS.md`, `UNIFIED_RDF_PLATFORM_TASK.md`) retain historical
> context; when they disagree with this file, this file wins.

RDF Forge (product name **Cube Creator X**) is a microservice platform for
ingesting tabular / document data, transforming it to RDF, validating it
against SHACL, and publishing it to one or more triplestores.

---

## Module inventory

The Maven parent lives at `rdf-forge/pom.xml`. It lists 11 Java modules plus
a sibling Angular UI:

| Module | Port | Purpose |
|--------|------|---------|
| `rdf-forge-gateway` | 8000 | Spring Cloud Gateway; single entry point for `/api/v1/**`; auth, CORS, rate limiting, Resilience4j circuit breakers |
| `rdf-forge-pipeline-service` | 8001 | Pipeline CRUD, versioning, validation, operation catalog, destinations |
| `rdf-forge-shacl-service` | 8002 | SHACL shape CRUD, shape templates, on-demand and batch validation |
| `rdf-forge-job-service` | 8003 | Job execution, logs, metrics, WebSocket updates, cron schedules, Redis-backed queue |
| `rdf-forge-data-service` | 8004 | File upload / preview / download, storage provider + format registries, MinIO integration |
| `rdf-forge-dimension-service` | 8005 | Shared cube dimensions, dimension values, hierarchies |
| `rdf-forge-triplestore-service` | 8006 | Triplestore connection registry, SPARQL, graph upload/export, multi-provider |
| `rdf-forge-auth-service` | 8086 | Personal Access Tokens (PAT), Keycloak read-only client, admin endpoints |
| `rdf-forge-engine` | n/a (lib) | Core ETL engine: Apache Jena + Apache Camel, operation registry, cube/shacl operations |
| `rdf-forge-common` | n/a (lib) | Shared DTOs, exceptions (`ResourceNotFoundException`, `ShaclValidationException`, `TriplestoreConnectionException`, `RdfForgeException`), global exception handler (RFC 7807 ProblemDetail), audit log schema |
| `rdf-forge-cli` | n/a | Spring Shell CLI that talks to the gateway |
| `rdf-forge-ui` | 4200 (dev) / 3000 or 80 (container) | Angular 21 SPA |

All services speak REST over `/api/v1/**` via the gateway. The gateway routes
`/api/v1/pipelines|operations|templates → 8001`,
`/api/v1/shapes|validation → 8002`, `/api/v1/jobs|schedules → 8003`,
`/api/v1/data → 8004`, `/api/v1/dimensions|hierarchies → 8005`,
`/api/v1/triplestores|sparql|graphs → 8006`, and auth routes to 8086.

---

## Frontend stack (`rdf-forge/rdf-forge-ui/`)

- **Angular 21** with standalone components (`bootstrapApplication(App, appConfig)`).
- **Routing**: Lazy-loaded standalone components in `src/app/app.routes.ts`
  for dashboard, pipelines, jobs, shacl, cubes, data, dimensions,
  triplestore, settings.
- **Reactivity**: Angular **Signals** for component-local state; **RxJS ~7.8**
  for HTTP streams, WebSocket events, interceptor composition.
- **UI**: **Angular Material 21** + **`@oblique/oblique` ^15.1** (Swiss Admin
  design system) as the primary component / theming layer; `ngx-charts` and
  `ngx-graph` (+ `dagre`) for visualization.
- **HTTP**: `HttpClient` + custom interceptors (`auth.interceptor`) for bearer
  token injection and offline-mode no-op. Base URL driven by
  `environment.apiBaseUrl`.
- **Auth**: **Keycloak JS 24** adapter (+ `angular-oauth2-oidc` ^20) for
  online/OIDC flows. `AuthService` short-circuits when
  `environment.auth.enabled === false` (standalone / offline mode).
- **Real-time**: `@stomp/stompjs` + `sockjs-client` for job log streaming.
- **Build/dev tooling**: Angular CLI 21 (esbuild), ESLint 9, Prettier,
  TypeScript ~5.9.
- **Tests**: **Karma + Jasmine** for units (`ng test` / `npm test`);
  **Playwright** ^1.49 for e2e (`npm run e2e`).

Development loop:

```
cd rdf-forge/rdf-forge-ui
npm install --legacy-peer-deps
npm start                                  # ng serve (default dev)
ng serve --configuration offline           # dev server against offline backend
ng serve --configuration online            # dev server against Keycloak/online
npm test                                   # Karma + Jasmine watch
npm run test:ci                            # headless, coverage
npm run e2e                                # Playwright
ng build --configuration production        # production bundle in dist/rdf-forge-ui/browser
```

Container build: `Dockerfile` uses Node 20 → Nginx; Nginx proxies `/api` to
the gateway and falls back to `index.html` for SPA routing.

---

## Backend stack

- **Java 21 (LTS)**, compiled with the Maven compiler plugin; Maven 3.9.
- **Spring Boot 3.4.3**, **Spring Cloud 2024.0.1**.
- **Apache Jena 5.0** for RDF parsing, SHACL, and SPARQL.
- **Apache Camel 4.5** inside `rdf-forge-engine` for pipeline orchestration.
- **Spring Cloud Gateway** for routing; **Resilience4j** for circuit breakers
  and retries; **Bucket4j** for rate limiting.
- **SpringDoc OpenAPI 2.8** for API docs.
- **Lombok** + **MapStruct** for boilerplate reduction.
- Graceful shutdown enabled on every service (30s timeout). Job locking
  uses `ConcurrentHashMap<UUID, ReentrantLock>` in `JobService`.

---

## Data plane

| Store | Purpose |
|-------|---------|
| **PostgreSQL 16** | Primary relational DB for every service (service-owned schemas; Flyway-managed) |
| **MinIO** | S3-compatible object storage for raw data, shapes, pipeline artifacts (buckets: `rdf-forge-data`, `rdf-forge-shapes`, `rdf-forge-pipelines`) |
| **Redis 7** | Job queue, distributed cache, STOMP broker relay for WebSocket fan-out |
| **Apache Fuseki / Ontotext GraphDB** | Default triplestores; GraphDB is the standalone-mode default, Fuseki is also supported |
| **Keycloak 24** | IdP for online mode; not deployed in standalone |

Credentials are externalized via env vars: `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, etc.

---

## Extension seams

When adding functionality, prefer registering in these existing registries
rather than hardwiring logic into controllers or services.

| Registry | File | Extends |
|----------|------|---------|
| Engine operations | `rdf-forge-engine/src/main/java/io/rdfforge/engine/operation/OperationRegistry.java` | Add new pipeline `Operation` implementations (load / transform / validate / output) |
| Data formats | `rdf-forge-data-service/src/main/java/io/rdfforge/data/format/DataFormatRegistry.java` | Add parsers for new file formats (CSV, TSV, JSON, XLSX, Parquet, RDF) |
| Storage providers | `rdf-forge-data-service/src/main/java/io/rdfforge/data/storage/StorageProviderRegistry.java` | Add blob-store backends (MinIO is default; S3 / Azure / GCS are scaffolded) |
| Publish destinations | `rdf-forge-pipeline-service/src/main/java/io/rdfforge/pipeline/destination/DestinationRegistry.java` | Add new sinks for pipeline output |
| Triplestore providers | `rdf-forge-triplestore-service/src/main/java/io/rdfforge/triplestore/connector/TriplestoreProviderRegistry.java` | Add connectors for Fuseki / GraphDB / Stardog / Virtuoso / Blazegraph |

Engine operations are auto-discovered as Spring `@Component`s and registered
at startup; see `OperationRegistryTest.java` for the contract.

---

## Auth

Two modes, selected via Spring profiles:

- **Online (Keycloak)** — default in production. Gateway is a Spring Security
  OAuth2 resource server validating JWTs issued by Keycloak realm `rdfforge`.
  Clients: `rdf-forge-ui` (public) and `rdf-forge-gateway` (confidential).
  Roles: `admin`, `user`. `rdf-forge-auth-service` issues and manages PATs
  and exposes admin endpoints.
- **NoAuth (`noauth` profile)** — dev/demo only. `NoAuthSecurityConfig` and
  `NoAuthUserFilter` are `@Profile("noauth")`. Both have a
  `@PostConstruct` guard that throws `IllegalStateException` unless the
  active profile is in
  `ALLOWED_PROFILES = {"noauth", "test", "local"}` — this prevents the
  noauth wiring from ever activating in prod, even if misconfigured. Both
  components log loud security warnings.

Header hygiene at the gateway: `NoAuthUserFilter` always **strips**
`X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Auth-Type`, and
`X-Token-Name` from every incoming request before injecting its own
`X-User-Id` default. This prevents header-spoofing attacks where a caller
attaches fake identity headers and expects backends to trust them. In
online mode the JWT-validating filter performs the equivalent
strip-then-inject after successful verification.

CORS is configured per controller via
`@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")`.
Wildcards were removed during the 2026-03-08 production-readiness review.

---

## Flyway migration conventions

Each service owns its schema and its own migration folder under
`src/main/resources/db/migration/`:

- `rdf-forge-common` — `V100__init_audit_log.sql` (reserved high
  version to keep service-local migrations separated from shared ones).
- `rdf-forge-auth-service` — `V1__create_personal_access_tokens.sql`.
- `rdf-forge-data-service` — `V1__init_data_schema.sql`.
- `rdf-forge-dimension-service` — `V1__init_dimension_schema.sql` through
  `V6__add_cube_project_fields.sql`.
- `rdf-forge-job-service` — `V1__init_job_schema.sql`,
  `V2__add_job_progress.sql`.
- `rdf-forge-pipeline-service` — `V1__init_pipeline_schema.sql` through
  `V5__add_updated_by_column.sql`.
- `rdf-forge-shacl-service` — `V1__init_shacl_schema.sql`.
- `rdf-forge-triplestore-service` — `V1__init_triplestore_schema.sql`
  through `V3__update_fuseki_auth_to_basic.sql`.

Rules:

1. Never rewrite a migration that has shipped; add a new `V{n+1}__*.sql`.
2. Service-local versions start at `V1`; platform-wide shared tables
   (e.g. the audit log in `rdf-forge-common`) use `V100+` so they cannot
   collide with any single service's timeline.
3. All DDL changes must land via Flyway; do not hand-edit schemas.
4. Keep migrations idempotent-safe where possible (`CREATE TABLE IF NOT
   EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`) so reruns are safe
   in dev.

---

## Active-development phases

The following UI/product tracks are in active development. Implementations,
routes, and APIs are moving targets; update this section as each phase lands.

### Phase 1 — Project Workspace
Multi-project scoping: every pipeline, shape, dimension, data source, and
job should live under a project with its own membership and access controls.
Backend project entity exists partially in `rdf-forge-common`; UI workspace
switcher and project-scoped routing are in progress.

### Phase 2 — Ontology Studio
Interactive browser for the target ontology / vocabularies: class
hierarchies, property inspection, namespace management, and import from
external vocabularies. Will plug into the shape editor (Phase 5) and the
mapping studio (Phase 3).

### Phase 3 — Mapping Studio
Visual mapping between tabular source columns and target RDF classes /
properties. Replaces ad-hoc JSON mapping configs. Will emit engine
operations registered via `OperationRegistry`.

### Phase 4 — Preview
Run a short segment of a pipeline against a sample of the source data and
show the resulting RDF triples + validation deltas live, without committing
to the triplestore. Depends on mapping studio and shape editor.

### Phase 5 — Validation / SHACL Studio
Authoring, versioning, and running SHACL shapes. The existing
`rdf-forge-shacl-service` already provides CRUD + validation; the studio
adds a form-based editor, template shapes, and integration with Preview so
violations show up before publish.

---

## Related docs

- `rdf-forge/DEPLOYMENT.md` — compose files, Kubernetes, port matrix,
  environment variables, Windows port notes.
- `rdf-forge/CONTRIBUTING.md` — plugin authoring (how to add an operation).
- `rdf-forge/docs/plugin-development.md` — deeper plugin internals.
- `rdf-forge/docs/operations-catalog.md` — catalog of shipped engine
  operations.
- `rdf-forge/SECURITY_AUDIT.md`, `rdf-forge/PRODUCTION_READINESS.md` —
  hardening status and remaining work.
- `rdf-forge/USER_GUIDE.md` — end-user walkthroughs.
- `WARP.md`, `QUICKSTART.md` (repo root) — environment-agnostic getting
  started.

## Historical context

- `MIGRATION_ANALYSIS.md` — analysis of the Vue UI that preceded the
  current Angular 21 SPA. The Vue app has been removed; the Angular
  migration is complete.
- `UNIFIED_RDF_PLATFORM_TASK.md` — original product brief, written when a
  TypeScript/Express stack was still on the table. Superseded by this
  document.
- `TECHNICAL_ANALYSIS.md` — comparison of the legacy Zazuko TypeScript
  stack vs the current Java stack.
