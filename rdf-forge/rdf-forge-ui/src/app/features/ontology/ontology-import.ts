import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { OntologyService } from '../../core/services/ontology.service';
import { Ontology, OntologyImportRequest, RDF_FORMAT_OPTIONS, RdfFormat } from '../../core/models';

interface ImportDialogData {
  projectId: string;
}

/**
 * Dialog that collects ontology metadata + raw content and calls the import
 * endpoint. Accepts either a file upload or a pasted textarea, and infers the
 * serialization format from the file extension when possible.
 */
@Component({
  selector: 'app-ontology-import',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTabsModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>upload_file</mat-icon>
      Import Ontology
    </h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="content">

        <mat-form-field appearance="outline" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="flex">
            <mat-label>Base namespace</mat-label>
            <input matInput formControlName="namespace" placeholder="http://example.org/schema/">
          </mat-form-field>

          <mat-form-field appearance="outline" class="prefix">
            <mat-label>Prefix</mat-label>
            <input matInput formControlName="prefix" placeholder="ex">
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Format</mat-label>
          <mat-select formControlName="format">
            @for (opt of formats; track opt.value) {
              <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-tab-group>
          <mat-tab label="Upload file">
            <div class="tab-body">
              <button mat-stroked-button type="button" (click)="fileInput.click()">
                <mat-icon>attach_file</mat-icon>
                Choose file
              </button>
              <input #fileInput type="file" hidden
                     accept=".ttl,.rdf,.xml,.jsonld,.json,.nt,.nq,.trig"
                     (change)="onFile($event)">
              @if (fileName()) {
                <div class="file-info">
                  <mat-icon>insert_drive_file</mat-icon>
                  <span>{{ fileName() }}</span>
                </div>
              }
            </div>
          </mat-tab>
          <mat-tab label="Paste content">
            <div class="tab-body">
              <mat-form-field appearance="outline" class="full">
                <mat-label>RDF content</mat-label>
                <textarea matInput rows="10" formControlName="content"
                          placeholder="@prefix ex: <http://example.org/> ."></textarea>
              </mat-form-field>
            </div>
          </mat-tab>
        </mat-tab-group>

        @if (errorMessage()) {
          <div class="error">
            <mat-icon>error</mat-icon>
            <span>{{ errorMessage() }}</span>
          </div>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" [disabled]="busy()" (click)="cancel()">Cancel</button>
        <button mat-raised-button color="primary" type="submit"
                [disabled]="busy() || form.invalid || !hasContent()">
          @if (busy()) {
            <mat-progress-spinner mode="indeterminate" diameter="20"></mat-progress-spinner>
          } @else {
            <ng-container>Import</ng-container>
          }
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .content {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-width: 480px;
    }
    .full { width: 100%; }
    .row {
      display: flex;
      gap: 12px;
      align-items: baseline;
    }
    .flex { flex: 1 1 auto; }
    .prefix { width: 120px; }
    .tab-body {
      padding: 12px 4px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .file-info {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--rdf-text-secondary);
    }
    .error {
      display: flex;
      gap: 8px;
      color: #b91c1c;
      align-items: center;
      padding: 8px 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OntologyImport {
  private readonly dialogRef = inject<MatDialogRef<OntologyImport, Ontology | null>>(MatDialogRef);
  private readonly data = inject<ImportDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OntologyService);

  readonly formats = RDF_FORMAT_OPTIONS;
  readonly busy = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fileName = signal<string | null>(null);

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(1)]],
    description: [''],
    namespace: [''],
    prefix: [''],
    format: ['TURTLE' as RdfFormat, [Validators.required]],
    content: ['']
  });

  hasContent(): boolean {
    const c = this.form.value.content;
    return typeof c === 'string' && c.trim().length > 0;
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.fileName.set(file.name);
    const format = this.guessFormat(file.name);
    if (format) {
      this.form.patchValue({ format });
    }
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      this.form.patchValue({ content: text });
    };
    reader.onerror = () => this.errorMessage.set('Failed to read file');
    reader.readAsText(file);
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  submit(): void {
    if (this.form.invalid || !this.hasContent()) return;
    this.busy.set(true);
    this.errorMessage.set(null);
    const value = this.form.value;
    const req: OntologyImportRequest = {
      projectId: this.data.projectId,
      name: value.name,
      description: value.description || undefined,
      namespace: value.namespace || undefined,
      prefix: value.prefix || undefined,
      format: value.format,
      content: value.content
    };
    this.service.import(req).subscribe({
      next: ont => {
        this.busy.set(false);
        this.dialogRef.close(ont);
      },
      error: err => {
        this.busy.set(false);
        this.errorMessage.set(err?.error?.detail ?? err?.message ?? 'Import failed');
      }
    });
  }

  private guessFormat(name: string): RdfFormat | null {
    const n = name.toLowerCase();
    if (n.endsWith('.ttl')) return 'TURTLE';
    if (n.endsWith('.rdf') || n.endsWith('.xml')) return 'RDF_XML';
    if (n.endsWith('.jsonld') || n.endsWith('.json')) return 'JSON_LD';
    if (n.endsWith('.nt')) return 'N_TRIPLES';
    if (n.endsWith('.nq')) return 'N_QUADS';
    if (n.endsWith('.trig')) return 'TRIG';
    return null;
  }
}
