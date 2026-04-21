export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

export interface Project {
  id: string;
  name: string;
  description: string;
  baseUri: string;
  status: ProjectStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, unknown>;
}

export interface ProjectSummary extends Project {
  counts: Record<string, number>;
  lastActivity?: string;
  lastRelease?: string;
}

export interface ProjectCreateRequest {
  name: string;
  description?: string;
  baseUri: string;
  metadata?: Record<string, unknown>;
}

export interface ProjectUpdateRequest {
  name?: string;
  description?: string;
  baseUri?: string;
  metadata?: Record<string, unknown>;
}
