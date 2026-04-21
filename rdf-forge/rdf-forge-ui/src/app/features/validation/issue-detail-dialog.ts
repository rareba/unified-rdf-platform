import { ChangeDetectionStrategy, Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { ValidationIssue } from '../../core/models/validation.model';

interface DialogData {
  issue: ValidationIssue;
  projectId: string | null;
}

/**
 * Modal presentation of a {@link ValidationIssue} with drill-down links.
 *
 * Drill-down rules (documented in the component template):
 *  1. If {@code sourcePath} matches {@code mapping:<UUID>/rule:<ruleId>}
 *     we navigate to the mapping editor with the rule highlighted.
 *  2. Otherwise the user can open a "View in SPARQL console" link that
 *     pre-fills a DESCRIBE query for the resource URI.
 */
@Component({
  selector: 'rdf-issue-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      <span class="sev sev-{{ data.issue.severity }}">{{ data.issue.severity }}</span>
      Issue details
    </h2>
    <mat-dialog-content class="content">
      <dl>
        <dt>Message</dt>
        <dd>{{ data.issue.message ?? '—' }}</dd>
        <dt>Resource URI</dt>
        <dd class="mono">{{ data.issue.resourceUri ?? '—' }}</dd>
        <dt>Source path</dt>
        <dd class="mono">{{ data.issue.sourcePath ?? '—' }}</dd>
        <dt>Rule id</dt>
        <dd class="mono">{{ data.issue.ruleId ?? '—' }}</dd>
      </dl>

      @if (data.issue.details && (data.issue.details | json) !== '{}') {
        <h4>Details</h4>
        <pre class="mono">{{ data.issue.details | json }}</pre>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (mappingTarget(); as t) {
        <button mat-stroked-button color="primary"
                (click)="openMapping(t.mappingId, t.ruleId)">
          <mat-icon>route</mat-icon>&nbsp;Go to mapping rule
        </button>
      }
      @if (data.issue.resourceUri) {
        <button mat-stroked-button (click)="openInSparqlConsole()">
          <mat-icon>terminal</mat-icon>&nbsp;View in SPARQL console
        </button>
      }
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .content { max-width: 720px; }
    dl { display: grid; grid-template-columns: 120px 1fr; gap: 4px 12px; margin: 0; }
    dt { font-weight: 600; color: rgba(0,0,0,0.6); }
    dd { margin: 0; word-break: break-all; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
    pre.mono { background: rgba(0,0,0,0.04); padding: 8px; border-radius: 4px; overflow: auto; }
    .sev { padding: 2px 8px; border-radius: 12px; font-size: 11px; margin-right: 8px; }
    .sev-INFO    { background: #e3f2fd; color: #0d47a1; }
    .sev-WARNING { background: #fff8e1; color: #e65100; }
    .sev-ERROR   { background: #ffebee; color: #b71c1c; }
    .sev-FATAL   { background: #263238; color: #fff; }
  `]
})
export class IssueDetailDialog {
  private readonly router = inject(Router);
  private readonly dialogRef = inject(MatDialogRef<IssueDetailDialog>);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: DialogData) {}

  /**
   * Parse {@code sourcePath} of the form {@code mapping:<UUID>/rule:<ruleId>}
   * and return the two ids, or null if the format does not match.
   */
  mappingTarget(): { mappingId: string; ruleId: string } | null {
    const sp = this.data.issue.sourcePath;
    if (!sp) return null;
    const m = sp.match(/^mapping:([0-9a-fA-F-]{36})\/rule:(.+)$/);
    if (!m) return null;
    return { mappingId: m[1], ruleId: m[2] };
  }

  openMapping(mappingId: string, ruleId: string): void {
    if (!this.data.projectId) return;
    this.dialogRef.close();
    this.router.navigate(
      ['/projects', this.data.projectId, 'mapping', mappingId],
      { queryParams: { highlightRule: ruleId } }
    );
  }

  openInSparqlConsole(): void {
    const uri = this.data.issue.resourceUri;
    if (!uri) return;
    this.dialogRef.close();
    this.router.navigate(['/triplestore'], {
      queryParams: { query: `DESCRIBE <${uri}>` }
    });
  }
}
