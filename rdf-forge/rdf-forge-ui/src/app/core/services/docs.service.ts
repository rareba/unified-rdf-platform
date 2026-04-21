import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ApiDocFormat, SemanticApiDoc } from '../models/docs.model';
import { environment } from '../../../environments/environment';

/**
 * Client for the {@code /api/v1/docs/project/{id}} endpoints hosted in
 * shacl-service. The JSON variant returns a typed {@link SemanticApiDoc};
 * the HTML variant returns a raw string that can be set via [innerHTML].
 */
@Injectable({ providedIn: 'root' })
export class DocsService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  /** Fetch the typed model — the structured API doc. */
  getModel(projectId: string): Observable<SemanticApiDoc> {
    return this.api.get<SemanticApiDoc>(`/docs/project/${projectId}/model`);
  }

  /** Fetch an HTML string ready to bind via [innerHTML]. */
  getHtml(projectId: string): Observable<string> {
    return this.http.get(
      `${environment.apiBaseUrl}/docs/project/${projectId}?format=HTML`,
      { responseType: 'text', withCredentials: true }
    );
  }

  /** Generic fetch selector matching backend ApiDocFormat. */
  get(projectId: string, format: ApiDocFormat = 'JSON'): Observable<string> {
    return this.http.get(
      `${environment.apiBaseUrl}/docs/project/${projectId}?format=${format}`,
      { responseType: 'text', withCredentials: true }
    );
  }
}
