import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, timer } from 'rxjs';
import { map, retry, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { SettingsService } from './settings.service';
import { OPERATION_TYPE, CUSTOM_TIMEOUT } from '../interceptors/timeout.interceptor';

/**
 * Configuration for request retry behavior
 */
export interface RetryConfig {
  maxRetries: number;
  initialDelayMs: number;
  maxDelayMs: number;
  retryableStatuses: number[];
}

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  initialDelayMs: 1000,
  maxDelayMs: 10000,
  retryableStatuses: [0, 408, 429, 500, 502, 503, 504] // Network error, timeout, rate limit, server errors
};

interface PagedResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalPages: number;
  totalElements: number;
}

export interface RequestOptions {
  params?: Record<string, unknown>;
  operationType?: 'pipeline' | 'sparql' | 'default';
  timeoutMs?: number;
  /** Enable automatic retry with exponential backoff for transient failures */
  enableRetry?: boolean;
  /** Custom retry configuration */
  retryConfig?: Partial<RetryConfig>;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly settingsService = inject(SettingsService);
  private baseUrl = environment.apiBaseUrl;

  /**
   * Get the default page size from settings
   */
  get defaultPageSize(): number {
    return this.settingsService.pageSize();
  }

  /**
   * Get the default result limit for SPARQL queries
   */
  get sparqlResultLimit(): number {
    return this.settingsService.sparqlResultLimit();
  }

  get<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Observable<T> {
    const request$ = this.http.get<T>(`${this.baseUrl}${url}`, {
      params: this.toHttpParams(params),
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  getArray<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Observable<T[]> {
    // Apply default page size if not specified
    const effectiveParams = {
      ...params,
      size: params?.['size'] ?? this.defaultPageSize
    };

    const request$ = this.http.get<PagedResponse<T> | T[]>(`${this.baseUrl}${url}`, {
      params: this.toHttpParams(effectiveParams),
      context: this.createContext(options)
    }).pipe(
      map(response => {
        if (response && typeof response === 'object' && 'content' in response) {
          return (response as PagedResponse<T>).content;
        }
        return response as T[];
      })
    );
    return this.applyRetry(request$, options);
  }

  /**
   * Get paginated results with full page metadata
   */
  getPaged<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Observable<PagedResponse<T>> {
    const effectiveParams = {
      ...params,
      size: params?.['size'] ?? this.defaultPageSize
    };

    const request$ = this.http.get<PagedResponse<T>>(`${this.baseUrl}${url}`, {
      params: this.toHttpParams(effectiveParams),
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  post<T>(url: string, data: unknown, options?: RequestOptions): Observable<T> {
    const request$ = this.http.post<T>(`${this.baseUrl}${url}`, data, {
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  put<T>(url: string, data: unknown, options?: RequestOptions): Observable<T> {
    const request$ = this.http.put<T>(`${this.baseUrl}${url}`, data, {
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  delete<T>(url: string, options?: RequestOptions): Observable<T> {
    const request$ = this.http.delete<T>(`${this.baseUrl}${url}`, {
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  upload<T>(url: string, file: File, additionalData?: Record<string, unknown>, options?: RequestOptions): Observable<T> {
    const formData = new FormData();
    formData.append('file', file);
    if (additionalData) {
      Object.entries(additionalData).forEach(([key, value]) => {
        formData.append(key, String(value));
      });
    }
    const request$ = this.http.post<T>(`${this.baseUrl}${url}`, formData, {
      context: this.createContext(options)
    });
    return this.applyRetry(request$, options);
  }

  private toHttpParams(params?: Record<string, unknown>): HttpParams {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== null && params[key] !== undefined) {
          httpParams = httpParams.set(key, String(params[key]));
        }
      });
    }
    return httpParams;
  }

  private createContext(options?: RequestOptions): HttpContext {
    let context = new HttpContext();

    if (options?.operationType) {
      context = context.set(OPERATION_TYPE, options.operationType);
    }

    if (options?.timeoutMs) {
      context = context.set(CUSTOM_TIMEOUT, options.timeoutMs);
    }

    return context;
  }

  /**
   * Apply retry logic with exponential backoff to an observable request
   */
  private applyRetry<T>(request$: Observable<T>, options?: RequestOptions): Observable<T> {
    // Check if retry is enabled (can be set via options or settings)
    const shouldRetry = options?.enableRetry ?? this.settingsService.autoRetryFailed();

    if (!shouldRetry) {
      return request$;
    }

    const config: RetryConfig = {
      ...DEFAULT_RETRY_CONFIG,
      ...options?.retryConfig,
      maxRetries: options?.retryConfig?.maxRetries ?? this.settingsService.retryAttempts()
    };

    if (config.maxRetries <= 0) {
      return request$;
    }

    return request$.pipe(
      retry({
        count: config.maxRetries,
        delay: (error, retryCount) => {
          // Only retry on retryable errors
          if (!this.isRetryableError(error, config.retryableStatuses)) {
            return throwError(() => error);
          }

          // Calculate delay with exponential backoff and jitter
          const baseDelay = config.initialDelayMs * Math.pow(2, retryCount - 1);
          const jitter = Math.random() * 0.3 * baseDelay; // Add up to 30% jitter
          const delay = Math.min(baseDelay + jitter, config.maxDelayMs);

          console.log(`Request failed, retrying in ${Math.round(delay)}ms (attempt ${retryCount}/${config.maxRetries})`);

          return timer(delay);
        }
      }),
      catchError(error => {
        // Enhance error message for better debugging
        if (error instanceof HttpErrorResponse) {
          console.error(`Request failed after ${config.maxRetries} retries:`, error.url, error.status);
        }
        return throwError(() => error);
      })
    );
  }

  /**
   * Determine if an error should trigger a retry
   */
  private isRetryableError(error: unknown, retryableStatuses: number[]): boolean {
    if (error instanceof HttpErrorResponse) {
      // Check if status is in the retryable list
      return retryableStatuses.includes(error.status);
    }

    // Network errors (no response) should be retried
    if (error instanceof Error && error.message.includes('Network')) {
      return true;
    }

    return false;
  }
}