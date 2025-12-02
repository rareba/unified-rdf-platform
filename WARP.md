# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Repository overview

`cube-validator-x` hosts three main implementation tracks for an RDF data platform:

- `rdf-forge/` — Java 21 + Spring Boot 3 multi-module backend with the primary UI and deployment artifacts. This is the main path going forward.
- `lindas-cube-creator/` — fork of Zazuko Cube Creator in TypeScript.
- `lindas-barnard59/` — fork of Barnard59 pipeline engine in TypeScript/JavaScript.
- `workfolder-cube-validator-x/` — scratch/work space.
- `-p/` — legacy/experimental; do not assume production relevance.

Key design and analysis docs at the repo root and under `rdf-forge/`:

- `UNIFIED_RDF_PLATFORM_TASK.md` — high-level requirements and goals.
- `TECHNICAL_ANALYSIS.md` — comparison of the legacy TypeScript stack vs the new Java stack.
- `TEST_DOCUMENTATION.md` — detailed overview of existing tests and how to run them.
- `rdf-forge/ARCHITECTURE_ANALYSIS.md` — in-depth microservice architecture and security notes.
- `rdf-forge/DEPLOYMENT.md` — deployment modes, ports, and Docker Compose usage.
- `MIGRATION_ANALYSIS.md` — analysis of the earlier Vue-based UI and its feature set.

Prefer `rdf-forge` for new development unless you are explicitly working on the older Zazuko-derived projects.

## Primary backend: `rdf-forge` (Java)

`rdf-forge/` is a Maven multi-module project (`rdf-forge/pom.xml`) named **Cube Creator X**. Modules:

- `rdf-forge-common` — shared models, DTOs, exceptions, and utilities.
- `rdf-forge-engine` — core ETL engine (Apache Jena + Apache Camel) used by services.
- Service modules — Spring Boot 3 REST services, each focused on one bounded context:
  - `rdf-forge-pipeline-service` — pipeline definitions, versioning, and validation.
  - `rdf-forge-job-service` — job execution, logging, scheduling, and statistics.
  - `rdf-forge-shacl-service` — SHACL shape management and validation.
  - `rdf-forge-data-service` — data sources, file upload/preview, MinIO integration.
  - `rdf-forge-dimension-service` — shared cube dimensions and hierarchies.
  - `rdf-forge-triplestore-service` — triplestore connections, SPARQL, graph operations.
- `rdf-forge-gateway` — Spring Cloud Gateway entrypoint routing `/api/v1/**` to backend services. Current security configuration effectively permits all exchanges (see `ARCHITECTURE_ANALYSIS.md` and the gateway `SecurityConfig`), which is convenient for local/offline development but must be treated carefully for production.
- `rdf-forge-cli` — Spring Shell–based CLI interacting with the gateway APIs.

Infrastructure expectations (see `ARCHITECTURE_ANALYSIS.md` and `DEPLOYMENT.md`): PostgreSQL, Redis, MinIO, and a triplestore (GraphDB in standalone mode; Fuseki appears in older docs). Docker Compose files under `rdf-forge/` orchestrate these components.

### Backend build & test commands

Run these from `rdf-forge/`:

- Run all backend tests: `mvn test`.
- Run tests with coverage/verification: `mvn verify` (as described in `TEST_DOCUMENTATION.md`).
- Typical full multi-module build: `mvn clean install`.
- Run a single backend test class (example): `mvn test -Dtest=PipelineServiceTest`.

When changing a specific service, mirror the existing tests in that module’s `src/test/java` tree. `TEST_DOCUMENTATION.md` lists the key test classes (e.g., `JobServiceTest`, `DataServiceTest`, `GlobalExceptionHandlerTest`) and covered scenarios.

### End-to-end stack via Docker

From `rdf-forge/` (see `DEPLOYMENT.md` for full details):

- **Standalone/offline mode (recommended local stack, no auth):**
  - Start: `docker compose -f docker-compose.standalone.yml up -d`
  - Stop: `docker compose -f docker-compose.standalone.yml down` (add `-v` to reset data)
  - Default endpoints: UI at `http://localhost:4200`, API gateway at `http://localhost:9080`, GraphDB at `http://localhost:7200`, plus PostgreSQL, Redis, and MinIO.
- **Development compose (`docker-compose.development.yml`):** supports profiles for UI-only, UI + gateway, infrastructure, or full stack. Use the `.env.example` template and the profile matrix in `DEPLOYMENT.md` when wiring to remote Kubernetes clusters or enabling authentication.

## Current primary UI: `rdf-forge-ui` (Angular 21)

`rdf-forge/rdf-forge-ui/` is the actively maintained SPA. The Vue-based UI that previously existed has been fully migrated and removed.

Technology stack (see `rdf-forge/rdf-forge-ui/README.md` and `package.json`): Angular 21, TypeScript 5.9, RxJS 7.8, PrimeNG 20, PrimeFlex, PrimeIcons, Keycloak integration, Karma/Jasmine for tests, ESLint and Prettier for linting/formatting.

High-level structure (summarized from the UI README):

- `src/app/core` — core module with guards, HTTP interceptors, and application-wide services (auth, API, configuration).
- `src/app/shared` — shared components, pipes, and directives reused across features.
- `src/app/features` — feature modules corresponding to major screens:
  - `cube` (cube wizard), `dashboard`, `data`, `dimension`, `job`, `pipeline`, `settings`, `shacl`, `triplestore`.
- Root setup: `app.config.ts`, `app.routes.ts`, `app.ts`/`app.html`/`app.scss`.
- `src/environments` — environment switching:
  - `environment.ts` — default development.
  - `environment.offline.ts` — offline mode (no auth, local Docker backend).
  - `environment.online.ts` — online mode (Keycloak-authenticated, remote backend).
- Tooling/config: `angular.json`, `tsconfig*.json`, `karma.conf.js`, `eslint.config.js`, `Dockerfile`, `nginx.conf`.

### Angular UI development commands

Run from `rdf-forge/rdf-forge-ui/`:

**Install dependencies**

- Local dev: `npm install --legacy-peer-deps`.
- CI uses: `npm ci --legacy-peer-deps` (see `.github/workflows/rdf-forge-ui.yml`).

**Dev server**

- Default: `ng serve` (or `npm start`).
- Offline backend configuration: `ng serve --configuration offline`.
- Online/Keycloak configuration: `ng serve --configuration online`.
- Custom port / auto-open: add `--port 4201` and/or `--open` as needed.

**Builds**

- Development build: `ng build` or `ng build --configuration development`.
- Production build: `ng build --configuration production`.
- Offline/online bundles: `ng build --configuration offline` or `ng build --configuration online`.

**Linting**

- `ng lint` or `npm run lint` — same script used by the GitHub Actions workflow.

**Unit tests (Karma + Jasmine)**

- Run tests once: `ng test --watch=false`.
- Watch mode: `ng test` or `npm test`.
- With coverage: `ng test --code-coverage` (CI runs `npm run test -- --no-watch --code-coverage --browsers=ChromeHeadless`).
- Run a single spec file (example from the README): `ng test --include=**/dashboard.spec.ts`.

Karma/TypeScript test configuration lives in `karma.conf.js` and `tsconfig.spec.json`. Coverage output goes to `rdf-forge/rdf-forge-ui/coverage/` and is uploaded by CI.

### Angular UI Docker image

The `Dockerfile` in `rdf-forge/rdf-forge-ui/` uses a Node 20 build stage and an Nginx runtime stage:

- Build stage: installs dependencies and runs `npm run build -- --configuration ${BUILD_CONFIG}`.
- Runtime stage: serves `dist/rdf-forge-ui/browser` via Nginx using `nginx.conf`, which proxies `/api` to the gateway and handles SPA routing (`try_files` fallback to `index.html`).

The `rdf-forge-ui.yml` GitHub Actions workflow builds, tests, and publishes Docker images to GitHub Container Registry using this `Dockerfile`.

## Legacy / auxiliary projects

### Zazuko-derived projects

The `lindas-cube-creator/` and `lindas-barnard59/` directories contain forks of the original Zazuko systems. `TECHNICAL_ANALYSIS.md` documents their architecture and relationship to `rdf-forge`.

High-level guidance:

- `lindas-cube-creator/` — Vue 3 + Vuex UI, Express/Hydra APIs, and CLI for cube creation.
  - Tests (from `TEST_DOCUMENTATION.md`):
    - `cd lindas-cube-creator`.
    - `yarn install`.
    - `yarn test`.
- `lindas-barnard59/` — Node.js monorepo of RDF pipeline packages (formats, SHACL, SPARQL, S3, etc.).
  - Tests (from `TEST_DOCUMENTATION.md`):
    - `cd lindas-barnard59`.
    - `npm install`.
    - `npm test`.

For new features, prefer implementing them in `rdf-forge` and its Angular UI, using these legacy projects mainly as behavioural and conceptual references.

## Key architectural & testing references

When making non-trivial changes, consult these documents alongside the code:

- `rdf-forge/ARCHITECTURE_ANALYSIS.md` — full service inventory, responsibility breakdown, and offline/online mode design (including Mermaid diagrams and security notes).
- `rdf-forge/DEPLOYMENT.md` — authoritative guide for standalone, development, and Kubernetes deployments, port mappings, environment variables, and Windows-specific port considerations.
- `TECHNICAL_ANALYSIS.md` — overall project context, dual-stack (TypeScript + Java) architecture, and long-term recommendations.
- `TEST_DOCUMENTATION.md` — describes the structure and coverage of existing tests across Java and JS/TS projects and shows how to run all relevant suites.

Keep these documents in sync when you introduce significant architectural, deployment, or testing changes so future agents can operate accurately in this repository.