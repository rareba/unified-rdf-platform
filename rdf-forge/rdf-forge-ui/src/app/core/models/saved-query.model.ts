/**
 * Types for the Phase 7 SPARQL Workbench 2.0 feature.
 */

export type SavedQueryType = 'ASK' | 'SELECT' | 'CONSTRUCT' | 'DESCRIBE' | 'UPDATE';

export type SavedQueryParameterType = 'uri' | 'literal' | 'string' | 'number';

export interface SavedQueryParameterSpec {
  type: SavedQueryParameterType;
  default?: string;
  description?: string;
}

export interface SavedQuery {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  type: SavedQueryType;
  queryText: string;
  parameters?: Record<string, SavedQueryParameterSpec>;
  tags?: string[];
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
  runCount: number;
  lastRun?: string;
}

export interface SavedQueryCreateRequest {
  projectId: string;
  name: string;
  description?: string;
  type: SavedQueryType;
  queryText: string;
  parameters?: Record<string, SavedQueryParameterSpec>;
  tags?: string[];
}

export interface SavedQueryUpdateRequest {
  name?: string;
  description?: string;
  type?: SavedQueryType;
  queryText?: string;
  parameters?: Record<string, SavedQueryParameterSpec>;
  tags?: string[];
}

/**
 * Parameter values at execution time — the server accepts either the raw value
 * or a `{ type, value }` envelope. We always send the envelope so Jena binds
 * with the right node type.
 */
export interface SavedQueryRunParameter {
  type: SavedQueryParameterType;
  value: string;
}

export interface SavedQueryRunRequest {
  queryText?: string;
  triplestoreId: string;
  graph?: string;
  parameters?: Record<string, SavedQueryRunParameter>;
}

export interface SavedQueryBindingCell {
  type: string;
  value: string;
  datatype?: string;
  language?: string;
}

export interface SavedQueryRunResponse {
  type: SavedQueryType;
  variables?: string[];
  bindings?: Record<string, SavedQueryBindingCell>[];
  askResult?: boolean;
  rdf?: string;
  rdfFormat?: string;
  durationMs: number;
  executedAt: string;
}
