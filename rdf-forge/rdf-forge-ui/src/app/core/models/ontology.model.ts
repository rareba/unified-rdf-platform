export type RdfFormat =
  | 'TURTLE'
  | 'RDF_XML'
  | 'JSON_LD'
  | 'N_TRIPLES'
  | 'N_QUADS'
  | 'TRIG';

export interface Ontology {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  namespace: string;
  prefix?: string;
  format: RdfFormat;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface OntologyNamespace {
  prefix: string;
  uri: string;
}

export interface NamespaceMap {
  entries: OntologyNamespace[];
}

export interface OntologyImportRequest {
  projectId: string;
  name: string;
  description?: string;
  format: RdfFormat;
  content: string;
  namespace?: string;
  prefix?: string;
}

export interface OntologyUpdateRequest {
  name?: string;
  description?: string;
  namespace?: string;
  prefix?: string;
}

export interface OntologyContentUpdateRequest {
  content: string;
  format: RdfFormat;
}

export interface OntologyContent {
  id: string;
  name: string;
  format: RdfFormat;
  content: string;
}

export interface TermResult {
  uri: string;
  type: string;
  label?: string;
  comment?: string;
  altLabels?: string[];
  broader?: string[];
  narrower?: string[];
}

export interface TermDetail extends TermResult {
  types?: string[];
  domain?: string[];
  range?: string[];
  exactMatch?: string[];
  closeMatch?: string[];
}

export interface OntologyValidationResult {
  valid: boolean;
  errors: string[];
  tripleCount: number;
}

export type TermKind = 'classes' | 'properties' | 'skos-concepts';

export const RDF_FORMAT_OPTIONS: { value: RdfFormat; label: string; ext: string }[] = [
  { value: 'TURTLE', label: 'Turtle (.ttl)', ext: 'ttl' },
  { value: 'RDF_XML', label: 'RDF/XML (.rdf)', ext: 'rdf' },
  { value: 'JSON_LD', label: 'JSON-LD (.jsonld)', ext: 'jsonld' },
  { value: 'N_TRIPLES', label: 'N-Triples (.nt)', ext: 'nt' },
  { value: 'N_QUADS', label: 'N-Quads (.nq)', ext: 'nq' },
  { value: 'TRIG', label: 'TriG (.trig)', ext: 'trig' }
];
