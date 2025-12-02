import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Dimension, DimensionValue, Hierarchy, DimensionStats } from '../models';
import { environment } from '../../../environments/environment';

export interface DimensionListParams {
  projectId?: string;
  type?: string;
  search?: string;
}

export interface DimensionValueParams {
  search?: string;
  parentId?: string;
  page?: number;
  limit?: number;
}

@Injectable({
  providedIn: 'root'
})
export class DimensionService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  list(params?: DimensionListParams): Observable<Dimension[]> {
    return this.api.getArray<Dimension>('/dimensions', params as Record<string, unknown>);
  }

  get(id: string): Observable<Dimension> {
    return this.api.get<Dimension>(`/dimensions/${id}`);
  }

  create(data: Dimension): Observable<Dimension> {
    return this.api.post<Dimension>('/dimensions', data);
  }

  update(id: string, data: Partial<Dimension>): Observable<Dimension> {
    return this.api.put<Dimension>(`/dimensions/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/dimensions/${id}`);
  }

  getValues(id: string, params?: DimensionValueParams): Observable<DimensionValue[]> {
    return this.api.getArray<DimensionValue>(`/dimensions/${id}/values`, params as Record<string, unknown>);
  }

  addValue(id: string, data: DimensionValue): Observable<DimensionValue> {
    return this.api.post<DimensionValue>(`/dimensions/${id}/values`, data);
  }

  updateValue(valueId: string, data: Partial<DimensionValue>): Observable<DimensionValue> {
    return this.api.put<DimensionValue>(`/dimensions/values/${valueId}`, data);
  }

  deleteValue(valueId: string): Observable<void> {
    return this.api.delete<void>(`/dimensions/values/${valueId}`);
  }

  importCsv(id: string, file: File): Observable<{ imported: number }> {
    return this.api.upload<{ imported: number }>(`/dimensions/${id}/import/csv`, file);
  }

  exportTurtle(id: string): Observable<Blob> {
    return this.http.get(`${environment.apiBaseUrl}/dimensions/${id}/export/turtle`, {
      responseType: 'blob'
    });
  }

  getTree(id: string): Observable<DimensionValue[]> {
    return this.api.getArray<DimensionValue>(`/dimensions/${id}/tree`);
  }

  getStats(projectId: string): Observable<DimensionStats> {
    return this.api.get<DimensionStats>('/dimensions/stats', { projectId });
  }

  // Hierarchy endpoints
  listHierarchies(dimensionId: string): Observable<Hierarchy[]> {
    return this.api.getArray<Hierarchy>('/hierarchies', { dimensionId });
  }

  createHierarchy(data: Hierarchy): Observable<Hierarchy> {
    return this.api.post<Hierarchy>('/hierarchies', data);
  }

  exportSkos(id: string, dimensionId: string): Observable<Blob> {
    return this.http.get(`${environment.apiBaseUrl}/hierarchies/${id}/export/skos`, {
      params: { dimensionId },
      responseType: 'blob'
    });
  }
}
