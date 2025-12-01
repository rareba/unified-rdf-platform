import { get, post, del } from './client'

export interface Job {
  id: string
  pipelineId: string
  pipelineName: string
  pipelineVersion: number
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  variables: Record<string, any>
  triggeredBy: 'manual' | 'schedule' | 'api'
  startedAt?: Date
  completedAt?: Date
  duration?: number
  metrics?: {
    rowsProcessed: number
    quadsGenerated: number
  }
  errorMessage?: string
  errorDetails?: {
    stackTrace?: string
    context?: Record<string, any>
  }
  outputGraph?: string
  steps?: JobStep[]
  createdBy: string
  createdAt: Date
}

export interface JobStep {
  id: string
  name: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped'
  duration?: number
  metrics?: {
    rowsProcessed: number
    quadsGenerated?: number
  }
  error?: string
}

export interface JobLog {
  id: string
  timestamp: Date
  level: 'debug' | 'info' | 'warn' | 'error'
  step?: string
  message: string
  details?: Record<string, any>
}

export interface JobSchedule {
  id: string
  pipelineId: string
  cronExpression: string
  variables: Record<string, any>
  isActive: boolean
  lastRun?: Date
  nextRun?: Date
}

export async function fetchJobs(params?: { status?: string; pipelineId?: string; page?: number; limit?: number }): Promise<Job[]> {
  return get<Job[]>('/jobs', params)
}

export async function fetchJob(id: string): Promise<Job> {
  return get<Job>(`/jobs/${id}`)
}

export async function createJob(pipelineId: string, variables?: Record<string, any>, priority?: number): Promise<Job> {
  return post<Job>('/jobs', { pipelineId, variables, priority })
}

export async function cancelJob(id: string): Promise<void> {
  return del(`/jobs/${id}`)
}

export async function retryJob(id: string): Promise<Job> {
  return post<Job>(`/jobs/${id}/retry`)
}

export async function fetchJobLogs(id: string, params?: { level?: string; limit?: number; offset?: number }): Promise<JobLog[]> {
  return get<JobLog[]>(`/jobs/${id}/logs`, params)
}

export async function fetchJobMetrics(id: string): Promise<{ rowsProcessed: number; quadsGenerated: number; duration: number }> {
  return get(`/jobs/${id}/metrics`)
}

export async function fetchSchedules(): Promise<JobSchedule[]> {
  return get<JobSchedule[]>('/schedules')
}

export async function createSchedule(pipelineId: string, cronExpression: string, variables?: Record<string, any>): Promise<JobSchedule> {
  return post<JobSchedule>('/schedules', { pipelineId, cronExpression, variables })
}

export async function updateSchedule(id: string, data: Partial<JobSchedule>): Promise<JobSchedule> {
  return post<JobSchedule>(`/schedules/${id}`, data)
}

export async function deleteSchedule(id: string): Promise<void> {
  return del(`/schedules/${id}`)
}
