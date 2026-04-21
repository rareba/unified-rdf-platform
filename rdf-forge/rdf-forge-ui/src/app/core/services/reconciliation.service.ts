import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  CandidateListFilter,
  ManualCandidateRequest,
  MatchCandidate,
  MatchStats,
  MatcherInfo,
  SuggestRequest,
  SuggestResponse
} from '../models/reconciliation.model';

/**
 * Client for the Phase 8 reconciliation endpoints under
 * `/api/v1/reconciliation/**`.
 */
@Injectable({ providedIn: 'root' })
export class ReconciliationService {
  private readonly api = inject(ApiService);

  suggest(request: SuggestRequest): Observable<SuggestResponse> {
    return this.api.post<SuggestResponse>(
      '/reconciliation/candidates/suggest',
      request,
      { operationType: 'sparql' }
    );
  }

  list(projectId: string, filter?: CandidateListFilter): Observable<MatchCandidate[]> {
    const params: Record<string, unknown> = { projectId };
    if (filter?.status)    params['status']    = filter.status;
    if (filter?.predicate) params['predicate'] = filter.predicate;
    if (filter?.matcher)   params['matcher']   = filter.matcher;
    if (filter?.search)    params['search']    = filter.search;
    return this.api.getArray<MatchCandidate>('/reconciliation/candidates', params);
  }

  get(id: string): Observable<MatchCandidate> {
    return this.api.get<MatchCandidate>(`/reconciliation/candidates/${id}`);
  }

  approve(id: string): Observable<MatchCandidate> {
    return this.api.post<MatchCandidate>(`/reconciliation/candidates/${id}/approve`, {});
  }

  reject(id: string): Observable<MatchCandidate> {
    return this.api.post<MatchCandidate>(`/reconciliation/candidates/${id}/reject`, {});
  }

  manual(request: ManualCandidateRequest): Observable<MatchCandidate> {
    return this.api.post<MatchCandidate>('/reconciliation/candidates/manual', request);
  }

  matchers(): Observable<MatcherInfo[]> {
    return this.api.getArray<MatcherInfo>('/reconciliation/matchers');
  }

  stats(projectId: string): Observable<MatchStats> {
    return this.api.get<MatchStats>('/reconciliation/stats', { projectId });
  }
}
