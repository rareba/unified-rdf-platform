import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { LineageGraph, LineageNodeKind } from '../models/lineage.model';

/**
 * Typed client for the Lineage / Provenance REST API.
 *
 * <p>Mirrors {@code /api/v1/lineage} in rdf-forge-pipeline-service.
 */
@Injectable({ providedIn: 'root' })
export class LineageService {
  private readonly api = inject(ApiService);

  forProject(projectId: string): Observable<LineageGraph> {
    return this.api.get<LineageGraph>(`/lineage/project/${projectId}`);
  }

  forResource(kind: LineageNodeKind, id: string): Observable<LineageGraph> {
    return this.api.get<LineageGraph>(`/lineage/resource/${kind}/${id}`);
  }
}
