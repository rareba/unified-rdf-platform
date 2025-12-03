import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { ApiService } from './api.service';
import {
  TriplestoreProviderInfo,
  DataFormatInfo,
  StorageProviderInfo,
  DestinationInfo,
  ValidationResult,
  AvailabilityResult
} from '../models';

/**
 * Service for discovering available providers in the system.
 *
 * This service provides access to dynamically registered providers
 * for triplestores, data formats, storage backends, and destinations.
 */
@Injectable({
  providedIn: 'root'
})
export class ProviderService {
  private readonly api = inject(ApiService);

  // Cached observables for providers (loaded once and shared)
  private triplestoreProviders$?: Observable<TriplestoreProviderInfo[]>;
  private dataFormats$?: Observable<DataFormatInfo[]>;
  private storageProviders$?: Observable<StorageProviderInfo[]>;
  private destinations$?: Observable<DestinationInfo[]>;

  // ==================== Triplestore Providers ====================

  /**
   * Get all available triplestore providers.
   * Results are cached for the lifetime of the service.
   */
  getTriplestoreProviders(): Observable<TriplestoreProviderInfo[]> {
    if (!this.triplestoreProviders$) {
      this.triplestoreProviders$ = this.api
        .getArray<TriplestoreProviderInfo>('/triplestores/providers')
        .pipe(shareReplay(1));
    }
    return this.triplestoreProviders$;
  }

  /**
   * Get details about a specific triplestore provider.
   */
  getTriplestoreProvider(type: string): Observable<TriplestoreProviderInfo> {
    return this.api.get<TriplestoreProviderInfo>(`/triplestores/providers/${type}`);
  }

  // ==================== Data Format Handlers ====================

  /**
   * Get all available data format handlers.
   * Results are cached for the lifetime of the service.
   */
  getDataFormats(): Observable<DataFormatInfo[]> {
    if (!this.dataFormats$) {
      this.dataFormats$ = this.api
        .getArray<DataFormatInfo>('/data/formats')
        .pipe(shareReplay(1));
    }
    return this.dataFormats$;
  }

  /**
   * Get details about a specific format handler.
   */
  getDataFormat(format: string): Observable<DataFormatInfo> {
    return this.api.get<DataFormatInfo>(`/data/formats/${format}`);
  }

  /**
   * Get format handler for a specific file extension.
   */
  getDataFormatByExtension(extension: string): Observable<DataFormatInfo> {
    return this.api.get<DataFormatInfo>(`/data/formats/by-extension/${extension}`);
  }

  // ==================== Storage Providers ====================

  /**
   * Get all available storage providers.
   * Results are cached for the lifetime of the service.
   */
  getStorageProviders(): Observable<StorageProviderInfo[]> {
    if (!this.storageProviders$) {
      this.storageProviders$ = this.api
        .getArray<StorageProviderInfo>('/data/storage/providers')
        .pipe(shareReplay(1));
    }
    return this.storageProviders$;
  }

  /**
   * Get the currently active storage provider.
   */
  getActiveStorageProvider(): Observable<{ type: string; provider: StorageProviderInfo }> {
    return this.api.get<{ type: string; provider: StorageProviderInfo }>('/data/storage/providers/active');
  }

  // ==================== Destination Providers ====================

  /**
   * Get all available destination providers.
   * Results are cached for the lifetime of the service.
   */
  getDestinations(): Observable<DestinationInfo[]> {
    if (!this.destinations$) {
      this.destinations$ = this.api
        .getArray<DestinationInfo>('/destinations')
        .pipe(shareReplay(1));
    }
    return this.destinations$;
  }

  /**
   * Get destinations grouped by category.
   */
  getDestinationsByCategory(): Observable<Record<string, DestinationInfo[]>> {
    return this.api.get<Record<string, DestinationInfo[]>>('/destinations/by-category');
  }

  /**
   * Get details about a specific destination.
   */
  getDestination(type: string): Observable<DestinationInfo> {
    return this.api.get<DestinationInfo>(`/destinations/${type}`);
  }

  /**
   * Get all supported RDF formats across destinations.
   */
  getSupportedFormats(): Observable<string[]> {
    return this.api.getArray<string>('/destinations/formats');
  }

  /**
   * Get destinations that support a specific format.
   */
  getDestinationsByFormat(format: string): Observable<DestinationInfo[]> {
    return this.api.getArray<DestinationInfo>(`/destinations/by-format/${format}`);
  }

  /**
   * Validate a destination configuration.
   */
  validateDestinationConfig(type: string, config: Record<string, unknown>): Observable<ValidationResult> {
    return this.api.post<ValidationResult>(`/destinations/${type}/validate`, config);
  }

  /**
   * Check if a destination is reachable.
   */
  checkDestinationAvailability(type: string, config: Record<string, unknown>): Observable<AvailabilityResult> {
    return this.api.post<AvailabilityResult>(`/destinations/${type}/check-availability`, config);
  }

  /**
   * Get available destination categories.
   */
  getDestinationCategories(): Observable<string[]> {
    return this.api.getArray<string>('/destinations/categories');
  }

  // ==================== Cache Management ====================

  /**
   * Clear all cached provider data.
   * Call this if providers may have been added/removed.
   */
  clearCache(): void {
    this.triplestoreProviders$ = undefined;
    this.dataFormats$ = undefined;
    this.storageProviders$ = undefined;
    this.destinations$ = undefined;
  }
}
