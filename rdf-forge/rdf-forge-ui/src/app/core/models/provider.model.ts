/**
 * Configuration field for providers
 */
export interface ConfigField {
  name: string;
  displayName: string;
  type: 'string' | 'number' | 'boolean' | 'select' | 'password' | 'textarea';
  description: string;
  required: boolean;
  defaultValue?: unknown;
  allowedValues?: unknown[];
  sensitive?: boolean;
}

/**
 * Triplestore provider information
 */
export interface TriplestoreProviderInfo {
  type: string;
  displayName: string;
  description: string;
  vendor: string;
  documentationUrl: string;
  configFields: Record<string, ConfigField>;
  capabilities: string[];
  defaultPort?: number;
}

/**
 * Data format handler information
 */
export interface DataFormatInfo {
  format: string;
  displayName: string;
  description: string;
  extensions: string[];
  mimeTypes: string[];
  options: Record<string, ConfigField>;
  capabilities: string[];
}

/**
 * Storage provider information
 */
export interface StorageProviderInfo {
  type: string;
  displayName: string;
  description: string;
  vendor: string;
  documentationUrl: string;
  configFields: Record<string, ConfigField>;
  capabilities: string[];
}

/**
 * Destination provider information
 */
export interface DestinationInfo {
  type: string;
  displayName: string;
  description: string;
  category: 'triplestore' | 'file' | 'cloud-storage' | 'api';
  configFields: Record<string, ConfigField>;
  capabilities: string[];
  supportedFormats: string[];
}

/**
 * Validation result from provider
 */
export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Availability check result
 */
export interface AvailabilityResult {
  type: string;
  available: boolean;
  message: string;
}
