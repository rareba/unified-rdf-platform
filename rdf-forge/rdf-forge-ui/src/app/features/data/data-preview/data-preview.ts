import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule } from '@angular/material/sort';
import { DataService } from '../../../core/services';
import { DataPreview, UploadOptions } from '../../../core/models';

@Component({
  selector: 'app-data-preview',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatProgressSpinnerModule, MatSortModule],
  template: `
    <div class="data-preview">
      @if (loading()) {
        <div class="loading-container">
          <mat-spinner diameter="50"></mat-spinner>
        </div>
      } @else if (error()) {
        <div class="error-message">
          <span>{{ error() }}</span>
        </div>
      } @else if (previewData()) {
        <div class="table-container">
          <table mat-table [dataSource]="previewData()!.data" matSort class="mat-elevation-z2">
            @for (col of previewData()!.columns; track col) {
              <ng-container [matColumnDef]="col">
                <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ col }}</th>
                <td mat-cell *matCellDef="let row">{{ row[col] }}</td>
              </ng-container>
            }

            <tr mat-header-row *matHeaderRowDef="previewData()!.columns"></tr>
            <tr mat-row *matRowDef="let row; columns: previewData()!.columns"></tr>

            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="previewData()?.columns?.length">
                No data found.
              </td>
            </tr>
          </table>

          <div class="table-summary">
            In total there are {{ previewData()?.totalRows || 0 }} rows.
          </div>
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
    }

    .loading-container {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 2rem;
      min-height: 200px;
    }

    .error-message {
      padding: 1rem;
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      margin: 1rem 0;
    }

    .table-container {
      width: 100%;
      overflow-x: auto;
      max-height: 400px;
      overflow-y: auto;
    }

    table {
      width: 100%;
      min-width: 50rem;
    }

    .table-summary {
      padding: 1rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-top: 1px solid rgba(0, 0, 0, 0.12);
      background-color: #fafafa;
    }

    th.mat-header-cell {
      font-weight: 600;
      background-color: #f5f5f5;
    }

    tr.mat-row:hover {
      background-color: #f5f5f5;
    }
  `]
})
export class DataPreviewComponent implements OnChanges {
  @Input() dataSourceId: string | null = null;
  @Input() options: UploadOptions | null = null;

  private readonly dataService = inject(DataService);

  previewData = signal<DataPreview | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dataSourceId'] || changes['options']) {
      this.loadPreview();
    }
  }

  loadPreview(): void {
    if (!this.dataSourceId) {
      this.previewData.set(null);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    // If we had options to pass to preview, we would add them here.
    // The current DataService.preview only accepts {rows, offset}.
    // We might need to extend the backend API to support previewing with *proposed* options 
    // (e.g. "what if delimiter is ';'?"), but for now we'll just fetch the default preview.
    
    this.dataService.preview(this.dataSourceId, { rows: 20 }).subscribe({
      next: (data) => {
        this.previewData.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Preview error:', err);
        this.error.set('Failed to load data preview. ' + (err.message || ''));
        this.loading.set(false);
      }
    });
  }
}
