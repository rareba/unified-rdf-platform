import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  Mapping,
  MappingCreateRequest,
  MappingUpdateRequest,
  MappingPreviewRequest,
  MappingPreviewResponse,
  ExplainRequest,
  ExplainResponse,
  MappingValidationRequest,
  MappingValidationResponse
} from '../models/mapping.model';

/**
 * Typed client for the Universal Mapping Studio REST API.
 *
 * <p>Mirrors the backend controller at
 * {@code /api/v1/mappings} in rdf-forge-pipeline-service. All requests are
 * project-scoped via the `projectId` query param on {@link listByProject}.
 */
@Injectable({ providedIn: 'root' })
export class MappingService {
  private readonly api = inject(ApiService);

  listByProject(projectId: string): Observable<Mapping[]> {
    return this.api.getArray<Mapping>('/mappings', { projectId });
  }

  get(id: string): Observable<Mapping> {
    return this.api.get<Mapping>(`/mappings/${id}`);
  }

  create(req: MappingCreateRequest): Observable<Mapping> {
    return this.api.post<Mapping>('/mappings', req);
  }

  update(id: string, req: MappingUpdateRequest): Observable<Mapping> {
    return this.api.put<Mapping>(`/mappings/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/mappings/${id}`);
  }

  validate(id: string, req?: MappingValidationRequest): Observable<MappingValidationResponse> {
    return this.api.post<MappingValidationResponse>(`/mappings/${id}/validate`, req ?? {});
  }

  preview(id: string, req: MappingPreviewRequest): Observable<MappingPreviewResponse> {
    return this.api.post<MappingPreviewResponse>(`/mappings/${id}/preview`, req);
  }

  explain(id: string, req: ExplainRequest): Observable<ExplainResponse> {
    return this.api.post<ExplainResponse>(`/mappings/${id}/explain`, req);
  }
}
