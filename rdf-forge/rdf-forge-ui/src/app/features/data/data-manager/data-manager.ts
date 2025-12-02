import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { FileUploadModule, FileUpload } from 'primeng/fileupload';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ProgressBarModule } from 'primeng/progressbar';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { MessageService, ConfirmationService } from 'primeng/api';
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
    ToastModule,
    ProgressBarModule,
    ConfirmDialogModule,
    CardModule,
    DividerModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './data-manager.html',
  styleUrl: './data-manager.scss',
})
export class DataManager implements OnInit {
  private readonly dataService = inject(DataService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  loading = signal(true);
  searchQuery = signal('');
  dataSources = signal<DataSource[]>([]);
  uploadDialogVisible = signal(false);
  previewDialogVisible = signal(false);
  detailDialogVisible = signal(false);
  selectedDataSource = signal<DataSource | null>(null);
  preview = signal<DataPreview | null>(null);
  previewLoading = signal(false);
  uploading = signal(false);
  uploadProgress = signal(0);

  filteredDataSources = computed(() => {
    const query = this.searchQuery().toLowerCase();
    if (!query) return this.dataSources();
    return this.dataSources().filter(ds =>
      ds.name.toLowerCase().includes(query) ||
      ds.originalFilename.toLowerCase().includes(query) ||
      ds.format.toLowerCase().includes(query)
    );
  });

  totalSize = computed(() => {
    return this.dataSources().reduce((sum, ds) => sum + (ds.sizeBytes || 0), 0);
  });

  totalRows = computed(() => {
    return this.dataSources().reduce((sum, ds) => sum + (ds.rowCount || 0), 0);
  });

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
      error: (err) => {
        console.error('Failed to load data sources:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load data sources' });
        this.loading.set(false);
      }
    });
  }

  onFileSelect(event: { files: File[] }, fileUpload: FileUpload): void {
    const file = event.files[0];
    if (!file) return;

    this.uploading.set(true);
    this.uploadProgress.set(0);

    this.dataService.upload(file, { analyze: true }).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Uploaded', detail: `${file.name} uploaded successfully` });
        this.uploading.set(false);
        this.uploadDialogVisible.set(false);
        fileUpload.clear();
        this.loadDataSources();
      },
      error: (err) => {
        console.error('Upload failed:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to upload file' });
        this.uploading.set(false);
        fileUpload.clear();
      }
    });
  }

  viewDetails(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.selectedDataSource.set(source);
    this.detailDialogVisible.set(true);
  }

  previewData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.selectedDataSource.set(source);
    this.previewDialogVisible.set(true);
    this.previewLoading.set(true);

    this.dataService.preview(source.id, { rows: 100 }).subscribe({
      next: (data) => {
        this.preview.set(data);
        this.previewLoading.set(false);
      },
      error: (err) => {
        console.error('Preview failed:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load preview' });
        this.previewLoading.set(false);
      }
    });
  }

  downloadData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.messageService.add({ severity: 'info', summary: 'Downloading', detail: `Preparing ${source.originalFilename}...` });
    this.dataService.download(source.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Downloaded', detail: 'File download started' });
      },
      error: (err) => {
        console.error('Download failed:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to download file' });
      }
    });
  }

  confirmDelete(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      message: `Are you sure you want to delete "${source.name}"? This action cannot be undone.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.deleteData(source);
      }
    });
  }

  private deleteData(source: DataSource): void {
    this.dataService.delete(source.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: `${source.name} deleted` });
        this.loadDataSources();
      },
      error: (err) => {
        console.error('Delete failed:', err);
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

  getFormatSeverity(format: string): 'success' | 'info' | 'warn' | 'secondary' | 'danger' {
    switch (format?.toLowerCase()) {
      case 'csv': return 'success';
      case 'json': return 'info';
      case 'xlsx': return 'warn';
      case 'parquet': return 'danger';
      default: return 'secondary';
    }
  }

  getFormatIcon(format: string): string {
    switch (format?.toLowerCase()) {
      case 'csv': return 'pi pi-file';
      case 'json': return 'pi pi-code';
      case 'xlsx': return 'pi pi-file-excel';
      case 'parquet': return 'pi pi-database';
      case 'xml': return 'pi pi-sitemap';
      default: return 'pi pi-file';
    }
  }

  closeUploadDialog(): void {
    this.uploadDialogVisible.set(false);
    this.uploading.set(false);
    this.uploadProgress.set(0);
  }
}
