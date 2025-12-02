export type DefinitionFormat = 'YAML' | 'TURTLE' | 'JSON';

export interface Pipeline {
  id: string;
  name: string;
  description: string;
  definition: string;
  definitionFormat: DefinitionFormat;
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
  definitionFormat: DefinitionFormat;
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

export type OperationType = 'SOURCE' | 'TRANSFORM' | 'CUBE' | 'VALIDATION' | 'OUTPUT';

export interface OperationParameter {
  name: string;
  description: string;
  type: string;  // e.g. 'java.lang.String', 'java.lang.Boolean', 'java.util.Map'
  required: boolean;
  defaultValue: unknown;
}

export interface Operation {
  id: string;
  name: string;
  description: string;
  type: OperationType;
  parameters: Record<string, OperationParameter>;
}

// Pipeline definition format (JSON)
export interface PipelineDefinition {
  steps: PipelineStep[];
}

export interface PipelineStep {
  id: string;
  operation: string;
  params: Record<string, unknown>;
}

// Visual editor types
export interface PipelineNode {
  id: string;
  operationId: string;
  operationName: string;
  operationType: OperationType;
  x: number;
  y: number;
  params: Record<string, unknown>;
}

export interface PipelineEdge {
  id: string;
  sourceId: string;
  targetId: string;
}
