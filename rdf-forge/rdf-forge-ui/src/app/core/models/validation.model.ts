/**
 * Phase 5 — Validation Cockpit models. Mirrors the backend DTOs
 * produced by rdf-forge-shacl-service under /api/v1/validation.
 */

export type ValidationSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'FATAL';

export type ValidationStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'ERRORED';

export type SuiteRuleType =
  | 'SHACL_SHAPE'
  | 'SPARQL_ASK'
  | 'SPARQL_SELECT'
  | 'CUBE_PROFILE';

export type ReleaseGate =
  | 'DISABLED'
  | 'WARN_ONLY'
  | 'FAIL_ON_WARNING'
  | 'FAIL_ON_ERROR'
  | 'FAIL_ON_FATAL';

export interface SuiteRule {
  id: string;
  name: string;
  type: SuiteRuleType;
  /**
   * For SHACL_SHAPE → UUID of a Shape entity (as string).
   * For SPARQL_ASK / SPARQL_SELECT → the inline query text.
   * For CUBE_PROFILE → the profile id (e.g. "standalone-cube-constraint").
   */
  resourceRef: string;
  severity: ValidationSeverity;
}

export interface ValidationSuite {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  rules: SuiteRule[];
  gate: ReleaseGate;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ValidationSuiteCreateRequest {
  projectId: string;
  name: string;
  description?: string;
  rules: SuiteRule[];
  gate: ReleaseGate;
}

export interface ValidationSuiteUpdateRequest {
  name: string;
  description?: string;
  rules: SuiteRule[];
  gate: ReleaseGate;
}

export interface ValidationRunRequest {
  targetGraph?: string;
  targetTriplestoreId?: string;
  triggeredBy?: 'manual' | 'release' | 'ci';
}

export interface ValidationRun {
  id: string;
  suiteId: string;
  projectId: string;
  ranAt: string;
  durationMs: number;
  status: ValidationStatus;
  issueCount: number;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  fatalCount: number;
  summary?: string;
  context?: Record<string, unknown>;
  ranBy?: string;
}

export interface ValidationIssue {
  id: string;
  runId: string;
  ruleId?: string;
  severity: ValidationSeverity;
  resourceUri?: string;
  message?: string;
  sourcePath?: string;
  details?: Record<string, unknown>;
}

/**
 * Aggregate health derived from the latest run across every suite in a
 * project. Computed client-side in the cockpit and on the overview widget.
 */
export interface ProjectValidationHealth {
  /** "green" = all suites passed; "amber" = warnings only; "red" = at least one failed/errored. */
  status: 'green' | 'amber' | 'red' | 'unknown';
  suiteCount: number;
  passedCount: number;
  warningCount: number;
  failedCount: number;
}
