import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { OntologyService } from '../../../core/services/ontology.service';
import { Ontology, TermResult } from '../../../core/models';

export interface SkosImportDialogData {
  projectId: string;
}

export interface SkosImportResult {
  ontology: Ontology;
  selections: TermResult[];
}

/**
 * Two-step dialog:
 *   1. pick an ontology from the project
 *   2. pick one or many SKOS concepts
 *
 * Returns { ontology, selections } so the caller can persist or populate a
 * dimension form.
 */
@Component({
  selector: 'app-skos-import-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatCheckboxModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>bookmarks</mat-icon>
      Import SKOS Concepts
    </h2>
    <mat-dialog-content class="dialog">
      <mat-form-field appearance="outline" class="full">
        <mat-label>Ontology</mat-label>
        <mat-select [(value)]="selectedOntologyId" (selectionChange)="loadConcepts()">
          @for (o of ontologies(); track o.id) {
            <mat-option [value]="o.id">{{ o.name }} ({{ o.format }})</mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (ontologiesLoading()) {
        <div class="center">
          <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
        </div>
      } @else if (ontologies().length === 0) {
        <p class="empty">No ontologies in this project. Import one first.</p>
      }

      @if (selectedOntologyId) {
        <mat-form-field appearance="outline" class="full">
          <mat-label>Search concepts</mat-label>
          <input matInput [value]="searchText()"
                 (input)="onSearch($event)">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        @if (conceptsLoading()) {
          <div class="center">
            <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
          </div>
        } @else if (concepts().length === 0) {
          <p class="empty">No SKOS concepts in this ontology.</p>
        } @else {
          <div class="concept-list">
            @for (c of concepts(); track c.uri) {
              <div class="concept-row">
                <mat-checkbox
                    [checked]="isSelected(c)"
                    (change)="toggle(c, $event.checked)">
                  <div class="concept-main">
                    <div class="label">{{ c.label || shortUri(c.uri) }}</div>
                    <div class="sub">{{ c.uri }}</div>
                    @if (c.comment) {
                      <div class="sub">{{ c.comment }}</div>
                    }
                  </div>
                </mat-checkbox>
              </div>
            }
          </div>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="cancel()">Cancel</button>
      <button mat-raised-button color="primary" type="button"
              [disabled]="selectionCount() === 0"
              (click)="confirm()">
        Import {{ selectionCount() }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog {
      min-width: 480px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .full { width: 100%; }
    .center, .empty {
      display: flex;
      justify-content: center;
      padding: 16px;
      color: var(--rdf-text-secondary);
    }
    .concept-list {
      max-height: 320px;
      overflow-y: auto;
      border: 1px solid rgba(0,0,0,0.1);
      border-radius: 4px;
    }
    .concept-row {
      padding: 4px 8px;
      border-bottom: 1px solid rgba(0,0,0,0.05);
    }
    .concept-row:last-child { border-bottom: none; }
    .concept-main { display: inline-block; vertical-align: top; }
    .label { font-weight: 500; }
    .sub {
      font-size: 0.8em;
      color: var(--rdf-text-secondary);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SkosImportDialog {
  private readonly dialogRef = inject<MatDialogRef<SkosImportDialog, SkosImportResult | null>>(MatDialogRef);
  private readonly data = inject<SkosImportDialogData>(MAT_DIALOG_DATA);
  private readonly ontologyService = inject(OntologyService);

  readonly ontologies = signal<Ontology[]>([]);
  readonly ontologiesLoading = signal(false);
  readonly concepts = signal<TermResult[]>([]);
  readonly conceptsLoading = signal(false);
  readonly searchText = signal<string>('');
  readonly selectedByUri = signal<Map<string, TermResult>>(new Map());

  readonly selectionCount = computed(() => this.selectedByUri().size);

  selectedOntologyId: string | null = null;

  private debounceHandle: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.loadOntologies();
  }

  loadOntologies(): void {
    this.ontologiesLoading.set(true);
    this.ontologyService.list(this.data.projectId).subscribe({
      next: list => {
        this.ontologies.set(list ?? []);
        this.ontologiesLoading.set(false);
      },
      error: () => {
        this.ontologies.set([]);
        this.ontologiesLoading.set(false);
      }
    });
  }

  loadConcepts(): void {
    if (!this.selectedOntologyId) return;
    this.conceptsLoading.set(true);
    this.selectedByUri.set(new Map());
    this.ontologyService
      .skosConcepts(this.selectedOntologyId, this.searchText() || undefined, 200)
      .subscribe({
        next: list => {
          this.concepts.set(list ?? []);
          this.conceptsLoading.set(false);
        },
        error: () => {
          this.concepts.set([]);
          this.conceptsLoading.set(false);
        }
      });
  }

  onSearch(event: Event): void {
    const val = (event.target as HTMLInputElement).value ?? '';
    this.searchText.set(val);
    if (this.debounceHandle !== null) clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.loadConcepts(), 250);
  }

  isSelected(c: TermResult): boolean {
    return this.selectedByUri().has(c.uri);
  }

  toggle(c: TermResult, checked: boolean): void {
    const next = new Map(this.selectedByUri());
    if (checked) next.set(c.uri, c);
    else next.delete(c.uri);
    this.selectedByUri.set(next);
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  confirm(): void {
    const ontology = this.ontologies().find(o => o.id === this.selectedOntologyId);
    if (!ontology) return;
    const selections = Array.from(this.selectedByUri().values());
    if (selections.length === 0) return;
    this.dialogRef.close({ ontology, selections });
  }

  shortUri(uri: string): string {
    const hash = uri.lastIndexOf('#');
    if (hash >= 0) return uri.substring(hash + 1);
    const slash = uri.lastIndexOf('/');
    if (slash >= 0) return uri.substring(slash + 1);
    return uri;
  }
}
