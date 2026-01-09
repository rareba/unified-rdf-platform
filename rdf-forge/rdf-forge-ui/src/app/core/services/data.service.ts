import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpEvent, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, from, Subject } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { DataSource, DataPreview, UploadOptions, ColumnInfo, FormatDetectionResult } from '../models';
import { environment } from '../../../environments/environment';

export interface UploadProgress {
  progress: number;
  loaded: number;
  total: number;
}

export interface DataSourceListParams {
  search?: string;
  format?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DataService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  list(params?: DataSourceListParams): Observable<DataSource[]> {
    return this.api.getArray<DataSource>('/data', params as Record<string, unknown>);
  }

  get(id: string): Observable<DataSource> {
    return this.api.get<DataSource>(`/data/${id}`);
  }

  upload(file: File, options?: UploadOptions): Observable<DataSource> {
    return this.api.upload<DataSource>('/data/upload', file, options as Record<string, unknown>);
  }

  /**
   * Upload a file with progress reporting
   * @param file The file to upload
   * @param options Upload options
   * @param progressCallback Callback function receiving progress updates (0-100)
   * @returns Observable that emits the uploaded DataSource on completion
   */
  uploadWithProgress(
    file: File,
    options?: UploadOptions,
    progressCallback?: (progress: UploadProgress) => void
  ): Observable<DataSource> {
    const formData = new FormData();
    formData.append('file', file);
    if (options) {
      Object.entries(options).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          formData.append(key, String(value));
        }
      });
    }

    const request = new HttpRequest('POST', `${environment.apiBaseUrl}/data/upload`, formData, {
      reportProgress: true
    });

    return this.http.request<DataSource>(request).pipe(
      tap(event => {
        if (event.type === HttpEventType.UploadProgress && progressCallback) {
          const total = event.total || file.size;
          const progress = Math.round((event.loaded / total) * 100);
          progressCallback({
            progress,
            loaded: event.loaded,
            total
          });
        }
      }),
      filter((event): event is HttpResponse<DataSource> => event.type === HttpEventType.Response),
      map(response => response.body as DataSource)
    );
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/data/${id}`);
  }

  preview(id: string, params?: { rows?: number; offset?: number }): Observable<DataPreview> {
    return this.api.get<DataPreview>(`/data/${id}/preview`, params as Record<string, unknown>);
  }

  analyze(id: string): Observable<{ columns: ColumnInfo[]; rowCount: number }> {
    return this.api.post<{ columns: ColumnInfo[]; rowCount: number }>(`/data/${id}/analyze`, {});
  }

  download(id: string): Observable<void> {
    return from(this.downloadFile(id));
  }

  private async downloadFile(id: string): Promise<void> {
    const token = localStorage.getItem('access_token');
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${environment.apiBaseUrl}/data/${id}/download`, { headers });
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const disposition = response.headers.get('Content-Disposition');
    a.download = (disposition?.match(/filename="(.+)"/) || [])[1] || 'download';
    a.click();
    URL.revokeObjectURL(url);
  }

  detectFormat(file: File): Observable<FormatDetectionResult> {
    const formData = new FormData();
    formData.append('file', file.slice(0, 10000));
    return this.http.post<FormatDetectionResult>(`${environment.apiBaseUrl}/data/detect-format`, formData);
  }
}
