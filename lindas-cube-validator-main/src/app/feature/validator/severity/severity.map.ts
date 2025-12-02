import { sh } from '../../../core/rdf/namespace';

// shacl severity
// Severity	Description
// sh:Info	A non-critical constraint violation indicating an informative message.
// sh:Warning	A non-critical constraint violation indicating a warning.
// sh:Violation	A constraint violation.

export interface Severity {
  shaclSeverity: string;
  description: string;
  class: string;
}

/* oblique severity class
<mat-chip-option>Default</mat-chip-option>
    <mat-chip-option class="info">Info</mat-chip-option>
    <mat-chip-option class="success">Success</mat-chip-option>
    <mat-chip-option class="error">Error</mat-chip-option>
    <mat-chip-option class="warning">Warn</mat-chip-option>
  */

const _severityMap = new Map<string, Severity>();
_severityMap.set(sh['Info'].value, { shaclSeverity: 'sh:Info', description: 'Info', class: 'info' });
_severityMap.set(sh['Warning'].value, { shaclSeverity: 'sh:Warning', description: 'Warning', class: 'warning' });
_severityMap.set(sh['Violation'].value, { shaclSeverity: 'sh:Violation', description: 'Violation', class: 'error' });


export const ShaclToObliqueSeverityMap = _severityMap;