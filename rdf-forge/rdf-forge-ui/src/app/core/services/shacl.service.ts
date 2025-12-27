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

/**
 * Validation profile info (cube-link profiles like standalone, visualize, opendataswiss)
 */
export interface ValidationProfile {
  id: string;
  name: string;
  description: string;
}

/**
 * Profile validation request
 */
export interface ProfileValidationRequest {
  profile: string;
  dataContent: string;
  dataFormat?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ShaclService {
  private readonly api = inject(ApiService);

  list(params?: ShapeListParams): Observable<Shape[]> {
    return this.api.getArray<Shape>('/shapes', params as Record<string, unknown>);
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
    return this.api.getArray<ShapeVersion>(`/shapes/${id}/versions`);
  }

  getTemplates(): Observable<Shape[]> {
    return this.api.getArray<Shape>('/templates/shapes');
  }

  // ===== Validation Profiles (cube-link) =====

  /**
   * Get available validation profiles (standalone, visualize, opendataswiss)
   */
  getProfiles(): Observable<ValidationProfile[]> {
    return this.api.getArray<ValidationProfile>('/shapes/profiles');
  }

  /**
   * Validate data against a specific cube-link profile
   */
  validateAgainstProfile(request: ProfileValidationRequest): Observable<ValidationResult> {
    return this.api.post<ValidationResult>('/shapes/validate-profile', request);
  }

  /**
   * Validate data against all available profiles
   */
  validateAgainstAllProfiles(dataContent: string, dataFormat?: string): Observable<Record<string, ValidationResult>> {
    return this.api.post<Record<string, ValidationResult>>('/shapes/validate-all-profiles', { dataContent, dataFormat });
  }
}

