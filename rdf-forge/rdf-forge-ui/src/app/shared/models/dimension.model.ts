export interface Dimension {
  id?: string;
  name: string;
  key: string;
  type: DimensionType;
  description?: string;
  parentId?: string | null;
  children?: Dimension[];
  order?: number;
  metadata?: { [key: string]: unknown };
  createdAt?: string;
  updatedAt?: string;
}

export enum DimensionType {
  DIMENSION = 'DIMENSION',
  HIERARCHY = 'HIERARCHY',
  ATTRIBUTE = 'ATTRIBUTE'
}

export interface DimensionNode extends Dimension {
  expandable: boolean;
  level: number;
  isExpanded?: boolean;
}

export interface DimensionTreeNode {
  data: Dimension;
  children?: DimensionTreeNode[];
  expanded?: boolean;
  level?: number;
}

export interface DimensionCreateRequest {
  name: string;
  key: string;
  type: DimensionType;
  description?: string;
  parentId?: string | null;
  metadata?: { [key: string]: unknown };
}

export interface HierarchyReorderRequest {
  dimensionId: string;
  newParentId: string | null;
  newOrder: number;
}
