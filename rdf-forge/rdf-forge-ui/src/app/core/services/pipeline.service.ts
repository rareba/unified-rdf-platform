import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  Pipeline,
  PipelineCreateRequest,
  PipelineValidationResult,
  PipelineVersion,
  Operation
} from '../models';

export interface PipelineListParams {
  search?: string;
  status?: string;
  page?: number;
  limit?: number;
}

@Injectable({
  providedIn: 'root'
})
export class PipelineService {
  private readonly api = inject(ApiService);

  list(params?: PipelineListParams): Observable<Pipeline[]> {
    return this.api.get<Pipeline[]>('/pipelines', params as Record<string, unknown>);
  }

  get(id: string): Observable<Pipeline> {
    return this.api.get<Pipeline>(`/pipelines/${id}`);
  }

  create(data: PipelineCreateRequest): Observable<Pipeline> {
    return this.api.post<Pipeline>('/pipelines', data);
  }

  update(id: string, data: Partial<PipelineCreateRequest>): Observable<Pipeline> {
    return this.api.put<Pipeline>(`/pipelines/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/pipelines/${id}`);
  }

  duplicate(id: string): Observable<Pipeline> {
    return this.api.post<Pipeline>(`/pipelines/${id}/duplicate`, {});
  }

  validate(definition: string, format: 'yaml' | 'turtle'): Observable<PipelineValidationResult> {
    return this.api.post<PipelineValidationResult>('/pipelines/validate', { definition, format });
  }

  run(id: string, variables?: Record<string, unknown>): Observable<{ jobId: string }> {
    return this.api.post<{ jobId: string }>(`/pipelines/${id}/run`, { variables });
  }

  getVersions(id: string): Observable<PipelineVersion[]> {
    return this.api.get<PipelineVersion[]>(`/pipelines/${id}/versions`);
  }

  getVersion(id: string, version: number): Observable<Pipeline> {
    return this.api.get<Pipeline>(`/pipelines/${id}/versions/${version}`);
  }

  getOperations(): Observable<Operation[]> {
    return this.api.get<Operation[]>('/operations');
  }

  getOperation(id: string): Observable<Operation> {
    return this.api.get<Operation>(`/operations/${id}`);
  }
}
