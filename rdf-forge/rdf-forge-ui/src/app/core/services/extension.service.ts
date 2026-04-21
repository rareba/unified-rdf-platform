import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  ExtensionDescriptor,
  ExtensionKind,
  ExtensionSummary
} from '../models/extension.model';

/**
 * Client for the Extension Catalog.
 *
 * Calls the auth-service meta-aggregator at /admin/extensions. Per-service
 * extension endpoints (/extensions/operations, /extensions/formats, etc.)
 * exist and are routed individually by the gateway, but client-side fan-out
 * was removed because it hid partial failures behind an "empty list" facade
 * — if you want the real picture, call the aggregator; if the aggregator is
 * down, that is a visible error, not silent degradation.
 */
@Injectable({ providedIn: 'root' })
export class ExtensionService {
  private readonly api = inject(ApiService);

  /**
   * Fetch every registered extension across every service via the
   * auth-service meta-aggregator. Returns an error observable on failure.
   */
  listAll(): Observable<ExtensionDescriptor[]> {
    return this.api.getArray<ExtensionDescriptor>('/admin/extensions');
  }

  /**
   * Filter by kind at the aggregator level. The aggregator accepts
   * ?kind=... query. Keeps the single-round-trip contract.
   */
  listByKind(kind: ExtensionKind): Observable<ExtensionDescriptor[]> {
    return this.api.getArray<ExtensionDescriptor>(`/admin/extensions?kind=${kind}`);
  }

  summary(): Observable<ExtensionSummary> {
    return this.api.get<ExtensionSummary>('/admin/extensions/summary');
  }
}
