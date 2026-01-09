import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
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
    return this.api.getArray<Pipeline>('/pipelines', params as Record<string, unknown>).pipe(
      map(pipelines => pipelines.map(p => this.enrichPipeline(p)))
    );
  }

  get(id: string): Observable<Pipeline> {
    return this.api.get<Pipeline>(`/pipelines/${id}`).pipe(
      map(p => this.enrichPipeline(p))
    );
  }

  /**
   * Enriches a pipeline with calculated fields that may not come from the API
   */
  private enrichPipeline(pipeline: Pipeline): Pipeline {
    return {
      ...pipeline,
      // Calculate stepsCount from definition if not provided
      stepsCount: pipeline.stepsCount ?? this.countSteps(pipeline.definition),
      // Default status to 'active' if not provided
      status: pipeline.status ?? 'active',
      // Ensure tags is always an array
      tags: pipeline.tags ?? [],
      // Ensure description is a string
      description: pipeline.description ?? ''
    };
  }

  /**
   * Parse the pipeline definition and count the number of steps
   */
  private countSteps(definition: string): number {
    if (!definition) return 0;
    try {
      const parsed = JSON.parse(definition);
      if (Array.isArray(parsed.steps)) {
        return parsed.steps.length;
      }
      // Handle YAML-like structure where steps might be at top level
      if (Array.isArray(parsed)) {
        return parsed.length;
      }
    } catch {
      // If parsing fails, try to count "operation" occurrences as a fallback
      return (definition.match(/"operation"/g) || []).length;
    }
    return 0;
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

  validate(definition: string, format: 'yaml' | 'turtle' | 'json'): Observable<PipelineValidationResult> {
    return this.api.post<PipelineValidationResult>('/pipelines/validate', { definition, format });
  }

  run(id: string, variables?: Record<string, unknown>): Observable<{ jobId: string; id: string }> {
    // Create a job through the jobs API to run the pipeline
    return this.api.post<{ id: string }>('/jobs', {
      pipelineId: id,
      variables: variables || {}
    }).pipe(
      map(job => ({ jobId: job.id, id: job.id }))
    );
  }

  getVersions(id: string): Observable<PipelineVersion[]> {
    return this.api.getArray<PipelineVersion>(`/pipelines/${id}/versions`);
  }

  getVersion(id: string, version: number): Observable<Pipeline> {
    return this.api.get<Pipeline>(`/pipelines/${id}/versions/${version}`);
  }

  // Operations are fetched from the /operations endpoint
  getOperations(): Observable<Operation[]> {
    return this.api.get<Operation[]>('/operations');
  }

  getOperation(id: string): Observable<Operation> {
    return this.api.get<Operation>(`/operations/${id}`);
  }
}
