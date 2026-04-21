import { Injectable, inject } from '@angular/core';
import { forkJoin, map, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import {
  EXTENSION_KINDS,
  ExtensionDescriptor,
  ExtensionKind,
  ExtensionSummary
} from '../models/extension.model';

/**
 * Client for the Extension Catalog endpoints. Calls the aggregated
 * auth-service meta-endpoint by default; falls back to per-service
 * endpoints when the meta-endpoint is unavailable so local dev works
 * without auth-service running.
 */
@Injectable({ providedIn: 'root' })
export class ExtensionService {
  private readonly api = inject(ApiService);

  /**
   * Fetch every registered extension across every service.
   * Prefers the aggregated endpoint; degrades gracefully to per-service
   * fan-out on the client.
   */
  listAll(): Observable<ExtensionDescriptor[]> {
    return this.api.getArray<ExtensionDescriptor>('/admin/extensions').pipe(
      catchError(() => this.listAllByFanOut())
    );
  }

  /**
   * Client-side fan-out — parallel fetch to every per-service endpoint
   * and concatenate. Used when the meta-endpoint 404s.
   */
  listAllByFanOut(): Observable<ExtensionDescriptor[]> {
    const calls = EXTENSION_KINDS.map(kind =>
      this.listByKind(kind).pipe(catchError(() => of<ExtensionDescriptor[]>([])))
    );
    return forkJoin(calls).pipe(map(lists => lists.flat()));
  }

  listByKind(kind: ExtensionKind): Observable<ExtensionDescriptor[]> {
    const path = this.pathForKind(kind);
    return this.api.getArray<ExtensionDescriptor>(path);
  }

  summary(): Observable<ExtensionSummary> {
    return this.api.get<ExtensionSummary>('/admin/extensions/summary');
  }

  /**
   * Map a kind to its service-level endpoint. The meta-endpoint is
   * preferred, but this mapping is used for client-side fan-out.
   */
  private pathForKind(kind: ExtensionKind): string {
    switch (kind) {
      case 'OPERATION':
        return '/extensions/operations';
      case 'FORMAT':
        return '/extensions/formats';
      case 'STORAGE_PROVIDER':
        return '/extensions/storage-providers';
      case 'DESTINATION':
        return '/extensions/destinations';
      case 'TRIPLESTORE_PROVIDER':
        return '/extensions/triplestore-providers';
      case 'MATCHER':
        return '/extensions/matchers';
      case 'VALIDATOR':
        return '/extensions/validators';
      case 'CUBE_PROFILE':
        return '/extensions/cube-profiles';
    }
  }
}
