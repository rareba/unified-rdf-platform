# Cube Creator Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 6-step Cube Wizard with a 4-tab Cube Creator (CSV Mapping, Transform, Cube Designer, Publish) where every action generates a real pipeline.

**Architecture:** Pipeline Factory pattern — the Cube Creator is a guided UI that produces a real pipeline + SHACL shape + column mappings + cube metadata, each independently editable. The 4-tab layout mirrors Zazuko's Cube Creator for user familiarity, with a "View Pipeline" escape hatch on every tab.

**Tech Stack:** Angular 21 (standalone components, Signals, OnPush), Angular Material + Oblique, Keycloak JS, Spring Boot 3.4.3, Spring Cloud 2024.0.1, PostgreSQL (Flyway), Apache Jena 5.0, Apache Camel 4.5, Resilience4j

**Spec:** `docs/superpowers/specs/2026-03-11-cube-creator-redesign.md`

---

## File Structure

### Backend (dimension-service)

| File | Action | Responsibility |
|------|--------|---------------|
| `entity/CubeEntity.java` | Modify | Add `status`, `mappingsVersion`, `csvSettings` fields |
| `db/migration/V6__add_cube_project_fields.sql` | Create | Flyway migration for new columns |
| `controller/CubeController.java` | Modify | Add observations, export, unlist endpoints |
| `service/CubeService.java` | Modify | Add `getObservationPreview()`, `exportCube()`, `unlistCube()`, modify `generatePipeline()` to also generate shape, add `mappingsVersion` bump in `update()` |
| `dto/ObservationPage.java` | Create | DTO for paginated observation preview |
| `dto/ObservationColumn.java` | Create | DTO for observation column metadata |

### Frontend (rdf-forge-ui/src/app/)

| File | Action | Responsibility |
|------|--------|---------------|
| `core/models/cube.model.ts` | Modify | Add `status`, `mappingsVersion`, `CsvSettings`, `ColumnMapping`, `ObservationPage` interfaces |
| `core/services/cube.service.ts` | Modify | Add `getObservations()`, `exportCube()`, `unlistCube()` methods |
| `features/cube/cube-list/cube-list.ts` | Create | Card grid of cube projects |
| `features/cube/cube-project/cube-project.ts` | Create | Main 4-tab shell component |
| `features/cube/cube-project/csv-mapping-tab/csv-mapping-tab.ts` | Create | Two-panel CSV mapping |
| `features/cube/cube-project/csv-mapping-tab/csv-source-panel.ts` | Create | Left panel: uploaded CSV columns |
| `features/cube/cube-project/csv-mapping-tab/output-table-panel.ts` | Create | Right panel: RDF output tables |
| `features/cube/cube-project/csv-mapping-tab/column-mapping-editor.ts` | Create | Side panel for editing one column mapping |
| `features/cube/cube-project/transform-tab/transform-tab.ts` | Create | Run pipeline + job history |
| `features/cube/cube-project/transform-tab/mini-pipeline-preview.ts` | Create | Small vertical pipeline steps |
| `features/cube/cube-project/cube-designer-tab/cube-designer-tab.ts` | Create | Dimension cards + observation table |
| `features/cube/cube-project/cube-designer-tab/dimension-card.ts` | Create | Single dimension summary card |
| `features/cube/cube-project/cube-designer-tab/dimension-edit-panel.ts` | Create | Side panel for dimension metadata |
| `features/cube/cube-project/cube-designer-tab/observation-preview.ts` | Create | Paginated observation table |
| `features/cube/cube-project/publish-tab/publish-tab.ts` | Create | Publish/download/unlist actions |
| `features/cube/shared/shared-dimension-search.ts` | Create | Dialog to search/link shared dimensions |
| `features/cube/shared/cube-metadata-dialog.ts` | Create | Dialog for editing cube-level metadata |
| `app.routes.ts` | Modify | Update cube routes to list/detail pattern |

### Files to Delete (after validation)

| File | Reason |
|------|--------|
| `features/cube/cube-wizard/cube-wizard.ts` | Replaced by cube-project |
| `features/cube/cube-definition-editor/cube-definition-editor.ts` | Replaced by csv-mapping-tab |

---

## Chunk 1: Backend — Entity, Migration, New Endpoints

### Task 1.1: Add CubeEntity Fields + Flyway Migration

**Files:**
- Modify: `rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/entity/CubeEntity.java`
- Create: `rdf-forge/rdf-forge-dimension-service/src/main/resources/db/migration/V6__add_cube_project_fields.sql`

- [ ] **Step 1: Create Flyway migration**

Create `V6__add_cube_project_fields.sql`:

```sql
-- Cube Creator Redesign: Add project workflow fields
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'draft';
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS mappings_version INTEGER DEFAULT 0;
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS csv_settings JSONB;

-- Index on status for filtering
CREATE INDEX IF NOT EXISTS idx_cubes_status ON cubes(status);

COMMENT ON COLUMN cubes.status IS 'Cube lifecycle: draft, mapped, transformed, published';
COMMENT ON COLUMN cubes.mappings_version IS 'Incremented on every columnMappings save, used for drift detection';
COMMENT ON COLUMN cubes.csv_settings IS 'CSV parsing settings: delimiter, encoding, quoteChar';
```

- [ ] **Step 2: Add fields to CubeEntity.java**

Add after the `updatedAt` field:

```java
@Column(length = 50)
private String status = "draft";

@Column(name = "mappings_version")
private Integer mappingsVersion = 0;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "csv_settings", columnDefinition = "jsonb")
private Map<String, Object> csvSettings;
```

Add getters and setters for all three fields.

- [ ] **Step 3: Verify entity compiles**

Run: `cd rdf-forge && ./mvnw compile -pl rdf-forge-dimension-service -am -q` (or verify by reading the file if Maven is not available)

- [ ] **Step 4: Commit**

```bash
git add rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/entity/CubeEntity.java
git add rdf-forge/rdf-forge-dimension-service/src/main/resources/db/migration/V6__add_cube_project_fields.sql
git commit -m "feat(cube): add status, mappingsVersion, csvSettings fields to CubeEntity"
```

### Task 1.2: Create ObservationPage DTOs

**Files:**
- Create: `rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/dto/ObservationPage.java`
- Create: `rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/dto/ObservationColumn.java`

- [ ] **Step 1: Create ObservationColumn DTO**

```java
package io.rdfforge.dimension.dto;

public record ObservationColumn(
    String name,
    String propertyUri,
    String role,       // "dimension", "measure", "attribute"
    String datatype    // "xsd:string", "xsd:integer", etc. (nullable)
) {}
```

- [ ] **Step 2: Create ObservationPage DTO**

```java
package io.rdfforge.dimension.dto;

import java.util.List;
import java.util.Map;

public record ObservationPage(
    List<Map<String, Object>> items,
    List<ObservationColumn> columns,
    long totalCount,
    int page,
    int size
) {}
```

- [ ] **Step 3: Commit**

```bash
git add rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/dto/ObservationPage.java
git add rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/dto/ObservationColumn.java
git commit -m "feat(cube): add ObservationPage and ObservationColumn DTOs"
```

### Task 1.3: Add CubeService Methods — Observation Preview, Export, Unlist

**Files:**
- Modify: `rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/service/CubeService.java`

- [ ] **Step 1: Add triplestore service URL value and URI validation helper**

Add after the existing `@Value` fields:

```java
@Value("${rdf-forge.services.triplestore.url:http://localhost:8006}")
private String triplestoreServiceUrl;
```

Add a URI validation helper (prevents SPARQL injection via graphUri):

```java
private static final java.util.regex.Pattern SAFE_URI_PATTERN =
    java.util.regex.Pattern.compile("^https?://[\\w.:\\-/]+[\\w.:\\-/#?&=%]*$");

private String validateGraphUri(String graphUri) {
    if (graphUri == null || !SAFE_URI_PATTERN.matcher(graphUri).matches()) {
        throw new IllegalArgumentException("Invalid graph URI: " + graphUri);
    }
    return graphUri;
}
```

- [ ] **Step 2: Add getObservationPreview method**

Add after `unlinkPipeline()`:

```java
/**
 * Fetch paginated observation preview from the cube's triplestore graph.
 */
@Transactional(readOnly = true)
@CircuitBreaker(name = "triplestoreService", fallbackMethod = "getObservationPreviewFallback")
public ObservationPage getObservationPreview(UUID cubeId, int page, int size) {
    CubeEntity cube = cubeRepository.findById(cubeId)
            .orElseThrow(() -> new ResourceNotFoundException("Cube", cubeId.toString()));

    if (cube.getTriplestoreId() == null || cube.getGraphUri() == null) {
        return new ObservationPage(List.of(), List.of(), 0, page, size);
    }

    // Validate graph URI to prevent SPARQL injection
    String safeGraphUri = validateGraphUri(cube.getGraphUri());

    // Build column list from metadata for explicit SPARQL projection
    List<ObservationColumn> columns = buildColumnsFromMetadata(cube);
    if (columns.isEmpty()) {
        return new ObservationPage(List.of(), columns, 0, page, size);
    }

    // Build SPARQL to count observations
    String countSparql = "SELECT (COUNT(DISTINCT ?s) AS ?count) WHERE { GRAPH <" + safeGraphUri
            + "> { ?s a <http://purl.org/linked-data/cube#Observation> } }";

    // Build SPARQL to fetch observations with explicit column projection (pivoted)
    StringBuilder selectBuilder = new StringBuilder("SELECT DISTINCT ?s");
    StringBuilder whereBuilder = new StringBuilder();
    for (ObservationColumn col : columns) {
        String varName = col.name().replaceAll("[^a-zA-Z0-9]", "_");
        selectBuilder.append(" ?").append(varName);
        whereBuilder.append("  OPTIONAL { ?s <").append(col.propertyUri()).append("> ?").append(varName).append(" . }\n");
    }
    int offset = page * size;
    String selectSparql = selectBuilder + " WHERE { GRAPH <" + safeGraphUri
            + "> {\n  ?s a <http://purl.org/linked-data/cube#Observation> .\n"
            + whereBuilder + "} } LIMIT " + size + " OFFSET " + offset;

    // Proxy to triplestore service
    String queryUrl = triplestoreServiceUrl + "/api/v1/triplestores/" + cube.getTriplestoreId() + "/query";

    // Execute count query
    long totalCount = 0;
    try {
        @SuppressWarnings("unchecked")
        Map<String, Object> countResult = restTemplate.postForObject(
                queryUrl, Map.of("query", countSparql), Map.class);
        if (countResult != null && countResult.containsKey("results")) {
            totalCount = extractCount(countResult);
        }
    } catch (Exception e) {
        log.warn("Failed to count observations for cube {}: {}", cubeId, e.getMessage());
    }

    // Execute select query
    List<Map<String, Object>> items = new ArrayList<>();
    try {
        @SuppressWarnings("unchecked")
        Map<String, Object> selectResult = restTemplate.postForObject(
                queryUrl, Map.of("query", selectSparql), Map.class);
        if (selectResult != null) {
            items = extractObservationRows(selectResult);
        }
    } catch (Exception e) {
        log.warn("Failed to fetch observations for cube {}: {}", cubeId, e.getMessage());
    }

    // Build columns from cube metadata
    List<ObservationColumn> columns = buildColumnsFromMetadata(cube);

    return new ObservationPage(items, columns, totalCount, page, size);
}

private ObservationPage getObservationPreviewFallback(UUID cubeId, int page, int size, Throwable t) {
    log.error("Triplestore service unavailable for observation preview cubeId={}: {}", cubeId, t.getMessage());
    return new ObservationPage(List.of(), List.of(), 0, page, size);
}
```

- [ ] **Step 3: Add exportCube method**

```java
/**
 * Export cube RDF from triplestore in specified format.
 * Proxies to triplestore-service graph export endpoint.
 */
@CircuitBreaker(name = "triplestoreService")
public byte[] exportCube(UUID cubeId, String format) {
    CubeEntity cube = cubeRepository.findById(cubeId)
            .orElseThrow(() -> new ResourceNotFoundException("Cube", cubeId.toString()));

    if (cube.getTriplestoreId() == null || cube.getGraphUri() == null) {
        throw new IllegalStateException("Cube has no triplestore or graph URI configured");
    }

    String exportUrl = triplestoreServiceUrl + "/api/v1/triplestores/"
            + cube.getTriplestoreId() + "/graphs/"
            + java.net.URLEncoder.encode(cube.getGraphUri(), java.nio.charset.StandardCharsets.UTF_8)
            + "/export?format=" + format;

    try {
        return restTemplate.getForObject(exportUrl, byte[].class);
    } catch (Exception e) {
        throw new RuntimeException("Failed to export cube: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 4: Add unlistCube method**

```java
/**
 * Unlist cube: drop named graph from triplestore and set status to draft.
 * Both the graph deletion and status update must succeed together.
 */
public CubeEntity unlistCube(UUID cubeId) {
    CubeEntity cube = cubeRepository.findById(cubeId)
            .orElseThrow(() -> new ResourceNotFoundException("Cube", cubeId.toString()));

    if (cube.getTriplestoreId() != null && cube.getGraphUri() != null) {
        // Drop the named graph from triplestore — propagate failure
        String dropUrl = triplestoreServiceUrl + "/api/v1/triplestores/"
                + cube.getTriplestoreId() + "/graphs/"
                + java.net.URLEncoder.encode(cube.getGraphUri(), java.nio.charset.StandardCharsets.UTF_8);
        restTemplate.delete(dropUrl);
    }

    cube.setStatus("draft");
    cube.setLastPublished(null);
    cube.setUpdatedAt(Instant.now());
    return cubeRepository.save(cube);
}
```

- [ ] **Step 5: Modify generatePipeline to also generate shape**

In the existing `generatePipeline()` method, add after the cube lookup (line ~182):

```java
// Auto-generate shape if none exists or if mappings have changed
Integer currentVersion = cube.getMappingsVersion() != null ? cube.getMappingsVersion() : 0;
Object lastGenVersion = cube.getMetadata() != null
        ? cube.getMetadata().get("lastGeneratedMappingsVersion") : null;
int lastGenVersionInt = lastGenVersion instanceof Number n ? n.intValue() : -1;

if (cube.getShapeId() == null || currentVersion != lastGenVersionInt) {
    try {
        generateShape(cubeId, cube.getName() + " Validation Shape",
                "http://purl.org/linked-data/cube#Observation");
        // Reload cube after shape generation
        cube = cubeRepository.findById(cubeId).orElseThrow();
    } catch (Exception e) {
        log.warn("Auto shape generation failed for cube {}: {}", cubeId, e.getMessage());
    }
}
```

Also, in the existing `buildPipelineDefinition()` method, add `emitConstraint` and `emitObservationSet` to the create-observation step params. Find the line `json.append("        \"emitUndefined\": true,\n");` (around line 450) and add after it:

```java
json.append("        \"emitConstraint\": true,\n");
json.append("        \"emitObservationSet\": true,\n");
```

This ensures the generated pipeline produces inline SHACL constraints and ObservationSet wrappers as specified.

After pipeline creation succeeds, store `lastGeneratedMappingsVersion`:

```java
// After cubeRepository.save(cube) in generatePipeline:
Map<String, Object> meta = cube.getMetadata() != null ? new java.util.HashMap<>(cube.getMetadata()) : new java.util.HashMap<>();
meta.put("lastGeneratedMappingsVersion", cube.getMappingsVersion());
cube.setMetadata(meta);
cubeRepository.save(cube);
```

- [ ] **Step 6: Add mappingsVersion bump in update method**

In the existing `update()` method, after the metadata update check:

```java
if (updates.getMetadata() != null) {
    // Check if columnMappings changed
    Object oldMappings = cube.getMetadata() != null ? cube.getMetadata().get("columnMappings") : null;
    Object newMappings = updates.getMetadata().get("columnMappings");
    if (newMappings != null && !newMappings.equals(oldMappings)) {
        cube.setMappingsVersion(
            (cube.getMappingsVersion() != null ? cube.getMappingsVersion() : 0) + 1);
    }
    cube.setMetadata(updates.getMetadata());
}
```

- [ ] **Step 7: Add helper methods**

Add the helper methods used by `getObservationPreview()`:

```java
@SuppressWarnings("unchecked")
private long extractCount(Map<String, Object> sparqlResult) {
    try {
        Map<String, Object> results = (Map<String, Object>) sparqlResult.get("results");
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) results.get("bindings");
        if (bindings != null && !bindings.isEmpty()) {
            Map<String, Object> countBinding = (Map<String, Object>) bindings.get(0).get("count");
            return Long.parseLong((String) countBinding.get("value"));
        }
    } catch (Exception e) {
        log.warn("Failed to parse count result: {}", e.getMessage());
    }
    return 0;
}

@SuppressWarnings("unchecked")
private List<Map<String, Object>> extractObservationRows(Map<String, Object> sparqlResult) {
    List<Map<String, Object>> rows = new ArrayList<>();
    try {
        Map<String, Object> results = (Map<String, Object>) sparqlResult.get("results");
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) results.get("bindings");
        if (bindings != null) {
            for (Map<String, Object> binding : bindings) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : binding.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> valMap) {
                        row.put(entry.getKey(), valMap.get("value"));
                    }
                }
                rows.add(row);
            }
        }
    } catch (Exception e) {
        log.warn("Failed to parse observation rows: {}", e.getMessage());
    }
    return rows;
}

@SuppressWarnings("unchecked")
private List<ObservationColumn> buildColumnsFromMetadata(CubeEntity cube) {
    List<ObservationColumn> columns = new ArrayList<>();
    Map<String, Object> metadata = cube.getMetadata();
    if (metadata != null && metadata.containsKey("columnMappings")) {
        Object mappingsObj = metadata.get("columnMappings");
        if (mappingsObj instanceof List<?> mappings) {
            for (Object m : mappings) {
                if (m instanceof Map<?, ?> mapping) {
                    String role = (String) mapping.get("role");
                    if (role != null && !"ignore".equals(role)) {
                        columns.add(new ObservationColumn(
                            (String) mapping.get("name"),
                            (String) mapping.get("predicateUri"),
                            role,
                            (String) mapping.get("datatype")
                        ));
                    }
                }
            }
        }
    }
    return columns;
}
```

- [ ] **Step 8: Add required imports**

At the top of CubeService.java, add:

```java
import io.rdfforge.dimension.dto.ObservationPage;
import io.rdfforge.dimension.dto.ObservationColumn;
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 9: Commit**

```bash
git add rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/service/CubeService.java
git commit -m "feat(cube): add observation preview, export, unlist, and auto-shape generation"
```

### Task 1.4: Add Controller Endpoints

**Files:**
- Modify: `rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/controller/CubeController.java`

- [ ] **Step 1: Add observations endpoint**

Add after the existing `unlinkPipeline` endpoint:

```java
@GetMapping("/{id}/observations")
@Operation(summary = "Preview observations", description = "Get paginated observation preview from cube's triplestore")
public ResponseEntity<ObservationPage> getObservations(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    if (size > 100) size = 100;
    ObservationPage result = cubeService.getObservationPreview(id, page, size);
    return ResponseEntity.ok(result);
}
```

- [ ] **Step 2: Add export endpoint**

```java
private static final java.util.Set<String> VALID_EXPORT_FORMATS =
    java.util.Set.of("turtle", "ntriples", "jsonld", "trig");

@GetMapping("/{id}/export")
@Operation(summary = "Export cube RDF", description = "Export cube as RDF in specified format")
public ResponseEntity<byte[]> exportCube(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "turtle") String format) {
    if (!VALID_EXPORT_FORMATS.contains(format)) {
        return ResponseEntity.badRequest().build();
    }

    byte[] data = cubeService.exportCube(id, format);

    // Look up cube name for filename
    String cubeName = cubeService.findById(id)
            .map(c -> c.getName().replaceAll("[^a-zA-Z0-9_-]", "_"))
            .orElse("cube-export");

    String contentType = switch (format) {
        case "ntriples" -> "application/n-triples";
        case "jsonld" -> "application/ld+json";
        case "trig" -> "application/trig";
        default -> "text/turtle";
    };

    String extension = switch (format) {
        case "ntriples" -> ".nt";
        case "jsonld" -> ".jsonld";
        case "trig" -> ".trig";
        default -> ".ttl";
    };

    return ResponseEntity.ok()
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=\"" + cubeName + extension + "\"")
            .body(data);
}
```

- [ ] **Step 3: Add unlist endpoint**

```java
@PostMapping("/{id}/unlist")
@Operation(summary = "Unlist cube", description = "Drop named graph from triplestore and set cube to draft status")
public ResponseEntity<CubeEntity> unlistCube(@PathVariable UUID id) {
    CubeEntity updated = cubeService.unlistCube(id);
    return ResponseEntity.ok(updated);
}
```

- [ ] **Step 4: Add import for ObservationPage DTO**

```java
import io.rdfforge.dimension.dto.ObservationPage;
```

- [ ] **Step 5: Commit**

```bash
git add rdf-forge/rdf-forge-dimension-service/src/main/java/io/rdfforge/dimension/controller/CubeController.java
git commit -m "feat(cube): add observations, export, and unlist API endpoints"
```

---

## Chunk 2: Frontend Foundation — Models, Service, CubeList, CubeProject Shell

### Task 2.1: Update Cube Model

**Files:**
- Modify: `rdf-forge/rdf-forge-ui/src/app/core/models/cube.model.ts`

- [ ] **Step 1: Add new interfaces and update Cube interface**

Replace the entire file with:

```typescript
export type CubeStatus = 'draft' | 'mapped' | 'transformed' | 'published';

export interface Cube {
  id: string;
  uri: string;
  name: string;
  description?: string;
  status?: CubeStatus;
  sourceDataId?: string;
  pipelineId?: string;
  shapeId?: string;
  triplestoreId?: string;
  graphUri?: string;
  observationCount?: number;
  mappingsVersion?: number;
  metadata?: CubeMetadata;
  csvSettings?: CsvSettings;
  lastPublished?: Date;
  createdBy?: string;
  createdAt: Date;
  updatedAt?: Date;
}

export interface CubeMetadata {
  columnMappings?: ColumnMapping[];
  lastGeneratedMappingsVersion?: number;
  [key: string]: unknown;
}

export interface CsvSettings {
  delimiter?: string;
  encoding?: string;
  quoteChar?: string;
}

export interface ColumnMapping {
  name: string;
  role: 'dimension' | 'measure' | 'attribute' | 'ignore';
  datatype?: string;
  predicateUri?: string;
  keyDimension?: boolean;
  scaleType?: string;
  unitUri?: string;
  unitLabel?: string;
  sharedDimensionUri?: string;
  metadata?: Record<string, unknown>;
}

export interface ObservationPage {
  items: Record<string, unknown>[];
  columns: ObservationColumn[];
  totalCount: number;
  page: number;
  size: number;
}

export interface ObservationColumn {
  name: string;
  propertyUri: string;
  role: string;
  datatype?: string;
}

export interface CsvPreview {
  fileName: string;
  rowCount: number;
  columns: CsvColumnPreview[];
}

export interface CsvColumnPreview {
  name: string;
  sampleValues: string[];
  mapped: boolean;
}

export interface CubeCreateRequest {
  uri: string;
  name: string;
  description?: string;
  sourceDataId?: string;
  pipelineId?: string;
  shapeId?: string;
  triplestoreId?: string;
  graphUri?: string;
  metadata?: CubeMetadata;
}
```

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/core/models/cube.model.ts
git commit -m "feat(cube): add CubeStatus, ColumnMapping, ObservationPage model types"
```

### Task 2.2: Update Cube Service

**Files:**
- Modify: `rdf-forge/rdf-forge-ui/src/app/core/services/cube.service.ts`

- [ ] **Step 1: Add `getBlob` method to ApiService**

`ApiService` does not have a `getBlob` method. Add it to `rdf-forge/rdf-forge-ui/src/app/core/services/api.service.ts` after the existing `delete` method:

```typescript
getBlob(url: string, params?: Record<string, unknown>): Observable<Blob> {
  let httpParams = new HttpParams();
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        httpParams = httpParams.set(key, String(value));
      }
    });
  }
  return this.http.get(`${this.baseUrl}${url}`, {
    params: httpParams,
    responseType: 'blob'
  });
}
```

- [ ] **Step 2: Add new imports and methods to CubeService**

Add to imports in `cube.service.ts`:

```typescript
import { Cube, CubeCreateRequest, ObservationPage } from '../models/cube.model';
```

Add after `unlinkPipeline()`:

```typescript
// ===== Cube Creator endpoints =====

getObservations(cubeId: string, page = 0, size = 10): Observable<ObservationPage> {
  return this.api.get<ObservationPage>(`/cubes/${cubeId}/observations`, { page, size });
}

exportCube(cubeId: string, format: string = 'turtle'): Observable<Blob> {
  return this.api.getBlob(`/cubes/${cubeId}/export`, { format });
}

unlistCube(cubeId: string): Observable<Cube> {
  return this.api.post<Cube>(`/cubes/${cubeId}/unlist`, {});
}
```

- [ ] **Step 3: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/core/services/api.service.ts
git add rdf-forge/rdf-forge-ui/src/app/core/services/cube.service.ts
git commit -m "feat(cube): add getBlob to ApiService, add observations/export/unlist to CubeService"
```

### Task 2.3: Create CubeList Component

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-list/cube-list.ts`

- [ ] **Step 1: Create the cube list component**

This replaces the old wizard's first step. Card grid of cube projects with create/delete/search.

```typescript
import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CubeService } from '../../../core/services/cube.service';
import { Cube, CubeStatus, CubeMetadata } from '../../../core/models/cube.model';

@Component({
  selector: 'app-cube-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatInputModule,
    MatFormFieldModule, MatChipsModule, MatMenuModule,
    MatDialogModule, MatProgressSpinnerModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cube-list-container">
      <div class="cube-list-header">
        <h1>Cube Projects</h1>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search cubes</mat-label>
            <input matInput [ngModel]="searchTerm()" (ngModelChange)="onSearch($event)" />
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="createCube()">
            <mat-icon>add</mat-icon> New Cube
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (cubes().length === 0) {
        <div class="empty-state">
          <mat-icon class="empty-icon">data_object</mat-icon>
          <h2>No cube projects yet</h2>
          <p>Create your first cube to start mapping CSV data to RDF.</p>
          <button mat-raised-button color="primary" (click)="createCube()">
            <mat-icon>add</mat-icon> Create Cube
          </button>
        </div>
      } @else {
        <div class="cube-grid">
          @for (cube of cubes(); track cube.id) {
            <mat-card class="cube-card" (click)="openCube(cube.id)">
              <mat-card-header>
                <mat-card-title>{{ cube.name }}</mat-card-title>
                <mat-card-subtitle>
                  <span class="status-badge" [attr.data-status]="cube.status || 'draft'">
                    {{ cube.status || 'draft' }}
                  </span>
                </mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @if (cube.description) {
                  <p class="cube-description">{{ cube.description }}</p>
                }
                <div class="cube-stats">
                  @if (cube.observationCount) {
                    <span class="stat">{{ cube.observationCount }} observations</span>
                  }
                  @if (cube.lastPublished) {
                    <span class="stat">Published {{ cube.lastPublished | date:'short' }}</span>
                  }
                </div>
              </mat-card-content>
              <mat-card-actions align="end">
                <button mat-icon-button [matMenuTriggerFor]="cubeMenu" (click)="$event.stopPropagation()">
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #cubeMenu="matMenu">
                  <button mat-menu-item (click)="openCube(cube.id)">
                    <mat-icon>open_in_new</mat-icon> Open
                  </button>
                  <button mat-menu-item (click)="duplicateCube(cube)">
                    <mat-icon>content_copy</mat-icon> Duplicate
                  </button>
                  <button mat-menu-item (click)="deleteCube(cube)">
                    <mat-icon>delete</mat-icon> Delete
                  </button>
                </mat-menu>
              </mat-card-actions>
            </mat-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .cube-list-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .cube-list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .header-actions { display: flex; gap: 16px; align-items: center; }
    .search-field { width: 280px; }
    .cube-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
    .cube-card { cursor: pointer; transition: box-shadow 0.2s; }
    .cube-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
    .status-badge { padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; text-transform: uppercase; }
    .status-badge[data-status="draft"] { background: #e0e0e0; color: #616161; }
    .status-badge[data-status="mapped"] { background: #fff3e0; color: #e65100; }
    .status-badge[data-status="transformed"] { background: #e3f2fd; color: #1565c0; }
    .status-badge[data-status="published"] { background: #e8f5e9; color: #2e7d32; }
    .cube-description { color: #666; font-size: 14px; margin: 8px 0; }
    .cube-stats { display: flex; gap: 16px; font-size: 12px; color: #999; }
    .loading-container, .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; }
    .empty-icon { font-size: 64px; width: 64px; height: 64px; color: #ccc; margin-bottom: 16px; }
    .empty-state h2 { color: #666; }
    .empty-state p { color: #999; margin-bottom: 24px; }
  `]
})
export class CubeList implements OnInit {
  private readonly cubeService = inject(CubeService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  cubes = signal<Cube[]>([]);
  loading = signal(true);
  searchTerm = signal('');

  ngOnInit() {
    this.loadCubes();
  }

  loadCubes() {
    this.loading.set(true);
    this.cubeService.list({ search: this.searchTerm() || undefined }).subscribe({
      next: (cubes) => {
        this.cubes.set(cubes);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearch(term: string) {
    this.searchTerm.set(term);
    this.loadCubes();
  }

  createCube() {
    this.router.navigate(['/cubes/new']);
  }

  openCube(id: string) {
    this.router.navigate(['/cubes', id]);
  }

  duplicateCube(cube: Cube) {
    this.cubeService.create({
      uri: cube.uri + '-copy',
      name: cube.name + ' (copy)',
      description: cube.description,
      metadata: cube.metadata as CubeMetadata
    }).subscribe(newCube => this.openCube(newCube.id));
  }

  deleteCube(cube: Cube) {
    if (confirm(`Delete cube "${cube.name}"? This cannot be undone.`)) {
      this.cubeService.delete(cube.id).subscribe(() => this.loadCubes());
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-list/cube-list.ts
git commit -m "feat(cube): create CubeList component with card grid layout"
```

### Task 2.4: Create CubeProject Shell Component (4-Tab)

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-project.ts`

- [ ] **Step 1: Create the cube project shell with 4 tabs**

```typescript
import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CubeService } from '../../../core/services/cube.service';
import { Cube } from '../../../core/models/cube.model';

export type CubeTab = 'mapping' | 'transform' | 'designer' | 'publish';

@Component({
  selector: 'app-cube-project',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatDialogModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cube-project-container">
      @if (loading()) {
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (cube()) {
        <!-- Top bar -->
        <div class="project-header">
          <div class="header-left">
            <button mat-icon-button (click)="goBack()">
              <mat-icon>arrow_back</mat-icon>
            </button>
            <div>
              <h1 class="project-title">{{ cube()!.name }}</h1>
              <span class="status-badge" [attr.data-status]="cube()!.status || 'draft'">
                {{ cube()!.status || 'draft' }}
              </span>
            </div>
          </div>
          <div class="header-right">
            @if (cube()!.pipelineId) {
              <button mat-stroked-button (click)="viewPipeline()">
                <mat-icon>bolt</mat-icon> View Pipeline
              </button>
            }
          </div>
        </div>

        <!-- 4 Tabs -->
        <mat-tab-group
          [selectedIndex]="tabIndex()"
          (selectedIndexChange)="onTabChange($event)"
          animationDuration="200ms">
          <mat-tab label="CSV Mapping">
            <ng-template matTabContent>
              <div class="tab-content">
                <p class="tab-placeholder">CSV Mapping tab — to be implemented in Task 3.1</p>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Transform">
            <ng-template matTabContent>
              <div class="tab-content">
                <p class="tab-placeholder">Transform tab — to be implemented in Task 3.2</p>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Cube Designer">
            <ng-template matTabContent>
              <div class="tab-content">
                <p class="tab-placeholder">Cube Designer tab — to be implemented in Task 3.3</p>
              </div>
            </ng-template>
          </mat-tab>
          <mat-tab label="Publish">
            <ng-template matTabContent>
              <div class="tab-content">
                <p class="tab-placeholder">Publish tab — to be implemented in Task 3.4</p>
              </div>
            </ng-template>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <div class="error-state">
          <mat-icon>error</mat-icon>
          <h2>Cube not found</h2>
          <button mat-raised-button (click)="goBack()">Back to Cubes</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .cube-project-container { max-width: 1400px; margin: 0 auto; padding: 16px 24px; }
    .project-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .header-left { display: flex; align-items: center; gap: 12px; }
    .project-title { margin: 0; font-size: 24px; }
    .status-badge { padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; text-transform: uppercase; }
    .status-badge[data-status="draft"] { background: #e0e0e0; color: #616161; }
    .status-badge[data-status="mapped"] { background: #fff3e0; color: #e65100; }
    .status-badge[data-status="transformed"] { background: #e3f2fd; color: #1565c0; }
    .status-badge[data-status="published"] { background: #e8f5e9; color: #2e7d32; }
    .tab-content { padding: 24px 0; min-height: 400px; }
    .tab-placeholder { color: #999; text-align: center; padding: 80px 0; }
    .loading-container, .error-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; }
    .error-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #f44336; margin-bottom: 16px; }
  `]
})
export class CubeProject implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cubeService = inject(CubeService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  cube = signal<Cube | null>(null);
  loading = signal(true);
  activeTab = signal<CubeTab>('mapping');

  private readonly tabMap: CubeTab[] = ['mapping', 'transform', 'designer', 'publish'];
  tabIndex = computed(() => this.tabMap.indexOf(this.activeTab()));

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.loadCube(id);
    } else {
      // New cube — create it first
      this.createNewCube();
    }
  }

  private loadCube(id: string) {
    this.loading.set(true);
    this.cubeService.get(id).subscribe({
      next: (cube) => {
        this.cube.set(cube);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load cube', 'Close', { duration: 3000 });
      }
    });
  }

  private createNewCube() {
    // Open a MatDialog for name + description (spec requires dialog, not prompt)
    import('../shared/cube-metadata-dialog').then(m => {
      const ref = this.dialog.open(m.CubeMetadataDialog, {
        width: '480px',
        data: { mode: 'create' }
      });
      ref.afterClosed().subscribe(result => {
        if (!result) {
          this.router.navigate(['/cubes']);
          return;
        }
        const uri = 'https://example.org/cube/' + result.name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
        this.cubeService.create({ uri, name: result.name, description: result.description }).subscribe({
          next: (cube) => {
            this.cube.set(cube);
            this.loading.set(false);
            this.router.navigate(['/cubes', cube.id], { replaceUrl: true });
          },
          error: () => {
            this.loading.set(false);
            this.snackBar.open('Failed to create cube', 'Close', { duration: 3000 });
            this.router.navigate(['/cubes']);
          }
        });
      });
    });
  }

  onTabChange(index: number) {
    this.activeTab.set(this.tabMap[index]);
  }

  viewPipeline() {
    const cube = this.cube();
    if (cube?.pipelineId) {
      this.router.navigate(['/pipelines', cube.pipelineId]);
    }
  }

  goBack() {
    this.router.navigate(['/cubes']);
  }

  /** Reload cube data — called by child tabs after mutations */
  refreshCube() {
    const cube = this.cube();
    if (cube) {
      this.cubeService.get(cube.id).subscribe(updated => this.cube.set(updated));
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-project.ts
git commit -m "feat(cube): create CubeProject shell with 4-tab layout"
```

### Task 2.5: Update Routes

**Files:**
- Modify: `rdf-forge/rdf-forge-ui/src/app/app.routes.ts`

- [ ] **Step 1: Replace the cubes route block**

Replace lines 57-61:

```typescript
{
  path: 'cubes',
  loadComponent: () => import('./features/cube/cube-wizard/cube-wizard').then(m => m.CubeWizard),
  canActivate: [authGuard]
},
```

With:

```typescript
{
  path: 'cubes',
  canActivate: [authGuard],
  children: [
    {
      path: '',
      loadComponent: () => import('./features/cube/cube-list/cube-list').then(m => m.CubeList)
    },
    {
      path: 'new',
      loadComponent: () => import('./features/cube/cube-project/cube-project').then(m => m.CubeProject)
    },
    {
      path: ':id',
      loadComponent: () => import('./features/cube/cube-project/cube-project').then(m => m.CubeProject)
    }
  ]
},
```

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/app.routes.ts
git commit -m "feat(cube): update routes to list/detail pattern with 4-tab project view"
```

---

## Chunk 3: Frontend Tab Components

### Task 3.1: CSV Mapping Tab

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/csv-mapping-tab/csv-source-panel.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/csv-mapping-tab/column-mapping-editor.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/csv-mapping-tab/output-table-panel.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/csv-mapping-tab/csv-mapping-tab.ts`

- [ ] **Step 1: Create CsvSourcePanel**

Left panel component showing uploaded CSV file info and column list. Accepts CSV data via `input()`, emits column selection events via `output()`. Shows file name, row count, column count, sample values per column, and checkboxes for each column.

Key signals: `csvData: InputSignal<CsvPreview | null>`, `selectedColumns: signal<string[]>`.

Template: file info card at top, then a list of column cards each with checkbox, column name, 3 sample values, and a colored dot indicating mapping status. Include a "Create table from selected columns" button at the bottom that emits the selected columns for mapping.

- [ ] **Step 2: Create ColumnMappingEditor**

Side-panel component for editing a single column mapping. Accepts the mapping via `input()`, emits save/cancel via `output()`.

Fields: Property URI (auto-generated), Role selector (Key Dimension / Measure / Attribute / Ignore), Datatype selector (xsd:string, xsd:integer, xsd:decimal, xsd:date, xsd:gYear), Scale type (Nominal / Ordinal / Ratio / Interval), "Link to shared dimension" button, Key dimension toggle, Unit URI + label (for measures).

Uses Angular Material form fields. Emits `ColumnMapping` on save.

- [ ] **Step 3: Create OutputTablePanel**

Right panel showing RDF output tables. Each table card shows: table type badge, list of column mappings with property name, datatype, role badge, edit/delete buttons.

Accepts `columnMappings: InputSignal<ColumnMapping[]>`, emits `editMapping` and `deleteMapping` events. Include a "View generated CSVW" expandable section at the bottom of each table showing the Turtle serialization of the column mapping configuration.

- [ ] **Step 4: Create CsvMappingTab (parent)**

Two-panel layout parent. Left: CsvSourcePanel. Right: OutputTablePanel. Below or overlay: ColumnMappingEditor.

Handles CSV upload via DataService, saves column mappings via `CubeService.update()`.

Key signals: `cube: InputSignal<Cube>`, `editingMapping: signal<ColumnMapping | null>`.

On column mapping save: `PUT /api/v1/cubes/:id` with updated `metadata.columnMappings`.

- [ ] **Step 5: Wire CsvMappingTab into CubeProject**

Import CsvMappingTab and replace the placeholder in the first mat-tab:

```html
<app-csv-mapping-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
```

- [ ] **Step 6: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/csv-mapping-tab/
git commit -m "feat(cube): implement CSV Mapping tab with two-panel layout"
```

### Task 3.2: Transform Tab

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/transform-tab/mini-pipeline-preview.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/transform-tab/transform-tab.ts`

- [ ] **Step 1: Create MiniPipelinePreview**

Small vertical list of pipeline steps with colored left borders. Accepts `pipeline: InputSignal<Pipeline | null>`.

Shows step name, operation type, status indicator. "Open in Pipeline Designer" link at bottom.

220px wide, fixed to the right side of the Transform tab.

- [ ] **Step 2: Create TransformTab**

Main transform tab with "Run Transform" button, job history list, and mini pipeline sidebar.

Key signals: `cube: InputSignal<Cube>`, `jobs: signal<Job[]>`, `currentJob: signal<Job | null>`, `running: signal<boolean>`.

**"Run Transform" logic:**
1. Check if pipeline exists (`cube.pipelineId`). If not, call `POST /api/v1/cubes/:id/generate-pipeline` first.
2. Check drift: compare `cube.mappingsVersion` vs `cube.metadata.lastGeneratedMappingsVersion`. If different, show warning and regenerate pipeline.
3. Call `JobService.create(pipelineId)` to start a job.
4. Poll `JobService.get(jobId)` every 3 seconds until terminal state.
5. On success, refresh cube data to pick up observation count.

Job history: list of past jobs for this pipeline, showing version, status badge, timestamp, "View log" link.

- [ ] **Step 3: Wire TransformTab into CubeProject**

Import TransformTab and replace the placeholder in the second mat-tab:

```html
<app-transform-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
```

- [ ] **Step 4: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/transform-tab/
git commit -m "feat(cube): implement Transform tab with pipeline generation and job monitoring"
```

### Task 3.3: Cube Designer Tab

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-designer-tab/dimension-card.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-designer-tab/dimension-edit-panel.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-designer-tab/observation-preview.ts`
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-designer-tab/cube-designer-tab.ts`

- [ ] **Step 1: Create DimensionCard**

Small card showing one dimension/measure. Accepts `mapping: InputSignal<ColumnMapping>`. Shows: name, role badge, scale type icon, datatype, shared dimension link status. "Edit" button emits event.

- [ ] **Step 2: Create DimensionEditPanel**

Side panel for editing dimension metadata: name, description, scale type, data kind, unit, link to shared dimension. Uses Angular Material form fields. Emits updated `ColumnMapping` on save.

- [ ] **Step 3: Create ObservationPreview**

Paginated table of observations. Accepts `cubeId: InputSignal<string>`. Fetches data via `CubeService.getObservations()`.

Mat-table with: dynamic columns from `ObservationColumn[]`, key dimensions bold, measures right-aligned. Paginator: prev/next, page size (10/20/50/100), total count.

Empty state: "No observations yet. Run a transformation first."

- [ ] **Step 4: Create CubeDesignerTab (parent)**

Layout: metadata bar (top), dimension cards row, observation preview table.

"Edit Metadata" button opens CubeMetadataDialog (Task 4.2).

Dimension cards row: horizontal scrollable row of DimensionCard components, one per non-ignored column mapping.

- [ ] **Step 5: Wire CubeDesignerTab into CubeProject**

```html
<app-cube-designer-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
```

- [ ] **Step 6: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-designer-tab/
git commit -m "feat(cube): implement Cube Designer tab with dimension cards and observation preview"
```

### Task 3.4: Publish Tab

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/publish-tab/publish-tab.ts`

- [ ] **Step 1: Create PublishTab**

Three action cards layout:

1. **Publish to Triplestore** — runs the pipeline (or just graph-store-put step). Button disabled if no pipeline or no observations. Shows last published date.
2. **Download RDF** — format dropdown (Turtle, N-Triples, JSON-LD, TriG) + download button. Calls `CubeService.exportCube()` and triggers browser download.
3. **Unlist Cube** — red outlined card with confirmation dialog. Calls `CubeService.unlistCube()`.

Publication history: list of past publish jobs (filtered from job history).

Key signals: `cube: InputSignal<Cube>`, `publishing: signal<boolean>`, `downloading: signal<boolean>`.

- [ ] **Step 2: Wire PublishTab into CubeProject**

```html
<app-publish-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
```

- [ ] **Step 3: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/publish-tab/
git commit -m "feat(cube): implement Publish tab with publish, download, and unlist actions"
```

---

## Chunk 4: Shared Components, Integration, Cleanup

### Task 4.1: Shared Dimension Search Dialog

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/shared/shared-dimension-search.ts`

- [ ] **Step 1: Create SharedDimensionSearch dialog**

Mat-dialog that searches available shared dimensions via DimensionService. Shows search input + results list. Each result shows dimension name, description, value count. "Link" button returns the selected dimension URI.

Used by ColumnMappingEditor and DimensionEditPanel when user clicks "Link to shared dimension".

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/shared/shared-dimension-search.ts
git commit -m "feat(cube): add shared dimension search dialog"
```

### Task 4.2: Cube Metadata Dialog

**Files:**
- Create: `rdf-forge/rdf-forge-ui/src/app/features/cube/shared/cube-metadata-dialog.ts`

- [ ] **Step 1: Create CubeMetadataDialog**

Mat-dialog for editing cube-level metadata: title, description, publisher URI, dcat:theme, contact point. Uses reactive forms. Saves via `CubeService.update()`.

- [ ] **Step 2: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/shared/cube-metadata-dialog.ts
git commit -m "feat(cube): add cube metadata edit dialog"
```

### Task 4.3: Wire All Tab Components into CubeProject Shell

**Files:**
- Modify: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-project.ts`

- [ ] **Step 1: Import all 4 tab components**

Add imports for CsvMappingTab, TransformTab, CubeDesignerTab, PublishTab. Add them to the `imports` array.

- [ ] **Step 2: Replace placeholder content in all 4 tabs**

```html
<mat-tab label="CSV Mapping">
  <ng-template matTabContent>
    <app-csv-mapping-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
  </ng-template>
</mat-tab>
<mat-tab label="Transform">
  <ng-template matTabContent>
    <app-transform-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
  </ng-template>
</mat-tab>
<mat-tab label="Cube Designer">
  <ng-template matTabContent>
    <app-cube-designer-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
  </ng-template>
</mat-tab>
<mat-tab label="Publish">
  <ng-template matTabContent>
    <app-publish-tab [cube]="cube()!" (cubeUpdated)="refreshCube()" />
  </ng-template>
</mat-tab>
```

- [ ] **Step 3: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/cube-project/cube-project.ts
git commit -m "feat(cube): wire all 4 tab components into CubeProject shell"
```

### Task 4.4: Delete Old Wizard Components + Update Barrel File

**Files:**
- Delete: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-wizard/cube-wizard.ts`
- Delete: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-wizard/cube-wizard.spec.ts`
- Delete: `rdf-forge/rdf-forge-ui/src/app/features/cube/cube-definition-editor/cube-definition-editor.ts`
- Modify: `rdf-forge/rdf-forge-ui/src/app/features/cube/index.ts`

- [ ] **Step 1: Update barrel file**

Replace the contents of `features/cube/index.ts` with new exports:

```typescript
// Cube feature exports
export { CubeList } from './cube-list/cube-list';
export { CubeProject } from './cube-project/cube-project';
```

- [ ] **Step 2: Verify no remaining imports reference old components**

Search for `cube-wizard` and `cube-definition-editor` in all `.ts` files. After updating `index.ts` and `app.routes.ts` (Task 2.5), there should be no remaining references.

- [ ] **Step 3: Delete old files**

```bash
rm rdf-forge/rdf-forge-ui/src/app/features/cube/cube-wizard/cube-wizard.ts
rm rdf-forge/rdf-forge-ui/src/app/features/cube/cube-wizard/cube-wizard.spec.ts
rm rdf-forge/rdf-forge-ui/src/app/features/cube/cube-definition-editor/cube-definition-editor.ts
```

- [ ] **Step 4: Commit**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/index.ts
git add -A rdf-forge/rdf-forge-ui/src/app/features/cube/cube-wizard/
git add -A rdf-forge/rdf-forge-ui/src/app/features/cube/cube-definition-editor/
git commit -m "chore(cube): remove old Cube Wizard and Definition Editor, update barrel exports"
```

### Task 4.5: Smoke Test

- [ ] **Step 1: Build frontend**

```bash
cd rdf-forge/rdf-forge-ui && npm run build
```

Fix any compilation errors.

- [ ] **Step 2: Verify routes work**

Start the dev server (`ng serve`) and verify:
- `/cubes` loads CubeList
- `/cubes/new` opens create flow
- `/cubes/:id` loads CubeProject with 4 tabs
- Each tab renders without errors
- "View Pipeline" button appears when cube has a pipelineId

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add rdf-forge/rdf-forge-ui/src/app/features/cube/ rdf-forge/rdf-forge-ui/src/app/core/ rdf-forge/rdf-forge-ui/src/app/app.routes.ts
git commit -m "fix(cube): resolve build issues from Cube Creator redesign"
```
