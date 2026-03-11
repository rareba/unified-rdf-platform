export type ContentFormat = 'turtle' | 'jsonld' | 'TURTLE' | 'JSON_LD';
export type ValidationSeverity = 'Violation' | 'Warning' | 'Info';

export interface Shape {
  id: string;
  uri: string;
  name: string;
  description?: string;
  targetClass?: string;
  content: string;
  contentFormat: ContentFormat;
  category?: string;
  tags: string[];
  isTemplate: boolean;
  version: number;
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface ShapeCreateRequest {
  uri: string;
  name: string;
  description?: string;
  targetClass?: string;
  content: string;
  contentFormat: ContentFormat;
  category?: string;
  tags?: string[];
}

export interface PropertyShape {
  path: string;
  name?: string;
  description?: string;
  datatype?: string;
  class?: string;
  nodeKind?: string;
  minCount?: number;
  maxCount?: number;
  minLength?: number;
  maxLength?: number;
  minInclusive?: number;
  maxInclusive?: number;
  pattern?: string;
  in?: string[];
  hasValue?: unknown;
  node?: string;
  message?: string;
}

export interface ValidationResult {
  conforms: boolean;
  violationCount: number;
  violations: ValidationViolation[];
  executionTime: number;
}

export interface ValidationViolation {
  focusNode: string;
  path?: string;
  value?: unknown;
  message: string;
  severity: ValidationSeverity;
  constraint: string;
  sourceShape: string;
}

export interface ShapeVersion {
  version: number;
  createdAt: Date;
}

/** Used by shacl-studio for inline validation errors */
export interface ValidationError {
  message?: string;
  path?: string;
  severity?: string;
  sourceShape?: string;
}

/** Profile metadata for SHACL shape profiles */
export interface ShapeProfile {
  id: string;
  name: string;
  description?: string;
}

/** Options for content validation */
export interface ValidationOptions {
  content: string;
  profile?: string;
}
