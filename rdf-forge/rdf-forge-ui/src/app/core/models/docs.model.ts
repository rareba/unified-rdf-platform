/**
 * Matches backend {@code io.rdfforge.shacl.docs.SemanticApiDoc}.
 */
export interface SemanticApiDoc {
  projectId: string;
  projectName: string;
  generatedAt: string;
  ontologySummary: OntologyDocSummary;
  mappingSummary: MappingDocSummary;
  endpoints: DocEndpointInfo[];
  exampleQueries: ExampleQuery[];
}

export interface OntologyDocSummary {
  ontologyCount: number;
  classCount: number;
  propertyCount: number;
  skosConceptCount: number;
  namespaces: NamespaceBinding[];
  ontologies: OntologyDocEntry[];
}

export interface OntologyDocEntry {
  id: string;
  name: string;
  namespace: string;
  prefix?: string;
  format: string;
  version: number;
}

export interface NamespaceBinding {
  prefix: string;
  uri: string;
}

export interface MappingDocSummary {
  mappingCount: number;
  sourceTypes: string[];
  sampleTriples: string[];
  mappings: MappingDocEntry[];
}

export interface MappingDocEntry {
  id: string;
  name: string;
  sourceType: string;
  targetNamespace?: string;
  version: number;
}

export interface DocEndpointInfo {
  kind: string;
  sparqlEndpoint: string;
  publishedGraph: string;
  metadata: Record<string, string>;
}

export interface ExampleQuery {
  title: string;
  description?: string;
  sparql: string;
}

export type ApiDocFormat = 'HTML' | 'JSON';
