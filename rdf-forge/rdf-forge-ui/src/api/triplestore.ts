import { get, post, put, del } from './client'

export interface TriplestoreConnection {
  id: string
  name: string
  type: 'fuseki' | 'stardog' | 'graphdb' | 'neptune' | 'virtuoso'
  url: string
  defaultGraph?: string
  authType: 'none' | 'basic' | 'apikey' | 'oauth2'
  isDefault: boolean
  healthStatus: 'healthy' | 'unhealthy' | 'unknown'
  lastHealthCheck?: Date
  createdBy: string
  createdAt: Date
}

export interface Graph {
  uri: string
  tripleCount: number
  lastModified?: Date
}

export interface Resource {
  uri: string
  type?: string
  label?: string
  properties: PropertyValue[]
  types: string[]
}

export interface PropertyValue {
  predicate: string
  object: string
  objectType: 'uri' | 'literal'
  datatype?: string
  language?: string
}

export interface QueryResult {
  variables: string[]
  bindings: Record<string, { type: string; value: string; datatype?: string; language?: string }>[]
  executionTime: number
}

export interface ConnectionCreateRequest {
  name: string
  type: 'fuseki' | 'stardog' | 'graphdb' | 'neptune' | 'virtuoso'
  url: string
  defaultGraph?: string
  authType: 'none' | 'basic' | 'apikey' | 'oauth2'
  authConfig?: {
    username?: string
    password?: string
    apiKey?: string
  }
  isDefault?: boolean
}

export async function fetchConnections(): Promise<TriplestoreConnection[]> {
  return get<TriplestoreConnection[]>('/triplestores')
}

export async function fetchConnection(id: string): Promise<TriplestoreConnection> {
  return get<TriplestoreConnection>(`/triplestores/${id}`)
}

export async function createConnection(data: ConnectionCreateRequest): Promise<TriplestoreConnection> {
  return post<TriplestoreConnection>('/triplestores', data)
}

export async function updateConnection(id: string, data: Partial<ConnectionCreateRequest>): Promise<TriplestoreConnection> {
  return put<TriplestoreConnection>(`/triplestores/${id}`, data)
}

export async function deleteConnection(id: string): Promise<void> {
  return del(`/triplestores/${id}`)
}

export async function testConnection(id: string): Promise<{ success: boolean; message?: string; latencyMs?: number }> {
  return get(`/triplestores/${id}/health`)
}

export async function connect(id: string): Promise<void> {
  return post(`/triplestores/${id}/connect`)
}

export async function fetchGraphs(connectionId: string): Promise<Graph[]> {
  return get<Graph[]>(`/triplestores/${connectionId}/graphs`)
}

export async function fetchGraphResources(connectionId: string, graphUri: string, params?: { limit?: number; offset?: number }): Promise<Resource[]> {
  return get<Resource[]>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources`, params)
}

export async function searchResources(connectionId: string, graphUri: string, query: string): Promise<Resource[]> {
  return get<Resource[]>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/search`, { q: query })
}

export async function fetchResource(connectionId: string, graphUri: string, resourceUri: string): Promise<Resource> {
  return get<Resource>(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/resources/${encodeURIComponent(resourceUri)}`)
}

export async function executeSparql(connectionId: string, query: string, graph?: string): Promise<QueryResult> {
  return post<QueryResult>(`/triplestores/${connectionId}/sparql`, { query, graph })
}

export async function uploadRdf(connectionId: string, graphUri: string, content: string, format: 'turtle' | 'rdfxml' | 'ntriples' | 'jsonld'): Promise<{ triplesLoaded: number }> {
  return post(`/triplestores/${connectionId}/upload`, { graphUri, content, format })
}

export async function deleteGraph(connectionId: string, graphUri: string): Promise<void> {
  return del(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}`)
}

export async function exportGraph(connectionId: string, graphUri: string, format: 'turtle' | 'rdfxml' | 'ntriples' | 'jsonld'): Promise<string> {
  return get(`/triplestores/${connectionId}/graphs/${encodeURIComponent(graphUri)}/export`, { format })
}
