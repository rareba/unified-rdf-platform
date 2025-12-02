import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { FileUploadModule } from 'primeng/fileupload';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DataService } from '../../../core/services';
import { DataSource, DataPreview } from '../../../core/models';

@Component({
  selector: 'app-data-manager',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    DialogModule,
    FileUploadModule,
    TagModule,
    TooltipModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './data-manager.html',
  styleUrl: './data-manager.scss',
})
export class DataManager implements OnInit {
  private readonly dataService = inject(DataService);
  private readonly messageService = inject(MessageService);

  loading = signal(true);
  searchQuery = signal('');
  dataSources = signal<DataSource[]>([]);
  uploadDialogVisible = signal(false);
  previewDialogVisible = signal(false);
  selectedDataSource = signal<DataSource | null>(null);
  preview = signal<DataPreview | null>(null);
  previewLoading = signal(false);

  ngOnInit(): void {
    this.loadDataSources();
  }

  loadDataSources(): void {
    this.loading.set(true);
    this.dataService.list().subscribe({
      next: (data) => {
        this.dataSources.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load data sources' });
        this.loading.set(false);
      }
    });
  }

  onUpload(event: { files: File[] }): void {
    const file = event.files[0];
    if (!file) return;

    this.dataService.upload(file).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Uploaded', detail: `${file.name} uploaded successfully` });
        this.uploadDialogVisible.set(false);
        this.loadDataSources();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to upload file' });
      }
    });
  }

  previewData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.selectedDataSource.set(source);
    this.previewDialogVisible.set(true);
    this.previewLoading.set(true);

    this.dataService.preview(source.id, { rows: 10 }).subscribe({
      next: (data) => {
        this.preview.set(data);
        this.previewLoading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load preview' });
        this.previewLoading.set(false);
      }
    });
  }

  downloadData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.dataService.download(source.id).subscribe({
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to download file' });
      }
    });
  }

  deleteData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.dataService.delete(source.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: `${source.name} deleted` });
        this.loadDataSources();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete file' });
      }
    });
  }

  formatSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getFormatSeverity(format: string): 'success' | 'info' | 'warn' | 'secondary' {
    switch (format) {
      case 'csv': return 'success';
      case 'json': return 'info';
      case 'xlsx': return 'warn';
      default: return 'secondary';
    }
  }
}
