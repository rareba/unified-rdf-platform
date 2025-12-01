import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  get<T>(url: string, params?: Record<string, unknown>): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}${url}`, { params: this.toHttpParams(params) });
  }

  post<T>(url: string, data: unknown): Observable<T> {
    return this.http.post<T>(`${this.baseUrl}${url}`, data);
  }

  put<T>(url: string, data: unknown): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}${url}`, data);
  }

  delete<T>(url: string): Observable<T> {
    return this.http.delete<T>(`${this.baseUrl}${url}`);
  }

  upload<T>(url: string, file: File, options?: Record<string, unknown>): Observable<T> {
    const formData = new FormData();
    formData.append('file', file);
    if (options) {
      Object.entries(options).forEach(([key, value]) => {
        formData.append(key, String(value));
      });
    }
    return this.http.post<T>(`${this.baseUrl}${url}`, formData);
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
}