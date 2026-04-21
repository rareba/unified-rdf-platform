import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  SavedQuery,
  SavedQueryCreateRequest,
  SavedQueryRunRequest,
  SavedQueryRunResponse,
  SavedQueryUpdateRequest
} from '../models/saved-query.model';

/**
 * Client for the Phase 7 SPARQL Workbench endpoints exposed under
 * `/api/v1/sparql/queries` and `/api/v1/sparql/run`.
 */
@Injectable({ providedIn: 'root' })
export class SavedQueryService {
  private readonly api = inject(ApiService);

  list(projectId: string, tags?: string[]): Observable<SavedQuery[]> {
    const params: Record<string, unknown> = { projectId };
    if (tags && tags.length > 0) {
      params['tags'] = tags.join(',');
    }
    return this.api.getArray<SavedQuery>('/sparql/queries', params);
  }

  get(id: string): Observable<SavedQuery> {
    return this.api.get<SavedQuery>(`/sparql/queries/${id}`);
  }

  create(request: SavedQueryCreateRequest): Observable<SavedQuery> {
    return this.api.post<SavedQuery>('/sparql/queries', request);
  }

  update(id: string, request: SavedQueryUpdateRequest): Observable<SavedQuery> {
    return this.api.put<SavedQuery>(`/sparql/queries/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/sparql/queries/${id}`);
  }

  run(id: string, request: SavedQueryRunRequest): Observable<SavedQueryRunResponse> {
    return this.api.post<SavedQueryRunResponse>(
      `/sparql/queries/${id}/run`,
      request,
      { operationType: 'sparql' }
    );
  }

  runInline(request: SavedQueryRunRequest): Observable<SavedQueryRunResponse> {
    return this.api.post<SavedQueryRunResponse>(
      '/sparql/run',
      request,
      { operationType: 'sparql' }
    );
  }
}
