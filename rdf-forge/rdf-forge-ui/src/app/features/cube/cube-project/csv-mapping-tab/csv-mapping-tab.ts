import {
  Component,
  input,
  output,
  signal,
  inject,
  ChangeDetectionStrategy,
  OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { Cube, ColumnMapping, CsvColumnPreview } from '../../../../core/models/cube.model';
import { CubeService } from '../../../../core/services/cube.service';
import { DataService } from '../../../../core/services/data.service';

import { CsvSourcePanel } from './csv-source-panel';
import { ColumnMappingEditor } from './column-mapping-editor';
import { OutputTablePanel } from './output-table-panel';

@Component({
  selector: 'app-csv-mapping-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatSnackBarModule,
    MatProgressBarModule,
    CsvSourcePanel,
    ColumnMappingEditor,
    OutputTablePanel
  ],
  template: `
    <!-- Upload progress bar -->
    @if (uploading()) {
      <mat-progress-bar mode="indeterminate" class="upload-progress"></mat-progress-bar>
    }

    <div class="mapping-layout">

      <!-- Left panel: CSV source -->
      <div class="panel panel-left">
        <app-csv-source-panel
          [cube]="cube()"
          (fileUploaded)="onFileUploaded($event)"
          (columnsSelected)="onColumnsSelected($event)">
        </app-csv-source-panel>
      </div>

      <div class="panel-divider"></div>

      <!-- Right panel: Output mappings -->
      <div class="panel panel-right">
        <app-output-table-panel
          [mappings]="currentMappings()"
          (editMapping)="startEditing($event)"
          (deleteMapping)="onDeleteMapping($event)">
        </app-output-table-panel>
      </div>

    </div>

    <!-- Column mapping editor slide-over -->
    <app-column-mapping-editor
      [mapping]="editingMapping()"
      (save)="onMappingSaved($event)"
      (cancel)="stopEditing()">
    </app-column-mapping-editor>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }

    .upload-progress {
      flex-shrink: 0;
    }

    .mapping-layout {
      display: flex;
      flex: 1;
      overflow: hidden;
    }

    .panel {
      flex: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .panel-divider {
      width: 1px;
      background: var(--mat-divider-color, rgba(0,0,0,.12));
      flex-shrink: 0;
    }
  `]
})
export class CsvMappingTab implements OnDestroy {
  private readonly cubeService = inject(CubeService);
  private readonly dataService = inject(DataService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  readonly cube = input.required<Cube>();
  readonly cubeUpdated = output<Cube>();

  readonly editingMapping = signal<ColumnMapping | null>(null);
  readonly uploading = signal(false);

  /** Returns the current column mappings from the cube's metadata. */
  currentMappings(): ColumnMapping[] {
    return this.cube().metadata?.columnMappings ?? [];
  }

  // ===== File upload =====

  onFileUploaded(file: File): void {
    this.uploading.set(true);

    this.dataService
      .upload(file)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: dataSource => {
          this.uploading.set(false);

          // Derive a CsvPreview from analyze data for display
          const csvPreview = {
            fileName: file.name,
            rowCount: 0,
            columns: [] as CsvColumnPreview[]
          };

          const updatedCube: Partial<Cube> = {
            sourceDataId: dataSource.id,
            metadata: {
              ...this.cube().metadata,
              csvPreview
            }
          };

          this.persistUpdate(updatedCube);

          // Trigger analyze to fill in column details
          this.analyzeDataSource(dataSource.id, file.name);
        },
        error: err => {
          this.uploading.set(false);
          console.error('Upload failed', err);
          this.snackBar.open('Upload failed. Please try again.', 'Dismiss', { duration: 4000 });
        }
      });
  }

  private analyzeDataSource(dataSourceId: string, fileName: string): void {
    this.dataService
      .analyze(dataSourceId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          const columns: CsvColumnPreview[] = result.columns.map(c => ({
            name: c.name,
            sampleValues: (c as { sampleValues?: string[] }).sampleValues ?? [],
            mapped: false
          }));

          const csvPreview = {
            fileName,
            rowCount: result.rowCount,
            columns
          };

          this.persistUpdate({
            metadata: {
              ...this.cube().metadata,
              csvPreview
            }
          });
        },
        error: err => {
          console.error('Analyze failed', err);
        }
      });
  }

  // ===== Column selection → create initial mappings =====

  onColumnsSelected(columnNames: string[]): void {
    const existing = this.currentMappings();
    const existingNames = new Set(existing.map(m => m.name));

    const newMappings: ColumnMapping[] = columnNames
      .filter(name => !existingNames.has(name))
      .map(name => ({
        name,
        role: 'attribute' as const,
        datatype: 'xsd:string',
        predicateUri: this.generatePredicateUri(name)
      }));

    if (newMappings.length === 0) {
      this.snackBar.open('All selected columns are already mapped.', 'Dismiss', { duration: 3000 });
      return;
    }

    const merged = [...existing, ...newMappings];
    this.saveMappings(merged);
  }

  // ===== Editing =====

  startEditing(mapping: ColumnMapping): void {
    this.editingMapping.set(mapping);
  }

  stopEditing(): void {
    this.editingMapping.set(null);
  }

  onMappingSaved(updated: ColumnMapping): void {
    const mappings = this.currentMappings().map(m =>
      m.name === updated.name ? updated : m
    );
    // Mark mapped status on csvPreview columns
    this.saveMappings(mappings, () => this.stopEditing());
  }

  // ===== Delete =====

  onDeleteMapping(mapping: ColumnMapping): void {
    const mappings = this.currentMappings().filter(m => m.name !== mapping.name);
    this.saveMappings(mappings);
  }

  // ===== Persistence =====

  private saveMappings(mappings: ColumnMapping[], onSuccess?: () => void): void {
    this.persistUpdate(
      { metadata: { ...this.cube().metadata, columnMappings: mappings } },
      onSuccess
    );
  }

  private persistUpdate(partial: Partial<Cube>, onSuccess?: () => void): void {
    this.cubeService
      .update(this.cube().id, partial)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.cubeUpdated.emit(updated);
          onSuccess?.();
        },
        error: err => {
          console.error('Failed to save cube', err);
          this.snackBar.open('Failed to save changes. Please try again.', 'Dismiss', { duration: 4000 });
        }
      });
  }

  // ===== Helpers =====

  private generatePredicateUri(columnName: string): string {
    const slug = columnName
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '');
    return `https://example.org/property/${slug}`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
