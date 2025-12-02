# Problem statement
The current UI story for RDF Forge is split between an older, fully featured Vue 3 application under `rdf-forge-ui-vue-backup/` and a newer Angular 21 application under `rdf-forge-ui/` that currently contains mostly placeholder components (e.g. "dashboard works!"). Docker and CI are already oriented toward the Angular app, but real end-user functionality still effectively lives in the Vue code.
The goal is to:
* Implement a **full-featured Angular UI** in `rdf-forge-ui/` with parity to the existing Vue UI.
* Ensure **all runtime and deployment paths use Angular only**.
* **Remove Vue from the active stack**, keeping at most historical artifacts in Git history, and ideally deleting the `rdf-forge-ui-vue-backup/` directory once Angular parity is reached.
# Current state overview
## Backend
* Java 21 + Spring Boot 3.2.5 multi-module backend (`rdf-forge/`) with microservices for pipelines, jobs, data, dimensions, SHACL, triplestores, and a gateway.
* REST APIs are already well-factored, e.g.:
    * `PipelineController` under `/api/v1/pipelines` (+ `/validate`, `/templates`, `/operations`, versions, etc.).
    * `JobController` under `/api/v1/jobs` (+ `/logs`, `/metrics`, `/stats`, `/retry`, etc.).
    * `TriplestoreController` under `/api/v1/triplestores` (+ graphs, SPARQL, upload, export, etc.).
* The gateway routes `/api/v1/**` to these services and supports `noauth` and `graphdb` profiles for offline/standalone use.
* Standalone Docker Compose brings up the full backend stack plus UI and GraphDB.
## Angular UI (`rdf-forge-ui/`)
* Angular 21, standalone bootstrap via `bootstrapApplication(App, appConfig)` in `src/main.ts`.
* Routing configured in `app.routes.ts` with lazy-loaded standalone components for:
    * `dashboard`, `pipelines`, `jobs`, `shacl`, `cubes`, `data`, `dimensions`, `triplestore`, `settings`.
* `AuthService` + `authGuard` already support offline mode (no auth) and online mode (Keycloak) using `environment.*.ts`.
* `auth.interceptor` is wired into `HttpClient` via `app.config.ts`.
* Feature components exist as TS shells (`Dashboard`, `PipelineList`, `PipelineDesigner`, `DataManager`, etc.) but the HTML templates are minimal placeholders and there are no Angular services for calling the backend APIs yet.
* Global styling was previously minimal but now includes a basic shell (sidebar, topbar, cards) in `styles.scss`.
## Vue UI (`rdf-forge-ui-vue-backup/`)
* Full-featured Vue 3 + Vite app with:
    * Rich layout (`App.vue`) including sidebar navigation, topbar, breadcrumbs, and main content area.
    * Views for dashboard, pipelines, pipeline designer (with Vue Flow), SHACL Studio, cube wizard, jobs (list + monitor), data manager, triplestore browser, settings, etc.
    * Axios-based API client and `src/api/*.ts` modules for all backend services.
    * Keycloak-based auth store (`src/stores/auth.ts`) and router configuration.
    * Vitest test suite for the client, auth store, and some components.
* Dockerfile builds the Vue app with `npm run build` and serves it via Nginx.
* Previously used by Docker in some configurations; now we have reverted Docker to Angular for standalone.
# Requirements and constraints
* **All production and local environments must use Angular** (`rdf-forge-ui/`) as the only UI.
* **No Vue dependencies at runtime**: no Vue-based Docker images, no Vue paths in CI, no Nginx configs referencing Vue build paths.
* Functional parity should be as high as practical, guided by the existing Vue code and backend APIs.
* Standalone/offline mode must work end-to-end with Angular, without Keycloak, using the same `/api/v1` endpoints.
* Online/Keycloak mode must remain possible via environment-based configuration.
# Proposed approach
## Phase 0: Lock deployment onto Angular & quarantine Vue
1. **Docker & compose**
    * Ensure `docker-compose.standalone.yml` uses `./rdf-forge-ui` with `BUILD_CONFIG=standalone` (already done).
    * Ensure `docker-compose.development.yml` and any other compose files reference only the Angular UI.
    * Confirm Nginx config used in all UI images expects an Angular build output (`dist/rdf-forge-ui/browser`).
2. **CI & workflows**
    * Confirm `.github/workflows/rdf-forge-ui.yml` only checks out and builds `rdf-forge/rdf-forge-ui`.
    * Remove any Vue-specific CI if present (e.g. no jobs referencing `rdf-forge-ui-vue-backup`).
3. **Docs & metadata**
    * Update `rdf-forge/README` or higher-level docs to state Angular is the canonical UI.
    * Optionally mark `rdf-forge-ui-vue-backup/` as deprecated in a small `README` inside that folder until deletion.
4. **Repository hygiene**
    * Stop using `rdf-forge-ui-vue-backup` anywhere in scripts, test harnesses, or dev workflows.
(After later phases, we can physically delete `rdf-forge-ui-vue-backup/` from the repo once we are confident Angular has full parity.)
## Phase 1: Core Angular infrastructure
1. **Global layout and design system**
    * Finalize the shell in `App` + `app.html` + `styles.scss` to provide:
        * Sidebar navigation with all major sections (dashboard, pipelines, SHACL, cube, jobs, data, dimensions, triplestore, settings).
        * Topbar with a title and room for notifications/profile.
        * A central page content container with sensible spacing and responsive behavior.
    * Use PrimeNG components for basic UI controls (buttons, tables, dialogs) in a way that parallels PrimeVue usage.
2. **Routing and route data**
    * Extend `app.routes.ts` to attach route data (e.g. `data: { title: 'Pipelines' }`) so `currentPageTitle` in `App` can derive its value from the router rather than a static string.
    * Ensure lazy-loaded standalone components are correctly declared/imported for each feature module.
3. **HTTP infrastructure**
    * Implement a generic `ApiClientService` using `HttpClient` that encapsulates:
        * Base URL from `environment.apiBaseUrl`.
        * Standard headers and error handling.
    * Make sure `auth.interceptor` correctly adds the bearer token in online mode and is a no-op in offline mode.
4. **Auth behavior**
    * Align Angular `AuthService` behavior with the Vue `auth` store semantics:
        * In offline/standalone mode: mark user as authenticated and provide a dummy profile (no Keycloak calls).
        * In online mode: initialize Keycloak, perform login-required flow, manage token refresh.
    * Verify `authGuard` behaves correctly in both offline and online modes using `environment.*.ts`.
## Phase 2: Port API layer from Vue to Angular services
For each `src/api/*.ts` module in the Vue project, create a corresponding Angular service in `rdf-forge-ui` under `src/app/core/api` or `src/app/core/services`:
1. **Pipeline service**
    * Map `src/api/pipeline.ts` to `PipelineService` with methods:
        * `fetchPipelines`, `fetchPipeline`, `createPipeline`, `updatePipeline`, `deletePipeline`, `duplicatePipeline`, `validatePipeline`, `runPipeline`, `fetchOperations`, `fetchOperation`, `fetchPipelineVersions`, `fetchPipelineVersion`.
    * Use strong TypeScript types mirroring the interface definitions in the Vue file.
2. **Job service**
    * Map job-related Vue API functions to `JobService` with methods to list jobs, get job detail, logs, metrics, create/cancel/retry jobs, and fetch stats.
3. **Data service**
    * Map data manager endpoints (upload, preview, list, delete, download) to `DataService`, including file upload with `FormData`.
4. **SHACL service**
    * Map shape library and validation calls to `ShaclService` (CRUD, validation, templates, categories).
5. **Dimension service**
    * Map dimension management (list, CRUD, values, import/export, hierarchy tree, lookup) to `DimensionService`.
6. **Triplestore service**
    * Map triplestore connection and browser operations (list connections, health, graphs, resources, SPARQL, upload/export) to `TriplestoreService`.
7. **Settings/Config service**
    * If the Vue app has explicit settings endpoints, mirror them; otherwise, rely on environment-driven config for now.
8. **Unit tests**
    * For each service, create Jasmine/Karma specs that mimic the behaviors covered by the Vue Vitest tests (e.g. base URL handling, auth header behavior, error handling).
## Phase 3: Port key feature UIs (Angular components)
Port features incrementally, using the Vue views as a behavioral reference but writing idiomatic Angular + PrimeNG implementations.
1. **Dashboard**
    * Expand the current dashboard layout into cards and widgets:
        * Aggregated job stats (`/api/v1/jobs/stats`).
        * Recently run pipelines, recent jobs, etc.
    * Reuse card-grid styles and show real data retrieved via services.
2. **Pipelines**
    * `PipelineList` component:
        * Use PrimeNG `p-table` with pagination, sorting, filtering (search bar).
        * Actions: view, edit, duplicate, delete.
    * `PipelineDesigner` component:
        * First pass: form-based editor for pipeline details and steps without visual graph.
        * Later enhancement: recreate a subset of the Vue Flow behavior using a suitable Angular graph/flow library.
3. **Jobs**
    * `JobList` component:
        * PrimeNG table listing jobs with filters by status and pipeline.
        * Action buttons for view, cancel, retry.
    * `JobMonitor` component:
        * Show details and logs for a single job; use periodic polling or WebSocket integration later.
4. **Data Manager**
    * `DataManager` component:
        * Upload form (file input + options) wired to `DataService.upload`.
        * Table listing data sources with delete/preview actions.
        * Preview dialog showing sample rows from `previewDataSource`.
5. **SHACL Studio**
    * `ShaclStudio` component:
        * List of shapes with filters and tags.
        * Integration with a text editor (Monaco via Angular wrapper) for editing SHACL.
    * `ShapeEditor`:
        * Detailed editing view for a single shape, with validation status.
6. **Cube Wizard**
    * `CubeWizard` component:
        * Multi-step wizard using Angular/PrimeNG `Steps` to guide through data selection, dimension mapping, and publication.
7. **Dimensions**
    * `DimensionManager` component:
        * Hierarchical list/tree of dimensions.
        * Detail panel for values with CRUD and CSV import.
8. **Triplestore Browser**
    * `TriplestoreBrowser` component:
        * Connection selector, graph list, SPARQL query editor.
        * Results table and basic graph statistics.
9. **Settings**
    * `Settings` component:
        * Basic view showing environment information and configurable options (e.g. API base URL override when applicable).
For each feature, start with read-only lists using stubbed data, then wire to real services and add mutation operations once the API layer is stable.
## Phase 4: Offline/online modes in Angular UI
1. **Offline (standalone) mode**
    * Ensure the `standalone` build configuration uses `environment.standalone.ts` where `auth.enabled = false` and `apiBaseUrl = '/api/v1'`.
    * Verify `authGuard` short-circuits to `true` when `auth.enabled` is false, and `AuthService.init()` sets a local offline user.
    * Confirm the Angular UI works end-to-end against the standalone compose stack without any Keycloak container.
2. **Online mode**
    * Use `environment.online.ts` with `auth.enabled = true` and proper Keycloak settings.
    * Validate the login flow, token injection via `auth.interceptor`, and route protection for all major sections.
3. **Development mode**
    * Ensure `ng serve --configuration offline` and `ng serve --configuration online` are both functional for day-to-day development.
## Phase 5: Testing & hardening
1. **Component tests**
    * Add unit tests for key components (Dashboard, PipelineList, JobList, DataManager, TriplestoreBrowser) using Jasmine + the Angular testing harness.
2. **Integration tests**
    * Add integration tests that boot the Angular app modules and interact with HTTP services using `HttpTestingController` to validate API integration behavior.
3. **E2E tests (optional)**
    * Once the Angular UI stabilizes, add Playwright or Cypress-based E2E tests exercising the main flows (pipeline creation, job run, data upload, SHACL validation, triplestore query).
4. **Performance checks**
    * Validate that production builds (`ng build --configuration production`) stay within budget constraints.
## Phase 6: Remove Vue
Only after Angular parity is acceptable and regressions are addressed:
1. **Stop using Vue in any tooling**
    * Double-check there are no remaining references to `rdf-forge-ui-vue-backup` in compose files, scripts, docs, or CI.
2. **Delete Vue project directory**
    * Remove `rdf-forge-ui-vue-backup/` from the repo (leaving it available in Git history if needed).
3. **Update documentation**
    * Update `MIGRATION_ANALYSIS.md` and related docs to mark the migration as complete and describe the Angular architecture as authoritative.
4. **Cleanup dependencies**
    * Ensure no stray Vue-related dependencies remain in root `package.json` or elsewhere.
# Execution notes
* This migration is substantial; implementation should proceed feature-by-feature, starting with core flows (pipelines, jobs, data) before more advanced areas (cube wizard, full SHACL editor, triplestore UX polish).
* Throughout, use the Vue implementation and the Java backend tests/documentation as the functional reference but write idiomatic Angular code with PrimeNG.
* The first tangible milestone is: "Angular UI in standalone mode provides a full shell and working read-only views (lists) for pipelines, jobs, and data against the real backend APIs."
