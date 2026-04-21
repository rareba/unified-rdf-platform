import { Component, Input, OnChanges, OnDestroy, SimpleChanges, inject, signal, viewChild, AfterViewInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { DataService } from '../../../core/services';
import { DataPreview as DataPreviewModel, UploadOptions } from '../../../core/models';
import { LoggerService } from '../../../core/services/logger.service';

@Component({
  selector: 'app-data-preview',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    ScrollingModule
  ],
  template: `
    <div class="data-preview">
      <!-- Toolbar -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          @if (previewData()) {
            <span class="data-info">
              {{ previewData()!.totalRows | number }} rows
              @if (detectedDelimiter()) {
                <span class="delimiter-info">| Delimiter: "{{ detectedDelimiter() }}"</span>
              }
            </span>
          }
        </div>
        <div class="toolbar-right">
          <button mat-stroked-button (click)="exportToCsv()" [disabled]="!previewData()" aria-label="Export to CSV">
            <mat-icon>download</mat-icon>
            Export CSV
          </button>
          <button mat-icon-button (click)="reloadPreview()" [disabled]="!dataSourceId" matTooltip="Reload preview" aria-label="Reload preview">
            <mat-icon>refresh</mat-icon>
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-container" role="status">
          <mat-spinner diameter="50"></mat-spinner>
          <p>Loading data preview...</p>
        </div>
      } @else if (error()) {
        <div class="error-message" role="alert">
          <mat-icon>error</mat-icon>
          <span>{{ error() }}</span>
          <button mat-button (click)="reloadPreview()">Retry</button>
        </div>
      } @else if (previewData()) {
        <!-- Virtual Scrolling Table -->
        <div class="table-container">
          <cdk-virtual-scroll-viewport
            #viewport
            itemSize="48"
            class="virtual-viewport"
            [minBufferPx]="400"
            [maxBufferPx]="800">
            <table mat-table [dataSource]="previewData()!.data" matSort class="mat-elevation-z2">
              @for (col of previewData()!.columns; track col) {
                <ng-container [matColumnDef]="col">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header [style.width]="getColumnWidth(col)">
                    <div class="column-header">
                      <span>{{ col }}</span>
                      <div class="resize-handle" (mousedown)="startResize($event, col)"></div>
                    </div>
                  </th>
                  <td mat-cell *matCellDef="let row" [style.width]="getColumnWidth(col)">{{ row[col] }}</td>
                </ng-container>
              }

              <tr mat-header-row *matHeaderRowDef="previewData()!.columns; sticky: true"></tr>
              <tr mat-row *matRowDef="let row; columns: previewData()!.columns;"></tr>

              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell" [attr.colspan]="previewData()?.columns?.length">
                  No data found.
                </td>
              </tr>
            </table>
          </cdk-virtual-scroll-viewport>
        </div>

        <div class="table-summary">
          <span>Showing {{ previewData()!.data.length }} of {{ previewData()!.totalRows | number }} rows</span>
          <span class="encoding-info">Encoding: {{ detectedEncoding() || 'UTF-8' }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .data-preview {
      width: 100%;
      display: flex;
      flex-direction: column;
    }

    .preview-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 16px;
      background-color: #f5f5f5;
      border-bottom: 1px solid #e0e0e0;
    }

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .data-info {
      font-size: 14px;
      color: #666;
    }

    .delimiter-info {
      color: #999;
      margin-left: 8px;
    }

    .toolbar-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      padding: 2rem;
      min-height: 200px;
      gap: 16px;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 1rem;
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      margin: 1rem;
    }

    .table-container {
      width: 100%;
      overflow: hidden;
    }

    .virtual-viewport {
      height: 400px;
      width: 100%;
    }

    table {
      width: 100%;
      min-width: 50rem;
    }

    .column-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: relative;
    }

    .resize-handle {
      position: absolute;
      right: -4px;
      top: 0;
      bottom: 0;
      width: 8px;
      cursor: col-resize;
      z-index: 10;
    }

    .resize-handle:hover {
      background-color: rgba(0, 0, 0, 0.1);
    }

    .table-summary {
      padding: 1rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-top: 1px solid rgba(0, 0, 0, 0.12);
      background-color: #fafafa;
      font-size: 14px;
      color: #666;
    }

    .encoding-info {
      color: #999;
    }

    th.mat-header-cell {
      font-weight: 600;
      background-color: #f5f5f5;
      position: relative;
    }

    tr.mat-row:hover {
      background-color: #f5f5f5;
    }

    td.mat-cell {
      max-width: 300px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Dark theme support */
    :host-context(.dark-theme) .preview-toolbar,
    :host-context(.dark-theme) .table-summary {
      background-color: #424242;
      color: #fff;
    }

    :host-context(.dark-theme) th.mat-header-cell {
      background-color: #424242;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataPreviewComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() dataSourceId: string | null = null;
  @Input() options: UploadOptions | null = null;

  private readonly destroy$ = new Subject<void>();
  private readonly dataService = inject(DataService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly logger = inject(LoggerService);

  readonly sort = viewChild<MatSort>(MatSort);
  readonly viewport = viewChild<CdkVirtualScrollViewport>(CdkVirtualScrollViewport);

  previewData = signal<DataPreviewModel | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  detectedDelimiter = signal<string | null>(null);
  detectedEncoding = signal<string>('UTF-8');

  private columnWidths: Map<string, string> = new Map();
  private resizingColumn: string | null = null;
  private startX = 0;
  private startWidth = 0;

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dataSourceId'] || changes['options']) {
      this.loadPreview();
    }
  }

  ngAfterViewInit(): void {
    // Setup sort after view init
    const sort = this.sort();
    if (sort) {
      sort.sortChange.pipe(takeUntil(this.destroy$)).subscribe(() => {
        // Re-sort data if needed
        this.sortData();
      });
    }
  }

  loadPreview(): void {
    if (!this.dataSourceId) {
      this.previewData.set(null);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    // Detect CSV delimiter if needed
    if (this.options?.delimiter) {
      this.detectedDelimiter.set(this.options.delimiter);
    } else {
      // Auto-detect delimiter
      this.autoDetectDelimiter();
    }

    this.dataService.preview(this.dataSourceId, { rows: 100, offset: 0 }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.previewData.set(data);
        this.loading.set(false);

        // Initialize default column widths
        if (data.columns) {
          data.columns.forEach(col => {
            if (!this.columnWidths.has(col)) {
              this.columnWidths.set(col, '150px');
            }
          });
        }
      },
      error: (err) => {
        this.logger.error('Preview error:', err);
        this.error.set('Failed to load data preview. ' + (err.message || ''));
        this.loading.set(false);
      }
    });
  }

  reloadPreview(): void {
    this.loadPreview();
  }

  private autoDetectDelimiter(): void {
    // Simple auto-detection: check common delimiters
    const sample = ''; // Would need sample data from backend
    const delimiters = [',', ';', '\t', '|'];
    const counts = delimiters.map(d => ({ delimiter: d, count: (sample.match(new RegExp(d, 'g')) || []).length }));
    const best = counts.sort((a, b) => b.count - a.count)[0];
    if (best && best.count > 0) {
      this.detectedDelimiter.set(best.delimiter);
    } else {
      this.detectedDelimiter.set(','); // Default
    }
  }

  getColumnWidth(column: string): string {
    return this.columnWidths.get(column) || '150px';
  }

  startResize(event: MouseEvent, column: string): void {
    this.resizingColumn = column;
    this.startX = event.pageX;
    const currentWidth = this.columnWidths.get(column) || '150px';
    this.startWidth = parseInt(currentWidth, 10);

    document.addEventListener('mousemove', this.onResizeMove);
    document.addEventListener('mouseup', this.onResizeEnd);
    event.preventDefault();
  }

  private onResizeMove = (event: MouseEvent): void => {
    if (!this.resizingColumn) return;

    const diff = event.pageX - this.startX;
    const newWidth = Math.max(50, this.startWidth + diff);
    this.columnWidths.set(this.resizingColumn, `${newWidth}px`);
  };

  private onResizeEnd = (): void => {
    this.resizingColumn = null;
    document.removeEventListener('mousemove', this.onResizeMove);
    document.removeEventListener('mouseup', this.onResizeEnd);
  };

  sortData(): void {
    const sort = this.sort();
    const data = this.previewData();
    if (!sort || !sort.active || !data) return;

    const sortedData = [...data.data].sort((a, b) => {
      const aVal = a[sort.active];
      const bVal = b[sort.active];

      if (aVal === bVal) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      const comparison = aVal < bVal ? -1 : 1;
      return sort.direction === 'asc' ? comparison : -comparison;
    });

    this.previewData.set({ ...data, data: sortedData });
  }

  exportToCsv(): void {
    const data = this.previewData();
    if (!data) return;

    const delimiter = this.detectedDelimiter() || ',';
    let csv = '';

    // Header
    csv += data.columns.join(delimiter) + '\n';

    // Data
    data.data.forEach(row => {
      const values = data.columns.map(col => {
        const val = row[col];
        // Escape values containing delimiter or quotes
        if (typeof val === 'string' && (val.includes(delimiter) || val.includes('"'))) {
          return '"' + val.replace(/"/g, '""') + '"';
        }
        return val ?? '';
      });
      csv += values.join(delimiter) + '\n';
    });

    // Create blob and download
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `data-preview-${this.dataSourceId}.csv`;
    a.click();
    URL.revokeObjectURL(url);

    this.snackBar.open('CSV exported successfully', 'Close', { duration: 3000 });
  }
}
