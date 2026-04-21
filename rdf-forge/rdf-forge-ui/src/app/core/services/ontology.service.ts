import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  NamespaceMap,
  Ontology,
  OntologyContent,
  OntologyImportRequest,
  OntologyUpdateRequest,
  OntologyValidationResult,
  RdfFormat,
  TermDetail,
  TermResult
} from '../models';

/**
 * Client for the Ontology Studio REST endpoints exposed by rdf-forge-shacl-service
 * under /api/v1/ontologies.
 */
@Injectable({ providedIn: 'root' })
export class OntologyService {
  private readonly api = inject(ApiService);

  list(projectId: string): Observable<Ontology[]> {
    return this.api.get<Ontology[]>('/ontologies', { projectId });
  }

  get(id: string): Observable<Ontology> {
    return this.api.get<Ontology>(`/ontologies/${id}`);
  }

  import(req: OntologyImportRequest): Observable<Ontology> {
    return this.api.post<Ontology>('/ontologies/import', req);
  }

  updateMetadata(id: string, req: OntologyUpdateRequest): Observable<Ontology> {
    return this.api.put<Ontology>(`/ontologies/${id}`, req);
  }

  updateContent(id: string, content: string, format: RdfFormat): Observable<Ontology> {
    return this.api.put<Ontology>(`/ontologies/${id}/content`, { content, format });
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/ontologies/${id}`);
  }

  namespaces(id: string): Observable<NamespaceMap> {
    return this.api.get<NamespaceMap>(`/ontologies/${id}/namespaces`);
  }

  classes(id: string, q?: string, limit = 50): Observable<TermResult[]> {
    return this.api.get<TermResult[]>(`/ontologies/${id}/classes`, this.searchParams(q, limit));
  }

  properties(id: string, q?: string, limit = 50): Observable<TermResult[]> {
    return this.api.get<TermResult[]>(`/ontologies/${id}/properties`, this.searchParams(q, limit));
  }

  skosConcepts(id: string, q?: string, limit = 50): Observable<TermResult[]> {
    return this.api.get<TermResult[]>(`/ontologies/${id}/skos-concepts`, this.searchParams(q, limit));
  }

  termDetail(id: string, uri: string): Observable<TermDetail> {
    return this.api.get<TermDetail>(`/ontologies/${id}/term`, { uri });
  }

  exportContent(id: string, format?: RdfFormat): Observable<OntologyContent> {
    const params = format ? { format } : undefined;
    return this.api.get<OntologyContent>(`/ontologies/${id}/content`, params);
  }

  validate(id: string): Observable<OntologyValidationResult> {
    return this.api.post<OntologyValidationResult>(`/ontologies/${id}/validate`, {});
  }

  private searchParams(q: string | undefined, limit: number): Record<string, unknown> {
    const out: Record<string, unknown> = { limit };
    if (q && q.trim().length > 0) out['q'] = q.trim();
    return out;
  }
}
