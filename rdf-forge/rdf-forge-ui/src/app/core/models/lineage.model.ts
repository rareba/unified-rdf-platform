/**
 * Lineage / provenance graph model (Phase 6). Mirrors
 * rdf-forge-pipeline-service/.../dto/LineageDto.java.
 */

export type LineageNodeKind =
  | 'PROJECT'
  | 'DATA_SOURCE'
  | 'MAPPING'
  | 'ONTOLOGY'
  | 'SHAPE'
  | 'PIPELINE'
  | 'JOB'
  | 'TRIPLESTORE'
  | 'RELEASE';

export type LineageEdgeKind =
  | 'USED_BY'
  | 'PRODUCED'
  | 'VALIDATED_BY'
  | 'DERIVED_FROM'
  | 'BELONGS_TO'
  | 'REFERENCES';

export interface LineageNode {
  id: string;
  kind: LineageNodeKind;
  label: string;
  updatedAt?: string | null;
  attributes?: Record<string, unknown>;
}

export interface LineageEdge {
  from: string;
  to: string;
  kind: LineageEdgeKind;
  attributes?: Record<string, unknown>;
}

export interface LineageGraph {
  projectId: string;
  nodes: LineageNode[];
  edges: LineageEdge[];
}
