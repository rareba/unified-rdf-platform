import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
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

  list(): Observable<TriplestoreConnection[]> {
    return this.api.getArray<TriplestoreConnection>('/triplestores');
  }

  get(id: string): Observable<TriplestoreConnection> {
    return this.api.get<TriplestoreConnection>(`/triplestores/${id}`);
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
    return this.api.getArray<Resource>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources`, params);
  }

  searchResources(connectionId: string, graphUri: string, query: string): Observable<Resource[]> {
    return this.api.getArray<Resource>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/search`, { q: query });
  }

  getResource(connectionId: string, graphUri: string, resourceUri: string): Observable<Resource> {
    return this.api.get<Resource>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources/${encodeURIComponent(resourceUri)}`);
  }

  executeSparql(connectionId: string, query: string, graph?: string): Observable<QueryResult> {
    return this.api.post<QueryResult>(`/triplestores/${connectionId}/sparql`, { query, graph });
  }

  uploadRdf(connectionId: string, graphUri: string, content: string, format: RdfFormat): Observable<{ triplesLoaded: number }> {
    return this.api.post<{ triplesLoaded: number }>(`/triplestores/${connectionId}/upload`, { graphUri, content, format });
  }

  deleteGraph(connectionId: string, graphUri: string): Observable<void> {
    return this.api.delete<void>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}`);
  }

  exportGraph(connectionId: string, graphUri: string, format: RdfFormat): Observable<string> {
    return this.api.get<string>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/export`, { format });
  }
}
