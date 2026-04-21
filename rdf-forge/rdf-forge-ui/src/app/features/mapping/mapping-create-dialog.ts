import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MappingService } from '../../core/services/mapping.service';
import { Mapping, MappingType, SourceType } from '../../core/models/mapping.model';

interface CreateDialogData {
  projectId: string;
}

/**
 * Minimal "New Mapping" dialog. Collects name, source type, mapping type and
 * optional description; everything else (rules, ontologies, source config)
 * is authored inside the Studio after creation.
 */
@Component({
  selector: 'app-mapping-create-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>New Mapping</h2>
    <mat-dialog-content>
      <form class="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput [(ngModel)]="name" name="name" required maxlength="255">
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Source type</mat-label>
          <mat-select [(ngModel)]="sourceType" name="sourceType">
            <mat-option value="CSV">CSV</mat-option>
            <mat-option value="TSV">TSV</mat-option>
            <mat-option value="JSON">JSON</mat-option>
            <mat-option value="XML">XML</mat-option>
            <mat-option value="XLSX">XLSX</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Mapping type</mat-label>
          <mat-select [(ngModel)]="mappingType" name="mappingType">
            <mat-option value="GENERIC">Generic</mat-option>
            <mat-option value="CUBE">Cube (qb:Observation template)</mat-option>
            <mat-option value="SKOS">SKOS</mat-option>
            <mat-option value="CUSTOM">Custom</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput [(ngModel)]="description" name="description" rows="2"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Target namespace (base IRI)</mat-label>
          <input matInput [(ngModel)]="targetNamespace" name="targetNamespace"
                 placeholder="https://example.org/myproj/">
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-raised-button color="primary"
              [disabled]="!name || saving"
              (click)="save()">
        {{ saving ? 'Creating…' : 'Create' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form { display: flex; flex-direction: column; gap: 12px; min-width: 440px; }
    .full-width { width: 100%; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MappingCreateDialog {
  private readonly svc = inject(MappingService);
  private readonly ref = inject(MatDialogRef<MappingCreateDialog>);
  private readonly data = inject<CreateDialogData>(MAT_DIALOG_DATA);
  private readonly snack = inject(MatSnackBar);

  name = '';
  description = '';
  sourceType: SourceType = 'CSV';
  mappingType: MappingType = 'GENERIC';
  targetNamespace = '';
  saving = false;

  save(): void {
    if (!this.name) return;
    this.saving = true;
    this.svc.create({
      projectId: this.data.projectId,
      name: this.name.trim(),
      description: this.description || undefined,
      sourceType: this.sourceType,
      mappingType: this.mappingType,
      targetNamespace: this.targetNamespace || undefined,
      rules: []
    }).subscribe({
      next: (m: Mapping) => { this.saving = false; this.ref.close(m); },
      error: err => {
        this.saving = false;
        this.snack.open('Create failed: ' + (err?.error?.detail ?? err?.message ?? err),
          'Dismiss', { duration: 6000 });
      }
    });
  }

  cancel(): void { this.ref.close(); }
}
