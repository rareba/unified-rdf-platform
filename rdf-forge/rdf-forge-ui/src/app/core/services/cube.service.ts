import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Cube, CubeCreateRequest } from '../models/cube.model';

export interface CubeListParams {
  projectId?: string;
  search?: string;
  page?: number;
  size?: number;
}

export interface GeneratedArtifact {
  id: string;
  name: string;
  type: 'SHACL_SHAPE' | 'PIPELINE';
}

export interface GenerateShapeRequest {
  name?: string;
  targetClass?: string;
}

export interface GeneratePipelineRequest {
  name?: string;
  triplestoreId?: string;
  graphUri?: string;
}

@Injectable({
  providedIn: 'root'
})
export class CubeService {
  private readonly api = inject(ApiService);

  list(params?: CubeListParams): Observable<Cube[]> {
    return this.api.getArray<Cube>('/cubes', params as Record<string, unknown>);
  }

  get(id: string): Observable<Cube> {
    return this.api.get<Cube>(`/cubes/${id}`);
  }

  create(data: CubeCreateRequest): Observable<Cube> {
    return this.api.post<Cube>('/cubes', data);
  }

  update(id: string, data: Partial<Cube>): Observable<Cube> {
    return this.api.put<Cube>(`/cubes/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/cubes/${id}`);
  }

  publish(id: string): Observable<Cube> {
    return this.api.post<Cube>(`/cubes/${id}/publish`, {});
  }

  // ===== New methods for cube definition architecture =====

  /**
   * Generate a SHACL shape from the cube definition's column mappings.
   */
  generateShape(cubeId: string, request?: GenerateShapeRequest): Observable<GeneratedArtifact> {
    return this.api.post<GeneratedArtifact>(`/cubes/${cubeId}/generate-shape`, request || {});
  }

  /**
   * Generate a draft ETL pipeline from the cube definition.
   */
  generatePipeline(cubeId: string, request?: GeneratePipelineRequest): Observable<GeneratedArtifact> {
    return this.api.post<GeneratedArtifact>(`/cubes/${cubeId}/generate-pipeline`, request || {});
  }

  /**
   * Link an existing SHACL shape to the cube.
   */
  linkShape(cubeId: string, shapeId: string): Observable<Cube> {
    return this.api.put<Cube>(`/cubes/${cubeId}/shape/${shapeId}`, {});
  }

  /**
   * Link an existing pipeline to the cube.
   */
  linkPipeline(cubeId: string, pipelineId: string): Observable<Cube> {
    return this.api.put<Cube>(`/cubes/${cubeId}/pipeline/${pipelineId}`, {});
  }

  /**
   * Remove the shape link from the cube.
   */
  unlinkShape(cubeId: string): Observable<Cube> {
    return this.api.delete<Cube>(`/cubes/${cubeId}/shape`);
  }

  /**
   * Remove the pipeline link from the cube.
   */
  unlinkPipeline(cubeId: string): Observable<Cube> {
    return this.api.delete<Cube>(`/cubes/${cubeId}/pipeline`);
  }
}

