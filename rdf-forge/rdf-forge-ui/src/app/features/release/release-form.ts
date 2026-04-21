import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReleaseService } from '../../core/services/release.service';
import { MappingService } from '../../core/services/mapping.service';
import { Release } from '../../core/models/release.model';
import { Mapping } from '../../core/models/mapping.model';

interface ReleaseFormData {
  projectId: string;
}

/**
 * Dialog form for creating a new {@link Release} draft.
 *
 * <p>Collects SemVer + name + notes and lets the user toggle which mappings
 * to include in the manifest. Cross-service assets (shapes, ontologies, data
 * sources, validation suites) are accepted as text IDs for v1 — a later pass
 * will replace those with proper multi-select dropdowns once the
 * ProjectService.summary carries the ID lists (Phase 6.1).
 */
@Component({
  selector: 'app-release-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>New Release</h2>
    <mat-dialog-content>
      <form class="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Version (SemVer)</mat-label>
          <input matInput [(ngModel)]="version" name="version" required
                 placeholder="1.0.0">
          <mat-hint>Must be SemVer: MAJOR.MINOR.PATCH (e.g. 1.0.0, 1.0.0-rc.1)</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput [(ngModel)]="name" name="name" required maxlength="255">
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Release notes</mat-label>
          <textarea matInput [(ngModel)]="notes" name="notes" rows="4"
                    placeholder="What changed in this release?"></textarea>
        </mat-form-field>

        <div class="assets">
          <div class="assets-header">
            <mat-icon>checklist</mat-icon>
            <span>Include in manifest</span>
          </div>
          @if (loadingMappings()) {
            <mat-spinner diameter="20"></mat-spinner>
          } @else if (mappings().length === 0) {
            <p class="hint">No mappings in this project yet. The release will still build with an empty manifest.</p>
          } @else {
            <p class="hint">Select mappings to include. The zip will contain a real JSON copy of each.</p>
            @for (m of mappings(); track m.id) {
              <mat-checkbox
                [checked]="selectedMappingIds().has(m.id)"
                (change)="toggleMapping(m.id, $event.checked)">
                {{ m.name }}
                <span class="muted">v{{ m.version }} · {{ m.mappingType }}</span>
              </mat-checkbox>
            }
          }
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Shape IDs (comma-separated, optional)</mat-label>
          <input matInput [(ngModel)]="shapesCsv" name="shapes"
                 placeholder="uuid,uuid">
          <mat-hint>Bundled as references — resolved by shacl-service in Phase 6.1</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Ontology IDs (comma-separated, optional)</mat-label>
          <input matInput [(ngModel)]="ontologiesCsv" name="ontologies"
                 placeholder="uuid,uuid">
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Validation suite IDs (comma-separated, optional)</mat-label>
          <input matInput [(ngModel)]="validationCsv" name="validation"
                 placeholder="uuid,uuid">
          <mat-hint>Gate mode defaults to WARN_ONLY in v1</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Triplestore ID (optional)</mat-label>
          <input matInput [(ngModel)]="triplestoreId" name="triplestore"
                 placeholder="uuid">
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()" [disabled]="saving()">Cancel</button>
      <button mat-raised-button color="primary"
              [disabled]="!version || !name || saving()"
              (click)="save()">
        {{ saving() ? 'Creating…' : 'Create Draft' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form { display: flex; flex-direction: column; gap: 12px; min-width: 480px; }
    .full-width { width: 100%; }
    .assets {
      display: flex; flex-direction: column; gap: 8px;
      padding: 12px; border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 4px;
    }
    .assets-header {
      display: flex; align-items: center; gap: 8px;
      font-weight: 500;
    }
    .hint { color: var(--rdf-text-secondary); font-size: 0.85rem; margin: 0; }
    .muted { color: var(--rdf-text-secondary); font-size: 0.85rem; margin-left: 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReleaseForm implements OnInit {
  private readonly releaseSvc = inject(ReleaseService);
  private readonly mappingSvc = inject(MappingService);
  private readonly ref = inject(MatDialogRef<ReleaseForm>);
  private readonly data = inject<ReleaseFormData>(MAT_DIALOG_DATA);
  private readonly snack = inject(MatSnackBar);

  version = '';
  name = '';
  notes = '';
  shapesCsv = '';
  ontologiesCsv = '';
  validationCsv = '';
  triplestoreId = '';

  readonly saving = signal(false);
  readonly loadingMappings = signal(false);
  readonly mappings = signal<Mapping[]>([]);
  readonly selectedMappingIds = signal<Set<string>>(new Set());

  ngOnInit(): void {
    this.loadingMappings.set(true);
    this.mappingSvc.listByProject(this.data.projectId).subscribe({
      next: list => {
        this.mappings.set(list);
        this.loadingMappings.set(false);
      },
      error: () => this.loadingMappings.set(false)
    });
  }

  toggleMapping(id: string, checked: boolean): void {
    const next = new Set(this.selectedMappingIds());
    if (checked) next.add(id); else next.delete(id);
    this.selectedMappingIds.set(next);
  }

  save(): void {
    if (!this.version || !this.name) return;
    this.saving.set(true);
    this.releaseSvc.create(this.data.projectId, {
      version: this.version.trim(),
      name: this.name.trim(),
      notes: this.notes || undefined,
      manifestRefs: {
        dataSources: [],
        mappings: Array.from(this.selectedMappingIds()),
        shapes: this.splitCsv(this.shapesCsv),
        ontologies: this.splitCsv(this.ontologiesCsv),
        validationSuiteIds: this.splitCsv(this.validationCsv),
        triplestoreId: this.triplestoreId.trim() || null
      }
    }).subscribe({
      next: (r: Release) => {
        this.saving.set(false);
        this.ref.close(r);
      },
      error: err => {
        this.saving.set(false);
        this.snack.open('Create failed: ' + (err?.error?.detail ?? err?.message ?? err),
          'Dismiss', { duration: 6000 });
      }
    });
  }

  cancel(): void { this.ref.close(); }

  private splitCsv(s: string): string[] {
    if (!s) return [];
    return s.split(',').map(x => x.trim()).filter(x => x.length > 0);
  }
}
