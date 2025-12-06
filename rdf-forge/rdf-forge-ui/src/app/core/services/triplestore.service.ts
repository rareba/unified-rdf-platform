import { Injectable, inject, computed } from '@angular/core';
import { Observable, of, switchMap } from 'rxjs';
import { ApiService } from './api.service';
import { SettingsService } from './settings.service';
import {
  TriplestoreConnection,
  ConnectionCreateRequest,
  ConnectionTestResult,
  Graph,
  Resource,
  QueryResult,
  RdfFormat
} from '../models';

@Injectable({
  providedIn: 'root'
})
export class TriplestoreService {
  private readonly api = inject(ApiService);
  private readonly settingsService = inject(SettingsService);

  /**
   * Get the default triplestore ID from settings
   */
  readonly defaultTriplestoreId = computed(() => this.settingsService.defaultTriplestoreId());

  /**
   * Get the SPARQL result limit from settings
   */
  readonly resultLimit = computed(() => this.settingsService.sparqlResultLimit());

  list(): Observable<TriplestoreConnection[]> {
    return this.api.getArray<TriplestoreConnection>('/triplestores');
  }

  get(id: string): Observable<TriplestoreConnection> {
    return this.api.get<TriplestoreConnection>(`/triplestores/${id}`);
  }

  /**
   * Get the default triplestore connection based on settings
   */
  getDefault(): Observable<TriplestoreConnection | null> {
    const defaultId = this.defaultTriplestoreId();
    if (!defaultId) {
      return of(null);
    }
    return this.get(defaultId);
  }

  create(data: ConnectionCreateRequest): Observable<TriplestoreConnection> {
    return this.api.post<TriplestoreConnection>('/triplestores', data);
  }

  update(id: string, data: Partial<ConnectionCreateRequest>): Observable<TriplestoreConnection> {
    return this.api.put<TriplestoreConnection>(`/triplestores/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/triplestores/${id}`);
  }

  test(id: string): Observable<ConnectionTestResult> {
    return this.api.get<ConnectionTestResult>(`/triplestores/${id}/health`);
  }

  connect(id: string): Observable<void> {
    return this.api.post<void>(`/triplestores/${id}/connect`, {});
  }

  getGraphs(connectionId: string): Observable<Graph[]> {
    return this.api.getArray<Graph>(`/triplestores/${connectionId}/graphs`);
  }

  getGraphResources(connectionId: string, graphUri: string, params?: { limit?: number; offset?: number }): Observable<Resource[]> {
    // Apply default result limit if not specified
    const effectiveParams = {
      ...params,
      limit: params?.limit ?? this.resultLimit()
    };
    return this.api.getArray<Resource>(
      `/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources`,
      effectiveParams
    );
  }

  searchResources(connectionId: string, graphUri: string, query: string): Observable<Resource[]> {
    return this.api.getArray<Resource>(
      `/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/search`,
      { q: query, limit: this.resultLimit() }
    );
  }

  getResource(connectionId: string, graphUri: string, resourceUri: string): Observable<Resource> {
    return this.api.get<Resource>(
      `/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources/${encodeURIComponent(resourceUri)}`
    );
  }

  /**
   * Execute a SPARQL query with timeout from settings
   */
  executeSparql(connectionId: string, query: string, graph?: string): Observable<QueryResult> {
    return this.api.post<QueryResult>(
      `/triplestores/${connectionId}/sparql`,
      { query, graph, limit: this.resultLimit() },
      { operationType: 'sparql' }
    );
  }

  /**
   * Execute SPARQL on the default triplestore
   */
  executeSparqlOnDefault(query: string, graph?: string): Observable<QueryResult | null> {
    const defaultId = this.defaultTriplestoreId();
    if (!defaultId) {
      return of(null);
    }
    return this.executeSparql(defaultId, query, graph);
  }

  uploadRdf(connectionId: string, graphUri: string, content: string, format: RdfFormat): Observable<{ triplesLoaded: number }> {
    return this.api.post<{ triplesLoaded: number }>(
      `/triplestores/${connectionId}/upload`,
      { graphUri, content, format },
      { operationType: 'pipeline' }
    );
  }

  deleteGraph(connectionId: string, graphUri: string): Observable<void> {
    return this.api.delete<void>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}`);
  }

  exportGraph(connectionId: string, graphUri: string, format: RdfFormat): Observable<string> {
    return this.api.get<string>(
      `/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/export`,
      { format },
      { operationType: 'sparql' }
    );
  }
}
