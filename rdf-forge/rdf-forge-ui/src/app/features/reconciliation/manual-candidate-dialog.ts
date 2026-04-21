import { ChangeDetectionStrategy, Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ReconciliationService } from '../../core/services/reconciliation.service';
import { MatchPredicate } from '../../core/models/reconciliation.model';

interface Data {
  projectId: string;
}

@Component({
  selector: 'rdf-manual-candidate-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule
  ],
  template: `
    <h2 mat-dialog-title>Create Manual Match</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Source URI</mat-label>
        <input matInput [(ngModel)]="sourceUri" />
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Target URI</mat-label>
        <input matInput [(ngModel)]="targetUri" />
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Predicate</mat-label>
        <mat-select [(value)]="predicate">
          <mat-option value="SAME_AS">owl:sameAs</mat-option>
          <mat-option value="EXACT_MATCH">skos:exactMatch</mat-option>
          <mat-option value="CLOSE_MATCH">skos:closeMatch</mat-option>
          <mat-option value="RELATED_MATCH">skos:relatedMatch</mat-option>
          <mat-option value="BROADER">skos:broadMatch</mat-option>
          <mat-option value="NARROWER">skos:narrowMatch</mat-option>
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Note (optional)</mat-label>
        <textarea matInput rows="2" [(ngModel)]="note"></textarea>
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(false)">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="!canSave() || saving">
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full { width: 100%; display: block; }
  `]
})
export class ManualCandidateDialog {
  private readonly service = inject(ReconciliationService);
  private readonly snackBar = inject(MatSnackBar);

  sourceUri = '';
  targetUri = '';
  predicate: MatchPredicate = 'SAME_AS';
  note = '';
  saving = false;

  constructor(
    public ref: MatDialogRef<ManualCandidateDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Data
  ) {}

  canSave(): boolean {
    return !!this.sourceUri && !!this.targetUri;
  }

  save(): void {
    if (!this.canSave() || this.saving) return;
    this.saving = true;
    this.service.manual({
      projectId: this.data.projectId,
      sourceUri: this.sourceUri,
      targetUri: this.targetUri,
      predicate: this.predicate,
      evidence: this.note ? { note: this.note } : undefined
    }).subscribe({
      next: () => {
        this.snackBar.open('Manual match created', 'OK', { duration: 2000 });
        this.ref.close(true);
      },
      error: (err) => {
        this.snackBar.open('Failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 });
        this.saving = false;
      }
    });
  }
}
