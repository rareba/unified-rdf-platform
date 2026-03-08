export interface Shape {
  id: string;
  name: string;
  description?: string;
  content: string;
  format: 'TURTLE' | 'RDF/XML' | 'JSON-LD' | 'N3';
  context?: string;
  prefix: string;
  namespace: string;
  targetClass?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ShapeValidationResult {
  valid: boolean;
  errors: ShapeViolation[];
  warnings: ShapeViolation[];
  info: ShapeViolation[];
}

export interface ShapeViolation {
  focusNode: string;
  resultPath?: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  sourceShape?: string;
  value?: string;
}

export interface ValidationProfile {
  id: string;
  name: string;
  description?: string;
  shapes: string[];
  builtIn: boolean;
}

export interface ShapeNode {
  id: string;
  label: string;
  type: 'shape' | 'property' | 'constraint';
  children?: ShapeNode[];
}

export interface ShapeGraph {
  nodes: ShapeNodeView[];
  edges: ShapeEdge[];
}

export interface ShapeNodeView {
  id: string;
  label: string;
  type: 'class' | 'property' | 'datatype';
  x?: number;
  y?: number;
}

export interface ShapeEdge {
  source: string;
  target: string;
  label: string;
}
