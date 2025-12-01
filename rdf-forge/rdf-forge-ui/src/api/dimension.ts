import client from './client';

export interface Dimension {
  id?: string;
  projectId?: string;
  uri: string;
  name: string;
  description?: string;
  type: 'TEMPORAL' | 'GEO' | 'KEY' | 'MEASURE' | 'ATTRIBUTE' | 'CODED';
  hierarchyType?: 'SKOS' | 'CUSTOM' | 'FLAT';
  content?: string;
  baseUri?: string;
  metadata?: Record<string, any>;
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
  metadata?: Record<string, any>;
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
  hierarchyType: 'SKOS_CONCEPT_SCHEME' | 'SKOS_COLLECTION' | 'XKOS_CLASSIFICATION' | 'CUSTOM';
  maxDepth?: number;
  rootConceptUri?: string;
  isDefault?: boolean;
}

export default {
  list(params?: any) {
    return client.get('/dimensions', { params });
  },

  get(id: string) {
    return client.get(`/dimensions/${id}`);
  },

  create(data: Dimension) {
    return client.post('/dimensions', data);
  },

  update(id: string, data: Partial<Dimension>) {
    return client.put(`/dimensions/${id}`, data);
  },

  delete(id: string) {
    return client.delete(`/dimensions/${id}`);
  },

  getValues(id: string, params?: any) {
    return client.get(`/dimensions/${id}/values`, { params });
  },

  addValue(id: string, data: DimensionValue) {
    return client.post(`/dimensions/${id}/values`, data);
  },

  updateValue(valueId: string, data: Partial<DimensionValue>) {
    return client.put(`/dimensions/values/${valueId}`, data);
  },

  deleteValue(valueId: string) {
    return client.delete(`/dimensions/values/${valueId}`);
  },

  importCsv(id: string, file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post(`/dimensions/${id}/import/csv`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  exportTurtle(id: string) {
    return client.get(`/dimensions/${id}/export/turtle`, {
      responseType: 'blob',
    });
  },

  getTree(id: string) {
    return client.get(`/dimensions/${id}/tree`);
  },

  getStats(projectId: string) {
    return client.get('/dimensions/stats', { params: { projectId } });
  },
  
  // Hierarchy endpoints
  listHierarchies(dimensionId: string) {
    return client.get('/hierarchies', { params: { dimensionId } });
  },
  
  createHierarchy(data: Hierarchy) {
    return client.post('/hierarchies', data);
  },
  
  exportSkos(id: string, dimensionId: string) {
    return client.get(`/hierarchies/${id}/export/skos`, {
      params: { dimensionId },
      responseType: 'blob'
    });
  }
};
