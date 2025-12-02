import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  Shape,
  ShapeCreateRequest,
  ShapeVersion,
  ValidationResult,
  PropertyShape,
  ContentFormat
} from '../models';

export interface ShapeListParams {
  search?: string;
  category?: string;
  isTemplate?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ShaclService {
  private readonly api = inject(ApiService);

  list(params?: ShapeListParams): Observable<Shape[]> {
    return this.api.get<Shape[]>('/shapes', params as Record<string, unknown>);
  }

  get(id: string): Observable<Shape> {
    return this.api.get<Shape>(`/shapes/${id}`);
  }

  create(data: ShapeCreateRequest): Observable<Shape> {
    return this.api.post<Shape>('/shapes', data);
  }

  update(id: string, data: Partial<ShapeCreateRequest>): Observable<Shape> {
    return this.api.put<Shape>(`/shapes/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/shapes/${id}`);
  }

  validateSyntax(content: string, format: ContentFormat): Observable<{ valid: boolean; errors: string[] }> {
    return this.api.post<{ valid: boolean; errors: string[] }>('/shapes/validate-syntax', { content, format });
  }

  runValidation(shapeId: string, data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples'): Observable<ValidationResult> {
    return this.api.post<ValidationResult>('/validation/run', { shapeId, data, dataFormat });
  }

  runValidationOnGraph(shapeId: string, triplestoreId: string, graphUri: string): Observable<ValidationResult> {
    return this.api.post<ValidationResult>('/validation/run', { shapeId, triplestoreId, graphUri });
  }

  inferShape(data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples', options?: { targetClass?: string }): Observable<Shape> {
    return this.api.post<Shape>('/shapes/infer', { data, dataFormat, ...options });
  }

  generateTurtle(nodeShape: { uri: string; targetClass: string; properties: PropertyShape[] }): Observable<string> {
    return this.api.post<string>('/shapes/generate', nodeShape);
  }

  getVersions(id: string): Observable<ShapeVersion[]> {
    return this.api.get<ShapeVersion[]>(`/shapes/${id}/versions`);
  }

  getTemplates(): Observable<Shape[]> {
    return this.api.get<Shape[]>('/templates/shapes');
  }
}
