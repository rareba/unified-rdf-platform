# RDF Forge Testing and Bug Fixes Report

**Date:** January 9, 2026
**Version:** Post-initial deployment testing

## Executive Summary

Comprehensive testing was performed on the RDF Forge application to verify all features work correctly. Four bugs were identified and fixed, all verified working after the fixes.

## Testing Coverage

### Features Tested

| Feature | Status | Notes |
|---------|--------|-------|
| Dashboard | PASS | All widgets and metrics display correctly |
| Pipelines | PASS | List, create, edit, run, delete operations |
| SHACL Studio | PASS | Create, edit, validate shapes |
| Cubes Wizard | PASS | Step-by-step cube creation |
| Jobs | PASS | List, view logs, status tracking |
| Data Sources | PASS | List, preview, upload |
| Dimensions | PASS | List and browse |
| Triplestore | PASS | SPARQL query execution |
| Settings | PASS | Configuration pages |
| Admin | PASS | Users, Roles, API Tokens, Git Sync, System |

## Bugs Identified and Fixed

### BUG #1: Dashboard Completed Jobs Count Shows 0

**Symptom:** Dashboard displayed "0 Completed Jobs" even when jobs had completed successfully.

**Root Cause:** Case sensitivity mismatch between backend and frontend. Backend returns uppercase status values (`COMPLETED`, `FAILED`, `RUNNING`), but frontend was comparing against lowercase values (`completed`, `failed`, `running`).

**Fix Applied:**
File: `rdf-forge-ui/src/app/features/dashboard/dashboard.ts`

```typescript
// Line 114 - Changed from:
completedJobs: jobs.filter(j => j.status === 'completed').length,
// To:
completedJobs: jobs.filter(j => j.status?.toLowerCase() === 'completed').length,

// Also fixed getStatusColor function (lines 162-171) to use toLowerCase()
```

File: `rdf-forge-ui/src/app/features/job/job-list/job-list.ts`

```typescript
// Multiple status comparisons fixed to use toLowerCase()
// Lines 95, 107, 108, 111, 115, 285-286
```

---

### BUG #2: Pipeline Last Run Shows "Never"

**Symptom:** All pipelines displayed "Never" for Last Run even after jobs had completed.

**Root Cause:** The Pipeline entity doesn't have a persistent `lastRun` field. The value needs to be computed from completed job data.

**Fix Applied:**
File: `rdf-forge-ui/src/app/features/pipeline/pipeline-list/pipeline-list.ts`

```typescript
// Added imports
import { JobService } from '../../../core/services';
import { Job } from '../../../core/models';
import { forkJoin, catchError, of } from 'rxjs';

// Added injection
private readonly jobService = inject(JobService);

// Modified loadPipelines() to fetch jobs and compute lastRun
loadPipelines(): void {
  this.loading.set(true);
  forkJoin({
    pipelines: this.pipelineService.list().pipe(catchError(() => of([]))),
    jobs: this.jobService.list().pipe(catchError(() => of([])))
  }).subscribe({
    next: ({ pipelines, jobs }) => {
      // Compute lastRun from job completedAt dates
      const lastRunMap = new Map<string, Date>();
      jobs.forEach((job: Job) => {
        if (job.completedAt && (job.status?.toLowerCase() === 'completed' || job.status?.toLowerCase() === 'failed')) {
          const completedAt = new Date(job.completedAt);
          const existing = lastRunMap.get(job.pipelineId);
          if (!existing || completedAt > existing) {
            lastRunMap.set(job.pipelineId, completedAt);
          }
        }
      });
      // Enrich pipelines with computed lastRun
      const enrichedPipelines = pipelines.map(p => ({
        ...p,
        lastRun: lastRunMap.get(p.id) || p.lastRun
      }));
      this.pipelines.set(enrichedPipelines);
      this.loading.set(false);
    },
    error: () => { /* error handling */ }
  });
}
```

---

### BUG #3: Run Pipeline Fails with 404

**Symptom:** Clicking "Run Pipeline" on Pipelines page returned 404 Not Found error.

**Root Cause:** Frontend was calling `POST /api/v1/pipelines/{id}/run` which doesn't exist in the backend. The backend uses the Jobs API to create and run pipeline jobs.

**Fix Applied:**
File: `rdf-forge-ui/src/app/core/services/pipeline.service.ts`

```typescript
// Changed run() method from:
run(id: string, variables?: Record<string, unknown>): Observable<{ jobId: string; id: string }> {
  return this.api.post<{ jobId: string; id: string }>(`/pipelines/${id}/run`, { variables });
}

// To:
run(id: string, variables?: Record<string, unknown>): Observable<{ jobId: string; id: string }> {
  return this.api.post<{ id: string }>('/jobs', {
    pipelineId: id,
    variables: variables || {}
  }).pipe(
    map(job => ({ jobId: job.id, id: job.id }))
  );
}
```

---

### BUG #4: Data Preview Returns 500 Error

**Symptom:** Clicking preview button on Data Sources page returned 500 Internal Server Error.

**Root Cause:** The `storage_path` values in demo-data.sql included the bucket name prefix (`rdf-forge-data/demo/...`), but the MinIO storage provider expects paths relative to the bucket root (`demo/...`).

**Fix Applied:**
File: `rdf-forge/docker/demo-data.sql`

```sql
-- Changed storage_path values from:
'rdf-forge-data/demo/swiss-population-by-canton.csv'
'rdf-forge-data/demo/swiss-employment-statistics.csv'
'rdf-forge-data/demo/swiss-gdp-by-sector.csv'

-- To:
'demo/swiss-population-by-canton.csv'
'demo/swiss-employment-statistics.csv'
'demo/swiss-gdp-by-sector.csv'
```

---

## Verification Results

All fixes were verified after rebuilding the UI container and restarting Docker services:

| Bug | Verification | Result |
|-----|--------------|--------|
| #1 | Dashboard shows "3 Completed Jobs" | PASS |
| #2 | Pipelines show actual Last Run dates | PASS |
| #3 | Run Pipeline creates job successfully (Job 758ab620) | PASS |
| #4 | Data preview shows Swiss Population data (26 rows) | PASS |

## Technical Notes

### Backend Status Enum Values
The backend `JobStatus` enum uses uppercase values:
- `COMPLETED`
- `FAILED`
- `PENDING`
- `RUNNING`
- `CANCELLED`

Frontend code must use `.toLowerCase()` when comparing status values for consistent behavior.

### API Endpoints
- Jobs are created via `POST /api/v1/jobs` with `pipelineId` in the body
- There is no `/api/v1/pipelines/{id}/run` endpoint
- Data preview uses `/api/v1/data/{id}/preview` which reads from MinIO storage

### Storage Path Convention
MinIO storage paths should be relative to the bucket root, not include the bucket name as a prefix.

## Deployment Steps Performed

1. Fixed all four bugs in source code
2. Rebuilt UI: `npm run build --legacy-peer-deps`
3. Rebuilt Docker image: `docker compose build rdf-forge-ui`
4. Restarted services with fresh volumes: `docker compose down -v && docker compose up -d`
5. Verified all fixes in browser

## Conclusion

The RDF Forge application is now in a presentable state with all identified bugs fixed and verified. All major features have been tested and are functioning correctly.
