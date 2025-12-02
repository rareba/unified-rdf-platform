export type DimensionType = 'TEMPORAL' | 'GEO' | 'KEY' | 'MEASURE' | 'ATTRIBUTE' | 'CODED';
export type HierarchyType = 'SKOS' | 'CUSTOM' | 'FLAT';
export type HierarchySchemeType = 'SKOS_CONCEPT_SCHEME' | 'SKOS_COLLECTION' | 'XKOS_CLASSIFICATION' | 'CUSTOM';

export interface Dimension {
  id?: string;
  projectId?: string;
  uri: string;
  name: string;
  description?: string;
  type: DimensionType;
  hierarchyType?: HierarchyType;
  content?: string;
  baseUri?: string;
  metadata?: Record<string, unknown>;
  version?: number;
  valueCount?: number;
  isShared?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DimensionValue {
  id?: string;
  dimensionId: string;
  uri: string;
  code: string;
  label: string;
  labelLang?: string;
  description?: string;
  parentId?: string;
  hierarchyLevel?: number;
  sortOrder?: number;
  metadata?: Record<string, unknown>;
  altLabels?: Record<string, string>;
  skosNotation?: string;
  isDeprecated?: boolean;
  replacedBy?: string;
}

export interface Hierarchy {
  id?: string;
  dimensionId: string;
  uri: string;
  name: string;
  description?: string;
  hierarchyType: HierarchySchemeType;
  maxDepth?: number;
  rootConceptUri?: string;
  isDefault?: boolean;
}

export interface DimensionStats {
  totalDimensions: number;
  totalValues: number;
  byType: Record<DimensionType, number>;
}
