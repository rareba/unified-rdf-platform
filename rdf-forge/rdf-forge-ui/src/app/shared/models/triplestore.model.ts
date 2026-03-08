export interface SparqlQuery {
  query: string;
  queryType?: 'SELECT' | 'ASK' | 'CONSTRUCT' | 'DESCRIBE';
  dataset?: string;
}

export interface SparqlResult {
  head: {
    vars: string[];
    link?: string[];
  };
  results: {
    bindings: SparqlBinding[];
  };
  boolean?: boolean;
}

export interface SparqlBinding {
  [key: string]: {
    type: 'uri' | 'literal' | 'bnode';
    value: string;
    datatype?: string;
    'xml:lang'?: string;
  };
}

export interface SparqlHistoryEntry {
  id: string;
  query: string;
  queryType: string;
  timestamp: Date;
  executionTimeMs?: number;
  resultCount?: number;
  success: boolean;
}

export interface TriplestoreInfo {
  name: string;
  version: string;
  readOnly: boolean;
  defaultDataset: string;
  availableDatasets: string[];
}
