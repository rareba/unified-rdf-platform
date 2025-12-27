export type TriplestoreType = 'FUSEKI' | 'STARDOG' | 'GRAPHDB' | 'NEPTUNE' | 'VIRTUOSO' | 'BLAZEGRAPH';
export type AuthType = 'none' | 'basic' | 'apikey' | 'oauth2';
export type HealthStatus = 'healthy' | 'unhealthy' | 'unknown';
export type RdfFormat = 'turtle' | 'rdfxml' | 'ntriples' | 'jsonld';

export interface TriplestoreConnection {
  id: string;
  name: string;
  type: TriplestoreType;
  url: string;
  defaultGraph?: string;
  authType: AuthType;
  isDefault: boolean;
  healthStatus: HealthStatus;
  lastHealthCheck?: Date;
  createdBy: string;
  createdAt: Date;
}

export interface ConnectionCreateRequest {
  name: string;
  type: TriplestoreType;
  url: string;
  defaultGraph?: string;
  authType: AuthType;
  authConfig?: {
    username?: string;
    password?: string;
    apiKey?: string;
    repository?: string;
  };
  isDefault?: boolean;
}

export interface Graph {
  uri: string;
  name?: string;
  tripleCount: number;
  lastModified?: Date;
}

export interface Resource {
  uri: string;
  type?: string;
  label?: string;
  properties: PropertyValue[];
  types: string[];
}

export interface PropertyValue {
  predicate: string;
  object: string;
  objectType: 'uri' | 'literal';
  datatype?: string;
  language?: string;
}

export interface QueryResult {
  variables: string[];
  bindings: Record<string, { type: string; value: string; datatype?: string; language?: string }>[];
  executionTime: number;
}

export interface ConnectionTestResult {
  success: boolean;
  message?: string;
  latencyMs?: number;
}
