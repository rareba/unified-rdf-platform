import { get, post, put, del } from './client'

export interface Pipeline {
  id: string
  name: string
  description: string
  definition: string
  definitionFormat: 'yaml' | 'turtle'
  variables: Record<string, any>
  tags: string[]
  stepsCount: number
  status: 'active' | 'draft' | 'archived'
  lastRun?: Date
  createdBy: string
  createdAt: Date
  updatedAt: Date
}

export interface PipelineCreateRequest {
  name: string
  description?: string
  definition: string
  definitionFormat: 'yaml' | 'turtle'
  variables?: Record<string, any>
  tags?: string[]
}

export interface PipelineValidationResult {
  valid: boolean
  errors: { path: string; message: string }[]
  warnings: { path: string; message: string }[]
}

export interface Operation {
  id: string
  name: string
  category: string
  description: string
  inputs: { name: string; type: string; required: boolean }[]
  outputs: { name: string; type: string }[]
  parameters: { name: string; type: string; required: boolean; default?: any; description: string }[]
}

export async function fetchPipelines(params?: { search?: string; status?: string; page?: number; limit?: number }): Promise<Pipeline[]> {
  return get<Pipeline[]>('/pipelines', params)
}

export async function fetchPipeline(id: string): Promise<Pipeline> {
  return get<Pipeline>(`/pipelines/${id}`)
}

export async function createPipeline(data: PipelineCreateRequest): Promise<Pipeline> {
  return post<Pipeline>('/pipelines', data)
}

export async function updatePipeline(id: string, data: Partial<PipelineCreateRequest>): Promise<Pipeline> {
  return put<Pipeline>(`/pipelines/${id}`, data)
}

export async function deletePipeline(id: string): Promise<void> {
  return del(`/pipelines/${id}`)
}

export async function duplicatePipeline(id: string): Promise<Pipeline> {
  return post<Pipeline>(`/pipelines/${id}/duplicate`)
}

export async function validatePipeline(definition: string, format: 'yaml' | 'turtle'): Promise<PipelineValidationResult> {
  return post<PipelineValidationResult>('/pipelines/validate', { definition, format })
}

export async function runPipeline(id: string, variables?: Record<string, any>): Promise<{ jobId: string }> {
  return post<{ jobId: string }>(`/pipelines/${id}/run`, { variables })
}

export async function fetchOperations(): Promise<Operation[]> {
  return get<Operation[]>('/operations')
}

export async function fetchOperation(id: string): Promise<Operation> {
  return get<Operation>(`/operations/${id}`)
}

export async function fetchPipelineVersions(id: string): Promise<{ version: number; createdAt: Date; changeMessage?: string }[]> {
  return get(`/pipelines/${id}/versions`)
}

export async function fetchPipelineVersion(id: string, version: number): Promise<Pipeline> {
  return get<Pipeline>(`/pipelines/${id}/versions/${version}`)
}
