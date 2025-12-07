export interface Cube {
  id: string;
  uri: string;
  name: string;
  description?: string;
  sourceDataId?: string;
  pipelineId?: string;
  shapeId?: string;
  triplestoreId?: string;
  graphUri?: string;
  observationCount?: number;
  metadata?: Record<string, unknown>;
  lastPublished?: Date;
  createdBy?: string;
  createdAt: Date;
  updatedAt?: Date;
}

export interface CubeCreateRequest {
  uri: string;
  name: string;
  description?: string;
  sourceDataId?: string;
  pipelineId?: string;
  shapeId?: string;
  triplestoreId?: string;
  graphUri?: string;
  metadata?: Record<string, unknown>;
}
