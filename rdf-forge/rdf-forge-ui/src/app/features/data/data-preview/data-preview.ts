import { Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageModule } from 'primeng/message';
import { DataService } from '../../../core/services';
import { DataPreview, UploadOptions } from '../../../core/models';

@Component({
  selector: 'app-data-preview',
  standalone: true,
  imports: [CommonModule, TableModule, SkeletonModule, MessageModule],
  template: `
    <div class="data-preview">
      @if (loading()) {
        <div class="p-4">
          <p-skeleton width="100%" height="200px"></p-skeleton>
        </div>
      } @else if (error()) {
        <p-message severity="error" [text]="error() || undefined" styleClass="w-full"></p-message>
      } @else if (previewData()) {
        <p-table 
          [value]="previewData()!.data" 
          [columns]="previewData()!.columns" 
          [scrollable]="true" 
          scrollHeight="400px"
          styleClass="p-datatable-sm p-datatable-gridlines p-datatable-striped"
          [tableStyle]="{'min-width': '50rem'}">
          <ng-template pTemplate="header" let-columns>
            <tr>
              <th *ngFor="let col of columns" [pSortableColumn]="col">
                {{col}} <p-sortIcon [field]="col"></p-sortIcon>
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowData let-columns="columns">
            <tr>
              <td *ngFor="let col of columns">
                {{rowData[col]}}
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td [attr.colspan]="previewData()?.columns?.length">No data found.</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="summary">
            <div class="flex align-items-center justify-content-between">
              In total there are {{previewData()?.totalRows || 0}} rows.
            </div>
          </ng-template>
        </p-table>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
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
