import { get, post, put, del } from './client'

export interface Shape {
  id: string
  uri: string
  name: string
  description?: string
  targetClass?: string
  content: string
  contentFormat: 'turtle' | 'jsonld'
  category?: string
  tags: string[]
  isTemplate: boolean
  version: number
  createdBy: string
  createdAt: Date
  updatedAt: Date
}

export interface ShapeCreateRequest {
  uri: string
  name: string
  description?: string
  targetClass?: string
  content: string
  contentFormat: 'turtle' | 'jsonld'
  category?: string
  tags?: string[]
}

export interface PropertyShape {
  path: string
  name?: string
  description?: string
  datatype?: string
  class?: string
  nodeKind?: string
  minCount?: number
  maxCount?: number
  minLength?: number
  maxLength?: number
  minInclusive?: number
  maxInclusive?: number
  pattern?: string
  in?: string[]
  hasValue?: any
  node?: string
  message?: string
}

export interface ValidationResult {
  conforms: boolean
  violationCount: number
  violations: ValidationViolation[]
  executionTime: number
}

export interface ValidationViolation {
  focusNode: string
  path?: string
  value?: any
  message: string
  severity: 'Violation' | 'Warning' | 'Info'
  constraint: string
  sourceShape: string
}

export async function fetchShapes(params?: { search?: string; category?: string; isTemplate?: boolean }): Promise<Shape[]> {
  return get<Shape[]>('/shapes', params)
}

export async function fetchShape(id: string): Promise<Shape> {
  return get<Shape>(`/shapes/${id}`)
}

export async function createShape(data: ShapeCreateRequest): Promise<Shape> {
  return post<Shape>('/shapes', data)
}

export async function updateShape(id: string, data: Partial<ShapeCreateRequest>): Promise<Shape> {
  return put<Shape>(`/shapes/${id}`, data)
}

export async function deleteShape(id: string): Promise<void> {
  return del(`/shapes/${id}`)
}

export async function validateSyntax(content: string, format: 'turtle' | 'jsonld'): Promise<{ valid: boolean; errors: string[] }> {
  return post('/shapes/validate-syntax', { content, format })
}

export async function runValidation(shapeId: string, data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples'): Promise<ValidationResult> {
  return post<ValidationResult>('/validation/run', { shapeId, data, dataFormat })
}

export async function runValidationOnGraph(shapeId: string, triplestoreId: string, graphUri: string): Promise<ValidationResult> {
  return post<ValidationResult>('/validation/run', { shapeId, triplestoreId, graphUri })
}

export async function inferShape(data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples', options?: { targetClass?: string }): Promise<Shape> {
  return post<Shape>('/shapes/infer', { data, dataFormat, ...options })
}

export async function generateTurtle(nodeShape: { uri: string; targetClass: string; properties: PropertyShape[] }): Promise<string> {
  return post<string>('/shapes/generate', nodeShape)
}

export async function fetchShapeVersions(id: string): Promise<{ version: number; createdAt: Date }[]> {
  return get(`/shapes/${id}/versions`)
}

export async function fetchShapeTemplates(): Promise<Shape[]> {
  return get<Shape[]>('/templates/shapes')
}
