import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { environment } from '../../../environments/environment';
import {
  Release,
  ReleaseBuildResponse,
  ReleaseCreateRequest
} from '../models/release.model';

/**
 * Typed client for the Release Factory REST API.
 *
 * <p>Mirrors the backend controller at {@code /api/v1/releases} in
 * rdf-forge-pipeline-service. All list / create requests are scoped by a
 * {@code projectId} query param matching the backend contract.
 */
@Injectable({ providedIn: 'root' })
export class ReleaseService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  listByProject(projectId: string): Observable<Release[]> {
    return this.api.getArray<Release>('/releases', { projectId });
  }

  get(id: string): Observable<Release> {
    return this.api.get<Release>(`/releases/${id}`);
  }

  getManifest(id: string): Observable<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>(`/releases/${id}/manifest`);
  }

  create(projectId: string, req: ReleaseCreateRequest): Observable<Release> {
    return this.api.post<Release>(`/releases?projectId=${encodeURIComponent(projectId)}`, req);
  }

  build(id: string): Observable<ReleaseBuildResponse> {
    return this.api.post<ReleaseBuildResponse>(`/releases/${id}/build`, {});
  }

  archive(id: string): Observable<Release> {
    return this.api.post<Release>(`/releases/${id}/archive`, {});
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/releases/${id}`);
  }

  /** Stream the zip bundle directly — bypasses the JSON-oriented ApiService. */
  download(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/releases/${id}/download`, {
      responseType: 'blob'
    });
  }
}
