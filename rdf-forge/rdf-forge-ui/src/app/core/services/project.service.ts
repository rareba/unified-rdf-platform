import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  Project,
  ProjectCreateRequest,
  ProjectSummary,
  ProjectStatus,
  ProjectUpdateRequest
} from '../models';

export interface ProjectListParams {
  status?: ProjectStatus;
}

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private readonly api = inject(ApiService);

  list(status?: ProjectStatus): Observable<Project[]> {
    const params: Record<string, unknown> = {};
    if (status) {
      params['status'] = status;
    }
    return this.api.getArray<Project>('/projects', params);
  }

  get(id: string): Observable<Project> {
    return this.api.get<Project>(`/projects/${id}`);
  }

  create(data: ProjectCreateRequest): Observable<Project> {
    return this.api.post<Project>('/projects', data);
  }

  update(id: string, data: ProjectUpdateRequest): Observable<Project> {
    return this.api.put<Project>(`/projects/${id}`, data);
  }

  archive(id: string): Observable<Project> {
    return this.api.post<Project>(`/projects/${id}/archive`, {});
  }

  unarchive(id: string): Observable<Project> {
    return this.api.post<Project>(`/projects/${id}/unarchive`, {});
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/projects/${id}`);
  }

  summary(id: string): Observable<ProjectSummary> {
    return this.api.get<ProjectSummary>(`/projects/${id}/summary`);
  }
}
