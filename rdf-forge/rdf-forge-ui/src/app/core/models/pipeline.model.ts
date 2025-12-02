export interface Pipeline {
  id: string;
  name: string;
  description: string;
  definition: string;
  definitionFormat: 'yaml' | 'turtle';
  variables: Record<string, unknown>;
  tags: string[];
  stepsCount: number;
  status: 'active' | 'draft' | 'archived';
  lastRun?: Date;
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface PipelineCreateRequest {
  name: string;
  description?: string;
  definition: string;
  definitionFormat: 'yaml' | 'turtle';
  variables?: Record<string, unknown>;
  tags?: string[];
}

export interface PipelineValidationResult {
  valid: boolean;
  errors: { path: string; message: string }[];
  warnings: { path: string; message: string }[];
}

export interface PipelineVersion {
  version: number;
  createdAt: Date;
  changeMessage?: string;
}

export interface Operation {
  id: string;
  name: string;
  category: string;
  description: string;
  inputs: { name: string; type: string; required: boolean }[];
  outputs: { name: string; type: string }[];
  parameters: { name: string; type: string; required: boolean; default?: unknown; description: string }[];
}
