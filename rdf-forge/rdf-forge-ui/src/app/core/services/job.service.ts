import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Job, JobLog, JobMetrics, JobSchedule } from '../models';

export interface JobListParams {
  status?: string;
  pipelineId?: string;
  page?: number;
  limit?: number;
}

export interface JobLogParams {
  level?: string;
  limit?: number;
  offset?: number;
}

@Injectable({
  providedIn: 'root'
})
export class JobService {
  private readonly api = inject(ApiService);

  list(params?: JobListParams): Observable<Job[]> {
    return this.api.get<Job[]>('/jobs', params as Record<string, unknown>);
  }

  get(id: string): Observable<Job> {
    return this.api.get<Job>(`/jobs/${id}`);
  }

  create(pipelineId: string, variables?: Record<string, unknown>, priority?: number): Observable<Job> {
    return this.api.post<Job>('/jobs', { pipelineId, variables, priority });
  }

  cancel(id: string): Observable<void> {
    return this.api.delete<void>(`/jobs/${id}`);
  }

  retry(id: string): Observable<Job> {
    return this.api.post<Job>(`/jobs/${id}/retry`, {});
  }

  getLogs(id: string, params?: JobLogParams): Observable<JobLog[]> {
    return this.api.get<JobLog[]>(`/jobs/${id}/logs`, params as Record<string, unknown>);
  }

  getMetrics(id: string): Observable<JobMetrics> {
    return this.api.get<JobMetrics>(`/jobs/${id}/metrics`);
  }

  // Schedule endpoints
  getSchedules(): Observable<JobSchedule[]> {
    return this.api.get<JobSchedule[]>('/schedules');
  }

  createSchedule(pipelineId: string, cronExpression: string, variables?: Record<string, unknown>): Observable<JobSchedule> {
    return this.api.post<JobSchedule>('/schedules', { pipelineId, cronExpression, variables });
  }

  updateSchedule(id: string, data: Partial<JobSchedule>): Observable<JobSchedule> {
    return this.api.post<JobSchedule>(`/schedules/${id}`, data);
  }

  deleteSchedule(id: string): Observable<void> {
    return this.api.delete<void>(`/schedules/${id}`);
  }
}
