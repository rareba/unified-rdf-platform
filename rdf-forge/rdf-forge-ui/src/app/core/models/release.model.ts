/**
 * Release Factory model definitions (Phase 6). Mirrors the Java DTOs in
 * rdf-forge-pipeline-service (ReleaseDto, ReleaseCreateRequest,
 * ReleaseBuildResponse, ReleaseStatus).
 *
 * Keep these in sync with:
 *   rdf-forge-pipeline-service/.../dto/ReleaseDto.java
 *   rdf-forge-pipeline-service/.../dto/ReleaseCreateRequest.java
 */

export type ReleaseStatus = 'DRAFT' | 'BUILDING' | 'PUBLISHED' | 'FAILED' | 'ARCHIVED';

export interface Release {
  id: string;
  projectId: string;
  version: string;
  name: string;
  notes?: string | null;
  status: ReleaseStatus;
  manifest?: Record<string, unknown> | null;
  artifactUri?: string | null;
  artifactSizeBytes: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string | null;
}

export interface ReleaseManifestRefs {
  dataSources?: string[];
  mappings?: string[];
  shapes?: string[];
  ontologies?: string[];
  triplestoreId?: string | null;
  validationSuiteIds?: string[];
}

export interface ReleaseCreateRequest {
  version: string;
  name: string;
  notes?: string | null;
  manifestRefs: ReleaseManifestRefs;
}

export interface ReleaseBuildResponse {
  releaseId: string;
  artifactUri?: string | null;
  artifactSizeBytes: number;
  manifest?: Record<string, unknown> | null;
  validationGateResult?: Record<string, unknown> | null;
}
