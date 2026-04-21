import {
  Component,
  input,
  output,
  signal,
  computed,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { Cube, CsvColumnPreview } from '../../../../core/models/cube.model';

@Component({
  selector: 'app-csv-source-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatCheckboxModule,
    MatChipsModule,
    MatTooltipModule,
    MatBadgeModule
  ],
  template: `
    <div class="csv-source-panel">

      <!-- Upload area (shown when no CSV data yet) -->
      @if (!hasCsvData()) {
        <div
          class="upload-zone"
          [class.drag-over]="isDragging()"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave()"
          (drop)="onDrop($event)"
          (click)="fileInput.click()"
          role="button"
          tabindex="0"
          (keydown.enter)="fileInput.click()"
          aria-label="Upload CSV file">
          <mat-icon class="upload-icon">cloud_upload</mat-icon>
          <p class="upload-primary">Drop CSV here or click to upload</p>
          <p class="upload-secondary">Supports .csv, .tsv files</p>
        </div>
        <input
          #fileInput
          type="file"
          accept=".csv,.tsv"
          class="hidden-input"
          (change)="onFileSelected($event)" />
      }

      <!-- CSV data loaded -->
      @if (hasCsvData()) {
        <div class="csv-header">
          <div class="file-info">
            <mat-icon class="file-icon">insert_drive_file</mat-icon>
            <span class="file-name">{{ csvPreview()!.fileName }}</span>
          </div>
          <div class="file-stats">
            <span class="stat-badge rows">
              {{ csvPreview()!.rowCount | number }} rows
            </span>
            <span class="stat-badge cols">
              {{ csvPreview()!.columns.length }} columns
            </span>
          </div>
          <button
            mat-icon-button
            (click)="fileInput2.click()"
            matTooltip="Replace CSV file"
            aria-label="Replace CSV file">
            <mat-icon>upload_file</mat-icon>
          </button>
          <input
            #fileInput2
            type="file"
            accept=".csv,.tsv"
            class="hidden-input"
            (change)="onFileSelected($event)" />
        </div>

        <div class="columns-list-header">
          <span class="columns-label">Columns</span>
          <button
            mat-button
            (click)="toggleSelectAll()">
            {{ allSelected() ? 'Deselect All' : 'Select All' }}
          </button>
        </div>

        <mat-list class="columns-list">
          @for (col of csvPreview()!.columns; track col.name) {
            <mat-list-item class="column-item">
              <div class="column-row">
                <mat-checkbox
                  [checked]="isSelected(col.name)"
                  (change)="toggleColumn(col.name, $event.checked)"
                  [attr.aria-label]="'Select column ' + col.name">
                </mat-checkbox>

                <span
                  class="mapped-dot"
                  [class.mapped]="col.mapped"
                  [matTooltip]="col.mapped ? 'Mapped' : 'Not mapped'"
                  aria-hidden="true">
                </span>

                <span class="column-name">{{ col.name }}</span>

                <div class="sample-chips">
                  @for (val of col.sampleValues.slice(0, 3); track $index) {
                    <span class="sample-chip">{{ val || '—' }}</span>
                  }
                </div>
              </div>
            </mat-list-item>
          }
        </mat-list>

        <div class="panel-footer">
          <button
            mat-raised-button
            color="primary"
            [disabled]="selectedCount() === 0"
            (click)="onCreateTable()">
            <mat-icon>table_chart</mat-icon>
            Create table from {{ selectedCount() }} selected
            {{ selectedCount() === 1 ? 'column' : 'columns' }}
          </button>
        </div>
      }

    </div>
  `,
  styles: [`
    .csv-source-panel {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }

    /* Upload zone */
    .upload-zone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin: 24px;
      padding: 48px 24px;
      border: 2px dashed var(--mat-divider-color, rgba(0,0,0,.24));
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.15s, border-color 0.15s;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .upload-zone:hover,
    .upload-zone.drag-over {
      background: var(--mat-sys-surface-container, rgba(0,0,0,.04));
      border-color: var(--mat-primary-color, #3f51b5);
    }

    .upload-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mat-primary-color, #3f51b5);
    }

    .upload-primary {
      margin: 0;
      font-size: 1rem;
      font-weight: 500;
    }

    .upload-secondary {
      margin: 0;
      font-size: 0.8rem;
    }

    .hidden-input {
      display: none;
    }

    /* CSV header */
    .csv-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      border-bottom: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
      flex-shrink: 0;
    }

    .file-info {
      display: flex;
      align-items: center;
      gap: 6px;
      flex: 1;
      min-width: 0;
    }

    .file-icon {
      flex-shrink: 0;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .file-name {
      font-weight: 500;
      font-size: 0.9rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .file-stats {
      display: flex;
      gap: 6px;
      flex-shrink: 0;
    }

    .stat-badge {
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 500;
    }

    .stat-badge.rows {
      background: #e3f2fd;
      color: #1565c0;
    }

    .stat-badge.cols {
      background: #e8f5e9;
      color: #2e7d32;
    }

    /* Column list */
    .columns-list-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px 4px;
      flex-shrink: 0;
    }

    .columns-label {
      font-size: 0.8rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .columns-list {
      flex: 1;
      overflow-y: auto;
      padding: 0;
    }

    .column-item {
      height: auto !important;
      min-height: 48px;
    }

    .column-row {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      padding: 8px 0;
    }

    .mapped-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
      background: #9e9e9e;
    }

    .mapped-dot.mapped {
      background: #4caf50;
    }

    .column-name {
      flex: 1;
      font-size: 0.875rem;
      font-weight: 500;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .sample-chips {
      display: flex;
      gap: 4px;
      flex-shrink: 0;
    }

    .sample-chip {
      padding: 1px 6px;
      background: var(--mat-sys-surface-container, #f5f5f5);
      border-radius: 10px;
      font-size: 0.7rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      max-width: 70px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Footer */
    .panel-footer {
      padding: 12px 16px;
      border-top: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
      flex-shrink: 0;
    }

    .panel-footer button {
      width: 100%;
    }
  `]
})
export class CsvSourcePanel {
  readonly cube = input.required<Cube>();
  readonly fileUploaded = output<File>();
  readonly columnsSelected = output<string[]>();

  readonly isDragging = signal(false);
  readonly selectedColumns = signal<Set<string>>(new Set());

  readonly csvPreview = computed(() => {
    const meta = this.cube().metadata;
    if (!meta || !meta['csvPreview']) return null;
    return meta['csvPreview'] as {
      fileName: string;
      rowCount: number;
      columns: CsvColumnPreview[];
    };
  });

  readonly hasCsvData = computed(() => this.csvPreview() !== null);

  readonly selectedCount = computed(() => this.selectedColumns().size);

  readonly allSelected = computed(() => {
    const preview = this.csvPreview();
    if (!preview) return false;
    return preview.columns.every(c => this.selectedColumns().has(c.name));
  });

  isSelected(columnName: string): boolean {
    return this.selectedColumns().has(columnName);
  }

  toggleColumn(columnName: string, checked: boolean): void {
    this.selectedColumns.update(set => {
      const next = new Set(set);
      if (checked) {
        next.add(columnName);
      } else {
        next.delete(columnName);
      }
      return next;
    });
  }

  toggleSelectAll(): void {
    const preview = this.csvPreview();
    if (!preview) return;

    if (this.allSelected()) {
      this.selectedColumns.set(new Set());
    } else {
      this.selectedColumns.set(new Set(preview.columns.map(c => c.name)));
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave(): void {
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.fileUploaded.emit(file);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.fileUploaded.emit(file);
    }
    // Reset so same file can be re-selected
    input.value = '';
  }

  onCreateTable(): void {
    const selected = Array.from(this.selectedColumns());
    if (selected.length > 0) {
      this.columnsSelected.emit(selected);
    }
  }
}
