/**
 * Canonical plugin kinds exposed by the Extension Catalog.
 * Matches {@code io.rdfforge.common.extensions.ExtensionKind} on the backend.
 */
export type ExtensionKind =
  | 'OPERATION'
  | 'FORMAT'
  | 'STORAGE_PROVIDER'
  | 'DESTINATION'
  | 'TRIPLESTORE_PROVIDER'
  | 'MATCHER'
  | 'VALIDATOR'
  | 'CUBE_PROFILE';

/**
 * Unified descriptor used by every extension registry endpoint.
 * Matches {@code io.rdfforge.common.extensions.ExtensionDescriptor}.
 */
export interface ExtensionDescriptor {
  id: string;
  kind: ExtensionKind;
  name: string;
  version: string;
  description: string;
  capabilities: string[];
  parameters: Record<string, string>;
  providedBy: string;
  docUrl?: string | null;
  available: boolean;
}

/** Ordered list of kinds used by the catalog UI tabs. */
export const EXTENSION_KINDS: ExtensionKind[] = [
  'OPERATION',
  'FORMAT',
  'STORAGE_PROVIDER',
  'DESTINATION',
  'TRIPLESTORE_PROVIDER',
  'VALIDATOR',
  'CUBE_PROFILE',
  'MATCHER'
];

/** Human-friendly labels for the extension kinds. */
export const EXTENSION_KIND_LABELS: Record<ExtensionKind, string> = {
  OPERATION: 'Operations',
  FORMAT: 'Data Formats',
  STORAGE_PROVIDER: 'Storage Providers',
  DESTINATION: 'Destinations',
  TRIPLESTORE_PROVIDER: 'Triplestore Providers',
  MATCHER: 'Matchers',
  VALIDATOR: 'Validators',
  CUBE_PROFILE: 'Cube Profiles'
};

/** Summary payload from the meta-endpoint. */
export interface ExtensionSummary {
  [kind: string]: number;
}
