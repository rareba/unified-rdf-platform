# Cube Creator Redesign — Pipeline-First Architecture

**Date:** 2026-03-11
**Status:** Approved
**Scope:** Replace the existing 6-step Cube Wizard with a 4-tab Cube Creator modeled after Zazuko's Cube Creator, where every action generates/modifies a real pipeline.

## Problem

The current Cube Wizard (`/cubes`) is a 6-step linear wizard that auto-generates a pipeline behind the scenes. Users never see the pipeline, can't customize it, and the cube creation experience is disconnected from the pipeline designer. Meanwhile, Zazuko's Cube Creator has a proven 4-tab workflow (CSV Mapping → Transform → Cube Designer → Publish) that Swiss government users are familiar with.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Integration model | Pipeline Factory — Cube Creator generates a real pipeline + artifacts | Pipeline is always the source of truth; advanced users can edit it |
| Post-creation view | Persistent 4-tab layout (CSV Mapping, Transform, Cube Designer, Publish) | Familiar to Cube Creator users, non-disruptive |
| Shared dimensions | Inline linking in CSV Mapping tab; Dimension Manager remains separate | Covers the key workflow without duplicating UI |
| Routing | `/cubes` list → `/cubes/:id` project view | Minimal disruption to existing navigation |
| Existing Cube Wizard | Replaced entirely | User requested dropping the current interface |

## Architecture

### Core Principle: Everything is a Pipeline

The Cube Creator is a guided UI that produces:

1. **Pipeline Definition** — a real pipeline (load-csv → create-observation → validate-shacl → graph-store-put) visible in the Pipeline Designer
2. **SHACL Shape** — `cube:Constraint` with property shapes, stored in SHACL service, editable in SHACL Studio
3. **Dimensions** — linked from Dimension Manager or created inline, exported as `schema:DefinedTermSet`
4. **Column Mappings** — stored in cube metadata (`CubeEntity.metadata.columnMappings`)
5. **Cube Metadata** — dct:title, dct:publisher, dcat:theme etc. stored in `CubeEntity.metadata`
6. **Data Source** — uploaded CSV in MinIO, referenced by pipeline's load-csv step

Each artifact is independently editable through its native editor. The Cube Creator tabs are a coordinated view over these artifacts.

### Data Flow

```
User uploads CSV
       ↓
CSV Mapping tab → creates CubeEntity with columnMappings in metadata
       ↓
User clicks "Run Transform" in Transform tab
       ↓
Frontend calls two sequential endpoints:
  1. POST /api/v1/cubes/:id/generate-pipeline → CubeService.generatePipeline()
     (also calls generateShape() internally — modified to do both in one call)
  2. POST /api/v1/pipelines/:pipelineId/run → starts job
       ↓
Cube Designer tab → queries triplestore for observations, displays preview
       ↓
Publish tab → runs graph-store-put step to publish to target endpoint
```

**Note:** `generatePipeline()` is modified to also call `generateShape()` internally when no shape exists yet, so a single POST produces both artifacts. If a shape already exists, it is regenerated only if column mappings have changed (detected via `mappingsVersion` — see CubeEntity Changes).

## Component Design

### 1. CubeListComponent (`/cubes`)

Replaces the old wizard's first step. Shows all cube projects as cards.

- **Display**: Card grid with cube name, observation count, last published date, status badge
- **Actions**: Create new, duplicate, delete, search/filter
- **Create flow**: Dialog for name + description → navigates to `/cubes/:id` (CSV Mapping tab)

### 2. CubeProjectComponent (`/cubes/:id`)

The main 4-tab view. Replaces the entire old Cube Wizard.

**Layout:**
- Top bar: Cube name + breadcrumb + `⚡ View Pipeline` button
- 4 tabs: CSV Mapping | Transform | Cube Designer | Publish
- Each tab is a child component loaded via `@if` based on `activeTab` signal

**State:**
- `cube: signal<Cube>` — the CubeEntity from backend
- `activeTab: signal<'mapping' | 'transform' | 'designer' | 'publish'>`
- `pipeline: signal<Pipeline | null>` — the linked pipeline (if generated)
- `shape: signal<Shape | null>` — the linked SHACL shape
- `dataSource: signal<DataSource | null>` — the linked CSV

**"View Pipeline" button:** Navigates to `/pipelines/:pipelineId` opening the existing Pipeline Designer. Only enabled after pipeline is generated.

### 3. Tab 1: CsvMappingTabComponent

Two-panel layout matching Cube Creator's visual mapping.

**Left panel — Input CSVs:**
- Upload area (drag-and-drop or button)
- For each uploaded CSV:
  - File name, row count, column count
  - Dropdown menu: edit settings (delimiter, encoding), replace, download, delete
  - Column list with checkboxes, sample values (3 examples), color-coded mapping dots
  - "Create table from selected columns" button

**Right panel — Output Tables:**
- Each output table shows:
  - Table type badge (Cube: observations, Concept: dimension values)
  - Column mappings list, each showing:
    - Property name
    - Datatype or linked shared dimension
    - Role badge (🔑 Key, 📏 Measure, 🏷️ Attribute)
    - Edit/delete buttons
  - "View generated CSVW" expandable (shows Turtle/JSON-LD serialization)

**Column Mapping Editor (side panel or dialog):**
- Property URI input (auto-generated from cube base URI + column name)
- Role selector: Key Dimension / Measure / Attribute / Ignore
- Datatype selector: xsd:string, xsd:integer, xsd:decimal, xsd:date, xsd:gYear, etc.
- Scale type: Nominal / Ordinal / Ratio / Interval (maps to qudt:scaleType)
- "Link to shared dimension" button → opens dimension search dialog
- For key dimensions: "Is key dimension" toggle
- For measures: Unit URI + Unit label inputs

**Backend interaction:**
- On column mapping changes: `PUT /api/v1/cubes/:id` updating `metadata.columnMappings`
- CSV upload: `POST /api/v1/data/upload` → stores file, returns DataSource ID → `PUT /api/v1/cubes/:id` linking `sourceDataId`

### 4. Tab 2: TransformTabComponent

Pipeline execution and job monitoring.

**Layout:**
- "Run Transform" button (primary action) — generates pipeline if not exists, then runs it
- Job history list with status badges:
  - ✓ Completed (green)
  - ⟳ Running (blue, animated)
  - ✗ Failed (red) with error summary
  - ⊘ Canceled (yellow)
- Each job row: version number, status, timestamp, "View log" link
- **Mini pipeline sidebar** (right side, 220px):
  - Vertical list of pipeline steps with colored left borders
  - "Open in Pipeline Designer →" link at bottom

**Backend interaction:**
- First "Run Transform":
  1. `POST /api/v1/cubes/:id/generate-pipeline` → creates pipeline + shape (single call, see Data Flow)
  2. `POST /api/v1/pipelines/:pipelineId/run` → starts job
- Subsequent runs: just run the existing pipeline
- Job polling: `GET /api/v1/jobs/:jobId` on interval until terminal state
- **Drift detection:** Compare `cube.mappingsVersion` (incremented on every column mapping save) against `cube.metadata.lastGeneratedMappingsVersion` (set when pipeline is generated). If they differ, show "Mappings changed, pipeline will be regenerated" warning and regenerate on next run.

### 5. Tab 3: CubeDesignerTabComponent

Observation preview and dimension metadata editing.

**Layout:**
- **Metadata bar** (top): Cube title, publisher, "Edit Metadata" button opening metadata dialog
- **Dimension cards row**: One card per dimension/measure showing:
  - Name (with language badge if multilingual)
  - Role badge (Key/Measure/Attribute)
  - Scale type icon
  - Data kind (if date, number, etc.)
  - "Linked: [SharedDimName]" or "Link to shared dimension" button
  - "Edit dimension" button → opens dimension metadata side panel
- **Observation preview table**:
  - Column headers from dimension names
  - Key dimensions: bold text
  - Measure dimensions: right-aligned, light colored background
  - Pagination: prev/next, page size (10/20/50/100), total count
  - Empty state: "No observations yet. Run a transformation first."

**Dimension metadata side panel:**
- Name (multilingual via altLabels)
- Description
- Scale type selector
- Data kind
- Unit (for measures)
- Link to shared dimension search

**Observation data source:**
- Query the cube's target triplestore/graph for `cube:Observation` instances
- Use `FetchObservationsOperation` or direct SPARQL via triplestore service
- Paginated (limit/offset in SPARQL)

**Backend interaction:**
- Observations: `GET /api/v1/cubes/:id/observations?page=0&size=10` — returns `ObservationPage` DTO (see API Contracts below). Precondition: `cube.triplestoreId` and `cube.graphUri` must be set (populated during pipeline generation).
- Dimension metadata: stored in `CubeEntity.metadata.columnMappings[].metadata`
- Shared dimension linking: `PUT /api/v1/cubes/:id` updating the relevant `columnMappings[].sharedDimensionUri` field (uses the `sharedDimensionUri` already supported by `CreateObservationOperation.DimensionConfig`). No separate `/link` endpoint needed.

### 6. Tab 4: PublishTabComponent

Publication management.

**Layout:**
- Three action cards:
  - **Publish to Triplestore** — runs the publish step of the pipeline
  - **Download RDF** — exports cube as Turtle/N-Triples/JSON-LD file
  - **Unlist Cube** — removes from published endpoint (with confirmation dialog)
- Publication history list (same format as job history)

**Backend interaction:**
- Publish: runs the pipeline (or just the graph-store-put step if observations already generated)
- Download: `GET /api/v1/cubes/:id/export?format=turtle` — see API Contracts below
- Unlist: `POST /api/v1/cubes/:id/unlist` — drops the named graph from the triplestore AND sets `cube.status = "draft"`. Uses the cube's `triplestoreId` and `graphUri` to call `DELETE` on the triplestore service's SPARQL Graph Store endpoint. Requires confirmation dialog in the frontend.

## Backend Changes Required

### CubeEntity Changes

Add fields to support the project model:

```java
// New fields on CubeEntity
private String status;           // "draft", "mapped", "transformed", "published"
private Integer mappingsVersion; // Incremented on every columnMappings save, used for drift detection
private UUID dataSourceId;       // already exists — link to uploaded CSV
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "csv_settings", columnDefinition = "jsonb")
private Map<String, Object> csvSettings; // delimiter, encoding, quoteChar
```

**Database migration required:** Add Flyway migration `V<next>__add_cube_project_fields.sql`:
```sql
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'draft';
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS mappings_version INTEGER DEFAULT 0;
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS csv_settings JSONB;
```

### New API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/cubes/:id/observations?page=0&size=10` | Paginated observation preview (proxies to triplestore) |
| POST | `/api/v1/cubes/:id/generate-pipeline` | Generate pipeline + shape from current mappings (modified to also call generateShape) |
| POST | `/api/v1/cubes/:id/generate-shape` | Already exists |
| GET | `/api/v1/cubes/:id/export?format=turtle` | Export cube RDF in specified format |
| POST | `/api/v1/cubes/:id/unlist` | Unlist cube: drop named graph + set status to draft |

### API Contracts

**`GET /api/v1/cubes/:id/observations`**
- Query params: `page` (int, default 0), `size` (int, default 10, max 100)
- Precondition: `cube.triplestoreId` and `cube.graphUri` must be set
- Implementation: Constructs SPARQL SELECT against the cube's triplestore, querying `cube:Observation` instances and their properties from the named graph
- Response `200 OK`:
```json
{
  "items": [
    {"year": "2022", "canton": "Zürich", "population": 1579967},
    {"year": "2022", "canton": "Bern", "population": 1047498}
  ],
  "columns": [
    {"name": "year", "propertyUri": "https://example.com/cube#year", "role": "dimension", "datatype": "xsd:gYear"},
    {"name": "canton", "propertyUri": "https://example.com/cube#canton", "role": "dimension"},
    {"name": "population", "propertyUri": "https://example.com/cube#population", "role": "measure", "datatype": "xsd:integer"}
  ],
  "totalCount": 1234,
  "page": 0,
  "size": 10
}
```

**`GET /api/v1/cubes/:id/export`**
- Query params: `format` — one of `turtle`, `ntriples`, `jsonld`, `trig`
- Implementation: Lives in **triplestore-service** (not dimension-service), since it needs Jena for RDF serialization. The cube controller proxies to triplestore-service: `GET /api/v1/triplestore/:triplestoreId/graphs/:graphUri/export?format=turtle`
- Response Content-Types: `text/turtle`, `application/n-triples`, `application/ld+json`, `application/trig`
- Response headers include `Content-Disposition: attachment; filename="cube-name.ttl"`

### CubeService Changes

- `generatePipeline()` — modify to also call `generateShape()` when no shape exists or mappings have changed. Store `lastGeneratedMappingsVersion` in cube metadata after generation. Add `emitConstraint: true` and `emitObservationSet: true` to the generated create-observation step params.
- Add `getObservationPreview(cubeId, page, pageSize)` — constructs SPARQL SELECT against linked triplestore, maps results to `ObservationPage` DTO
- Add `exportCube(cubeId, format)` — proxies to triplestore-service for graph export
- Add `unlistCube(cubeId)` — calls triplestore-service to drop the named graph, sets `cube.status = "draft"`, clears `cube.lastPublished`

## Frontend File Structure

```
features/cube/
├── cube-list/
│   └── cube-list.ts                    # Card grid of cube projects (replaces old wizard step 1)
├── cube-project/
│   ├── cube-project.ts                 # Main 4-tab shell component
│   ├── csv-mapping-tab/
│   │   ├── csv-mapping-tab.ts          # Two-panel CSV→RDF mapping
│   │   ├── csv-source-panel.ts         # Left panel: uploaded CSV columns
│   │   ├── output-table-panel.ts       # Right panel: RDF output tables
│   │   └── column-mapping-editor.ts    # Side panel for editing one column mapping
│   ├── transform-tab/
│   │   ├── transform-tab.ts            # Run pipeline + job history
│   │   └── mini-pipeline-preview.ts    # Small vertical pipeline graph
│   ├── cube-designer-tab/
│   │   ├── cube-designer-tab.ts        # Dimension cards + observation table
│   │   ├── dimension-card.ts           # Single dimension summary card
│   │   ├── dimension-edit-panel.ts     # Side panel for dimension metadata
│   │   └── observation-preview.ts      # Paginated observation table
│   └── publish-tab/
│       └── publish-tab.ts              # Publish/download/unlist actions
├── shared/
│   ├── shared-dimension-search.ts      # Dialog to search/link shared dimensions
│   └── cube-metadata-dialog.ts         # Dialog for editing cube-level metadata
└── cube-definition-editor/             # DELETED — replaced by new components
    └── cube-definition-editor.ts       # DELETED
```

## Routing Changes

```typescript
// app.routes.ts changes
{
  path: 'cubes',
  canActivate: [authGuard],
  children: [
    { path: '', loadComponent: () => import('./features/cube/cube-list/cube-list') },
    { path: 'new', loadComponent: () => import('./features/cube/cube-project/cube-project') },
    { path: ':id', loadComponent: () => import('./features/cube/cube-project/cube-project') },
  ]
}
```

## Files to Delete

- `features/cube/cube-wizard/cube-wizard.ts` — replaced by cube-project
- `features/cube/cube-definition-editor/cube-definition-editor.ts` — replaced by csv-mapping-tab + column-mapping-editor

## Files to Modify

- `app.routes.ts` — update cube routes
- `core/services/cube.service.ts` — add new API methods (getObservations, export, unlist)
- `core/models/cube.model.ts` — add status field, CsvSettings interface
- Backend: `CubeController.java` — add new endpoints
- Backend: `CubeService.java` — add observation preview, export, unlist methods
- Backend: `CubeEntity.java` — add status field

## Migration Strategy

1. Build new components alongside old ones (no breaking changes during development)
2. New route structure coexists: old `/cubes` wizard still works
3. Feature flag or gradual rollout: swap routes when ready
4. Delete old components after validation

## Success Criteria

- [ ] 4-tab layout matches Cube Creator workflow
- [ ] CSV Mapping tab shows two-panel layout with visual column mapping
- [ ] Transform tab generates and runs a real pipeline
- [ ] "View Pipeline" button opens the pipeline in Pipeline Designer
- [ ] Cube Designer shows observation preview table with pagination
- [ ] Each artifact (shape, dimensions, pipeline) editable independently
- [ ] Publish tab can push to triplestore and unlist
- [ ] Column mappings support linking to shared dimensions
- [ ] qudt:scaleType shown per dimension
- [ ] emitConstraint and emitObservationSet used in generated pipelines
