import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ApiService } from './api.service';
import {
  ProjectValidationHealth,
  ValidationIssue,
  ValidationRun,
  ValidationRunRequest,
  ValidationSeverity,
  ValidationSuite,
  ValidationSuiteCreateRequest,
  ValidationSuiteUpdateRequest
} from '../models/validation.model';

/**
 * HTTP client for the Phase 5 Validation Cockpit. Thin wrapper — the
 * cockpit component composes signals + derived state on top.
 */
@Injectable({ providedIn: 'root' })
export class ValidationService {
  private readonly api = inject(ApiService);

  listSuites(projectId: string): Observable<ValidationSuite[]> {
    return this.api.getArray<ValidationSuite>('/validation/suites', { projectId });
  }

  getSuite(id: string): Observable<ValidationSuite> {
    return this.api.get<ValidationSuite>(`/validation/suites/${id}`);
  }

  createSuite(req: ValidationSuiteCreateRequest): Observable<ValidationSuite> {
    return this.api.post<ValidationSuite>('/validation/suites', req);
  }

  updateSuite(id: string, req: ValidationSuiteUpdateRequest): Observable<ValidationSuite> {
    return this.api.put<ValidationSuite>(`/validation/suites/${id}`, req);
  }

  deleteSuite(id: string): Observable<void> {
    return this.api.delete<void>(`/validation/suites/${id}`);
  }

  runSuite(id: string, req: ValidationRunRequest): Observable<ValidationRun> {
    return this.api.post<ValidationRun>(`/validation/suites/${id}/run`, req);
  }

  validateAll(projectId: string, req: ValidationRunRequest): Observable<ValidationRun[]> {
    return this.api.post<ValidationRun[]>(
      `/validation/projects/${projectId}/validate-all`, req);
  }

  history(suiteId: string, limit = 20): Observable<ValidationRun[]> {
    return this.api.getArray<ValidationRun>('/validation/runs', { suiteId, limit });
  }

  getRun(id: string): Observable<ValidationRun> {
    return this.api.get<ValidationRun>(`/validation/runs/${id}`);
  }

  issues(runId: string, severity?: ValidationSeverity, limit = 100): Observable<ValidationIssue[]> {
    const params: Record<string, unknown> = { limit };
    if (severity) params['severity'] = severity;
    return this.api.getArray<ValidationIssue>(`/validation/runs/${runId}/issues`, params);
  }

  /**
   * Aggregate the latest-run status of every suite in a project so the
   * overview widget can render a single green/amber/red badge.
   */
  projectHealth(projectId: string): Observable<ProjectValidationHealth> {
    return this.listSuites(projectId).pipe(
      catchError(() => of([] as ValidationSuite[])),
      switchMap(suites => {
        if (!suites.length) {
          return of<ProjectValidationHealth>({
            status: 'unknown',
            suiteCount: 0,
            passedCount: 0,
            warningCount: 0,
            failedCount: 0
          });
        }
        const runs$ = suites.map(s =>
          this.history(s.id, 1).pipe(
            map(h => (h && h.length ? h[0] : null)),
            catchError(() => of<ValidationRun | null>(null))
          )
        );
        return forkJoin(runs$).pipe(
          map(latest => {
            let passed = 0, warning = 0, failed = 0;
            for (const r of latest) {
              if (!r) continue;
              if (r.status === 'FAILED' || r.status === 'ERRORED') failed++;
              else if (r.warningCount > 0) warning++;
              else if (r.status === 'PASSED') passed++;
            }
            let status: ProjectValidationHealth['status'] = 'unknown';
            if (failed > 0) status = 'red';
            else if (warning > 0) status = 'amber';
            else if (passed > 0) status = 'green';
            return {
              status,
              suiteCount: suites.length,
              passedCount: passed,
              warningCount: warning,
              failedCount: failed
            } satisfies ProjectValidationHealth;
          })
        );
      })
    );
  }
}
