export type CubeStatus = 'draft' | 'mapped' | 'transformed' | 'published';

export interface Cube {
  id: string;
  uri: string;
  name: string;
  description?: string;
  status?: CubeStatus;
  sourceDataId?: string;
  pipelineId?: string;
  shapeId?: string;
  triplestoreId?: string;
  graphUri?: string;
  observationCount?: number;
  mappingsVersion?: number;
  metadata?: CubeMetadata;
  csvSettings?: CsvSettings;
  lastPublished?: Date;
  createdBy?: string;
  createdAt: Date;
  updatedAt?: Date;
}

export interface CubeMetadata {
  columnMappings?: ColumnMapping[];
  lastGeneratedMappingsVersion?: number;
  [key: string]: unknown;
}

export interface CsvSettings {
  delimiter?: string;
  encoding?: string;
  quoteChar?: string;
}

export interface ColumnMapping {
  name: string;
  role: 'dimension' | 'measure' | 'attribute' | 'ignore';
  datatype?: string;
  predicateUri?: string;
  keyDimension?: boolean;
  scaleType?: string;
  unitUri?: string;
  unitLabel?: string;
  sharedDimensionUri?: string;
  metadata?: Record<string, unknown>;
}

export interface ObservationPage {
  items: Record<string, unknown>[];
  columns: ObservationColumn[];
  totalCount: number;
  page: number;
  size: number;
}

export interface ObservationColumn {
  name: string;
  propertyUri: string;
  role: string;
  datatype?: string;
}

export interface CsvPreview {
  fileName: string;
  rowCount: number;
  columns: CsvColumnPreview[];
}

export interface CsvColumnPreview {
  name: string;
  sampleValues: string[];
  mapped: boolean;
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
  metadata?: CubeMetadata;
}
