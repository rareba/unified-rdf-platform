import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatchCandidate } from '../../core/models/reconciliation.model';

@Component({
  selector: 'rdf-candidate-detail-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MatDividerModule],
  template: `
    <h2 mat-dialog-title>Match Candidate</h2>
    <mat-dialog-content>
      <dl class="cand">
        <dt>Source</dt>
        <dd class="uri">{{ data.sourceUri }}</dd>
        <dt>Target</dt>
        <dd class="uri">{{ data.targetUri }}</dd>
        <dt>Predicate</dt>
        <dd>{{ data.predicate }}</dd>
        <dt>Confidence</dt>
        <dd>{{ (data.confidence * 100) | number:'1.0-1' }}%</dd>
        <dt>Matcher</dt>
        <dd>{{ data.matcherName }} ({{ data.source }})</dd>
        <dt>Status</dt>
        <dd>{{ data.status }}</dd>
        <dt>Created</dt>
        <dd>{{ data.createdAt | date:'medium' }}</dd>
        @if (data.decidedAt) {
          <dt>Decided</dt>
          <dd>{{ data.decidedAt | date:'medium' }}</dd>
        }
      </dl>

      <h3>Evidence</h3>
      @if (data.evidence) {
        <pre class="evidence">{{ data.evidence | json }}</pre>
      } @else {
        <p>No evidence recorded.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .cand { display: grid; grid-template-columns: 130px 1fr; gap: 4px 12px; margin-bottom: 10px; }
    .cand dt { font-weight: 500; color: rgba(0,0,0,.6); }
    .cand dd { margin: 0; }
    .uri { font-family: ui-monospace, monospace; font-size: 11px; word-break: break-all; }
    .evidence { background: rgba(0,0,0,.03); padding: 10px; border-radius: 4px; font-size: 11px; max-height: 220px; overflow: auto; }
  `]
})
export class CandidateDetailDialog {
  constructor(
    public ref: MatDialogRef<CandidateDetailDialog>,
    @Inject(MAT_DIALOG_DATA) public data: MatchCandidate
  ) {}
}
