import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { SettingsService } from './settings.service';
import { OPERATION_TYPE, CUSTOM_TIMEOUT } from '../interceptors/timeout.interceptor';

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
    return this.http.get<T>(`${this.baseUrl}${url}`, {
      params: this.toHttpParams(params),
      context: this.createContext(options)
    });
  }

  getArray<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Observable<T[]> {
    // Apply default page size if not specified
    const effectiveParams = {
      ...params,
      size: params?.['size'] ?? this.defaultPageSize
    };

    return this.http.get<PagedResponse<T> | T[]>(`${this.baseUrl}${url}`, {
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
  }

  /**
   * Get paginated results with full page metadata
   */
  getPaged<T>(url: string, params?: Record<string, unknown>, options?: RequestOptions): Observable<PagedResponse<T>> {
    const effectiveParams = {
      ...params,
      size: params?.['size'] ?? this.defaultPageSize
    };

    return this.http.get<PagedResponse<T>>(`${this.baseUrl}${url}`, {
      params: this.toHttpParams(effectiveParams),
      context: this.createContext(options)
    });
  }

  post<T>(url: string, data: unknown, options?: RequestOptions): Observable<T> {
    return this.http.post<T>(`${this.baseUrl}${url}`, data, {
      context: this.createContext(options)
    });
  }

  put<T>(url: string, data: unknown, options?: RequestOptions): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}${url}`, data, {
      context: this.createContext(options)
    });
  }

  delete<T>(url: string, options?: RequestOptions): Observable<T> {
    return this.http.delete<T>(`${this.baseUrl}${url}`, {
      context: this.createContext(options)
    });
  }

  upload<T>(url: string, file: File, additionalData?: Record<string, unknown>, options?: RequestOptions): Observable<T> {
    const formData = new FormData();
    formData.append('file', file);
    if (additionalData) {
      Object.entries(additionalData).forEach(([key, value]) => {
        formData.append(key, String(value));
      });
    }
    return this.http.post<T>(`${this.baseUrl}${url}`, formData, {
      context: this.createContext(options)
    });
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
}