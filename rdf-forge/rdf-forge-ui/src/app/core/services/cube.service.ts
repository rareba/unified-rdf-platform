import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Cube, CubeCreateRequest } from '../models/cube.model';

export interface CubeListParams {
  projectId?: string;
  search?: string;
  page?: number;
  size?: number;
}

@Injectable({
  providedIn: 'root'
})
export class CubeService {
  private readonly api = inject(ApiService);

  list(params?: CubeListParams): Observable<Cube[]> {
    return this.api.getArray<Cube>('/cubes', params as Record<string, unknown>);
  }

  get(id: string): Observable<Cube> {
    return this.api.get<Cube>(`/cubes/${id}`);
  }

  create(data: CubeCreateRequest): Observable<Cube> {
    return this.api.post<Cube>('/cubes', data);
  }

  update(id: string, data: Partial<Cube>): Observable<Cube> {
    return this.api.put<Cube>(`/cubes/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/cubes/${id}`);
  }

  publish(id: string): Observable<Cube> {
    return this.api.post<Cube>(`/cubes/${id}/publish`, {});
  }
}
