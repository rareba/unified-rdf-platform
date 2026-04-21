/**
 * Universal Mapping Studio model definitions. Mirrors the Java DTOs in
 * rdf-forge-pipeline-service (MappingDto, MappingRule, TripleDto, …).
 * Keep these in sync with:
 *   rdf-forge-pipeline-service/.../dto/MappingDto.java
 *   rdf-forge-pipeline-service/.../entity/MappingRule.java
 */

export type SourceType = 'CSV' | 'TSV' | 'JSON' | 'XML' | 'XLSX';
export type MappingType = 'GENERIC' | 'CUBE' | 'SKOS' | 'CUSTOM';
export type RuleType = 'COLUMN_TO_URI' | 'COLUMN_TO_LITERAL' | 'FIXED_URI' | 'NESTED' | 'CONSTANT';
export type TransformType = 'UPPER' | 'LOWER' | 'TRIM' | 'SUBSTRING' | 'REGEX_REPLACE';
export type TripleObjectType = 'URI' | 'LITERAL' | 'BNODE';

export interface MappingRule {
  id: string;
  type: RuleType;
  source?: string | null;
  target?: string | null;
  uriTemplate?: string | null;
  datatype?: string | null;
  language?: string | null;
  transform?: { type: TransformType; params?: Record<string, unknown> } | null;
}

export interface Mapping {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  sourceType: SourceType;
  sourceConfig?: Record<string, unknown>;
  targetNamespace?: string;
  targetOntologies?: Record<string, unknown>;
  rules: MappingRule[];
  mappingType: MappingType;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface MappingCreateRequest {
  projectId: string;
  name: string;
  description?: string;
  sourceType: SourceType;
  sourceConfig?: Record<string, unknown>;
  targetNamespace?: string;
  targetOntologies?: Record<string, unknown>;
  rules?: MappingRule[];
  mappingType?: MappingType;
}

export interface MappingUpdateRequest {
  name?: string;
  description?: string;
  sourceType?: SourceType;
  sourceConfig?: Record<string, unknown>;
  targetNamespace?: string;
  targetOntologies?: Record<string, unknown>;
  rules?: MappingRule[];
}

export interface TripleDto {
  subject: string;
  predicate: string;
  object: string;
  objectType: TripleObjectType;
  datatype?: string | null;
  language?: string | null;
}

export interface MappingPreviewRequest {
  sourceRows?: Record<string, unknown>[];
  sourceDataBase64?: string;
  sourceDataRef?: string;
  sampleLimit?: number;
}

export interface MappingPreviewResponse {
  triples: TripleDto[];
  sampleSize: number;
  totalSourceRows: number;
}

export interface ExplainRequest {
  sourceRowIndex?: number | null;
  sourceRows?: Record<string, unknown>[];
  sampleLimit?: number;
}

export interface TransformStep {
  type: string;
  inputValue: string | null;
  outputValue: string | null;
  params: Record<string, unknown>;
}

export interface ExplainTrace {
  ruleId: string;
  ruleType: string;
  source: string | null;
  target: string | null;
  uriTemplateUsed: string | null;
  sourceValue: unknown;
  transforms: TransformStep[];
  finalValue: string | null;
}

export interface TripleExplain {
  triple: TripleDto;
  trace: ExplainTrace;
}

export interface RowExplain {
  rowIndex: number;
  row: Record<string, unknown>;
  triples: TripleExplain[];
}

export interface ExplainResponse {
  rows: RowExplain[];
}

export interface ValidationIssue {
  ruleId: string | null;
  field: string;
  code: string;
  message: string;
}

export interface MappingValidationResponse {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface MappingValidationRequest {
  availableColumns?: string[];
}
