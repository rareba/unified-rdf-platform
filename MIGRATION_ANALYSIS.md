# RDF Forge UI - Vue.js Frontend Migration Analysis

## Executive Summary

This document provides a comprehensive analysis of the Vue.js frontend application located in `rdf-forge/rdf-forge-ui/`. The application is a sophisticated RDF data management platform built with Vue 3, featuring pipeline design, SHACL validation, cube creation, job monitoring, and triplestore browsing capabilities.

---

## 1. Project Structure Analysis

### 1.1 Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Vue.js | 3.4.21 | Core framework (Composition API) |
| Pinia | 2.1.7 | State management |
| Vue Router | 4.3.0 | Client-side routing |
| PrimeVue | 3.52.0 | UI component library |
| PrimeFlex | 3.3.1 | CSS utility framework |
| Axios | 1.6.8 | HTTP client |
| Keycloak-js | 24.0.0 | Authentication |
| Vue Flow | 1.33.0 | Pipeline visual editor |
| Monaco Editor | 0.47.0 | Code editing |
| Vite | 5.2.8 | Build tool |
| Vitest | 1.5.0 | Testing framework |
| TypeScript | 5.4.0 | Type system |

### 1.2 Directory Structure

```
rdf-forge/rdf-forge-ui/
├── public/                    # Static assets
├── src/
│   ├── api/                   # API client services (7 files)
│   ├── components/common/     # Reusable components (2 files)
│   ├── router/                # Route definitions (1 file)
│   ├── stores/                # Pinia stores (8 files)
│   ├── test/                  # Test configuration
│   ├── types/                 # TypeScript definitions
│   ├── views/                 # Page components (14 files)
│   ├── App.vue                # Root component
│   └── main.ts                # Entry point
├── package.json
├── tsconfig.json
├── vite.config.ts
└── vitest.config.ts
```

---

## 2. Routing Structure

| Route Path | Name | Component | Description |
|------------|------|-----------|-------------|
| `/` | dashboard | Dashboard.vue | Main dashboard |
| `/pipelines` | pipelines | PipelineList.vue | Pipeline list |
| `/pipelines/new` | pipeline-new | PipelineDesigner.vue | Create pipeline |
| `/pipelines/:id` | pipeline-edit | PipelineDesigner.vue | Edit pipeline |
| `/shacl` | shacl | ShaclStudio.vue | Shape library |
| `/shacl/new` | shape-new | ShapeEditor.vue | Create shape |
| `/shacl/:id` | shape-edit | ShapeEditor.vue | Edit shape |
| `/cube` | cube | CubeWizard.vue | Cube creation |
| `/jobs` | jobs | JobList.vue | Job list |
| `/jobs/:id` | job-detail | JobMonitor.vue | Job monitoring |
| `/jobs/:id/logs` | job-logs | JobMonitor.vue | Job logs |
| `/data` | data | DataManager.vue | Data sources |
| `/dimensions` | dimensions | DimensionManager.vue | Dimensions |
| `/triplestore` | triplestore | TriplestoreBrowser.vue | Query browser |
| `/settings` | settings | Settings.vue | Settings |

---

## 3. Component Inventory

### 3.1 Common Components

| Component | Path | Props | Events |
|-----------|------|-------|--------|
| LoadingOverlay | src/components/common/LoadingOverlay.vue | `loading: boolean`, `message?: string` | None |
| ErrorBoundary | src/components/common/ErrorBoundary.vue | None | onErrorCaptured |

### 3.2 View Components

| Component | PrimeVue Dependencies | External Libraries |
|-----------|----------------------|---------------------|
| Dashboard.vue | Card, DataTable, Column, Tag, Button, Skeleton | None |
| PipelineList.vue | DataTable, Column, Button, InputText, Dropdown, Tag, Dialog, Skeleton | None |
| PipelineDesigner.vue | InputText, Dropdown, Button, Divider | Vue Flow |
| ShaclStudio.vue | Card, TabView, TabPanel, InputText, InputNumber, Textarea, Dropdown, Button, Tag | None |
| ShapeEditor.vue | Button, InputText, InputNumber, Textarea, Dropdown, Chips, Tag, Panel, TabView, TabPanel, Dialog | None |
| CubeWizard.vue | Steps, FileUpload, DataTable, Column, Dropdown, InputText, Textarea, Calendar, Chips, Checkbox, Button, TabView, TabPanel | None |
| JobList.vue | DataTable, Column, Button, InputText, Dropdown, Calendar, Tag, ProgressBar | None |
| JobMonitor.vue | Button, Tag, ProgressBar, TabView, TabPanel, Panel, Dropdown, InputText, ToggleButton, DataTable, Column, Skeleton | WebSocket |
| DataManager.vue | Button, InputText, Dropdown, Tag, Dialog, FileUpload, Checkbox, DataTable, Column, TabView, TabPanel, Skeleton | None |
| DimensionManager.vue | useToast | Vuetify (inconsistent) |
| TriplestoreBrowser.vue | Dropdown, Tag, Button, Panel, TabView, TabPanel, InputText, Textarea, DataTable, Column, Dialog, FileUpload | None |
| Settings.vue | Button, InputText, InputNumber, InputSwitch, Dropdown, Checkbox, Password, DataTable, Column, Tag, Dialog | None |

---

## 4. State Management (Pinia Stores)

### 4.1 Store Summary

| Store | State Properties | Key Actions |
|-------|-----------------|-------------|
| auth | keycloak, isAuthenticated, userProfile, token | initKeycloak, login, logout, getToken |
| pipeline | pipelines, currentPipeline, operations, loading, error | fetchPipelines, createPipeline, updatePipeline, deletePipeline, validatePipeline, runPipeline |
| job | jobs, currentJob, currentJobLogs, schedules, loading, error | fetchJobs, createJob, cancelJob, retryJob, fetchJobLogs |
| shacl | shapes, currentShape, templates, lastValidationResult, loading, error | fetchShapes, createShape, validateSyntax, runValidation, inferShape |
| data | dataSources, currentDataSource, currentPreview, loading, error | fetchDataSources, uploadDataSource, previewDataSource, analyzeDataSource |
| dimension | dimensions, currentDimension, currentValues, totalDimensions, loading, error | fetchDimensions, createDimension, importValues |
| triplestore | connections, currentConnection, graphs, currentGraph, currentResource, lastQueryResult, loading, error | fetchConnections, executeSparql, uploadRdf |

### 4.2 Computed Getters

- **job store**: `runningJobs`, `pendingJobs` (filter by status)

---

## 5. API Integration

### 5.1 HTTP Client Configuration

```typescript
// Base: /api/v1, Timeout: 30s
// Auth: Bearer token interceptor
// Error: Redirect to login on 401
```

### 5.2 API Endpoints Summary

| Service | Endpoints |
|---------|-----------|
| Pipeline | GET/POST/PUT/DELETE /pipelines, POST /pipelines/validate, POST /pipelines/:id/run |
| Job | GET/POST/DELETE /jobs, POST /jobs/:id/retry, GET /jobs/:id/logs |
| SHACL | GET/POST/PUT/DELETE /shapes, POST /validation/run, POST /shapes/infer |
| Data | GET/POST/DELETE /data, POST /data/upload, GET /data/:id/preview |
| Dimension | GET/POST/PUT/DELETE /dimensions, POST /dimensions/:id/import/csv |
| Triplestore | GET/POST/PUT/DELETE /triplestores, POST /triplestores/:id/sparql |

---

## 6. Styling Approach

- **CSS Framework**: PrimeFlex 3.3.1 utility classes
- **Component Library**: PrimeVue Aura Light Indigo theme
- **Custom CSS**: Scoped styles with CSS variables
- **Font**: Inter (Google Fonts)

---

## 7. Testing Structure

- **Framework**: Vitest 1.5.0 with @vue/test-utils
- **Environment**: jsdom
- **Coverage**: v8 provider
- **Test Files**: client.test.ts, auth.test.ts, LoadingOverlay.test.ts

---

## 8. Migration Mapping

| Vue Pattern | Angular Equivalent |
|-------------|-------------------|
| `<script setup>` | Component class |
| `ref()/reactive()` | Class properties |
| `computed()` | Getters / Pipes |
| `watch()` | ngOnChanges / RxJS |
| `defineProps()` | @Input() |
| `defineEmits()` | @Output() + EventEmitter |
| Pinia | NgRx / Services |
| PrimeVue | PrimeNG |
| Vue Router | Angular Router |
| axios | HttpClient |

---

## 9. High-Risk Components

1. **PipelineDesigner.vue** - Vue Flow has no Angular equivalent
2. **JobMonitor.vue** - WebSocket log streaming
3. **DimensionManager.vue** - Uses Vuetify instead of PrimeVue

---

## 10. Summary Statistics

| Category | Count |
|----------|-------|
| View Components | 14 |
| Common Components | 2 |
| Pinia Stores | 8 |
| API Services | 7 |
| Routes | 15 |
| Test Files | 3 |

---

*Document generated: December 1, 2024*