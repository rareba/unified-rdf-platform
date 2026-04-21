import { Component, inject, OnInit, OnDestroy, signal, computed, ViewChild, ElementRef, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { DataService } from '../../../core/services';
import { DataSource, DataPreview, DataFormatDescriptor } from '../../../core/models';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { LoggerService } from '../../../core/services/logger.service';

@Component({
  selector: 'app-data-manager',
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatPaginatorModule,
    MatSortModule
  ],
  templateUrl: './data-manager.html',
  styleUrl: './data-manager.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataManager implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly dataService = inject(DataService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly logger = inject(LoggerService);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

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
  selectedFile = signal<File | null>(null);
  isDragOver = signal(false);

  /**
   * All formats advertised by the backend (available AND stubs). Loaded from
   * /api/v1/data/formats on init so the UI never claims a format the backend
   * has not actually registered.
   */
  formats = signal<DataFormatDescriptor[]>([]);

  availableFormats = computed(() => this.formats().filter(f => f.available));
  unavailableFormats = computed(() => this.formats().filter(f => !f.available));

  /** Accept attribute for the <input type="file"> element, built from the server list. */
  acceptAttr = computed(() => {
    const exts = this.availableFormats().flatMap(f => f.extensions);
    if (exts.length === 0) return '';
    return exts.map(e => '.' + e).join(',');
  });

  /** Valid extensions (with leading dot, lower-case) used by the drop-zone guard. */
  private validExtensions = computed<string[]>(() =>
    this.availableFormats().flatMap(f => f.extensions).map(e => '.' + e.toLowerCase())
  );

  // Table
  displayedColumns = ['name', 'format', 'size', 'rows', 'uploadedAt', 'actions'];
  pageSize = 10;
  pageIndex = 0;

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

  pagedDataSources = computed(() => {
    const filtered = this.filteredDataSources();
    const start = this.pageIndex * this.pageSize;
    return filtered.slice(start, start + this.pageSize);
  });

  ngOnInit(): void {
    this.loadDataSources();
    this.loadFormats();
  }

  private loadFormats(): void {
    this.dataService.formats().pipe(takeUntil(this.destroy$)).subscribe({
      next: (formats) => this.formats.set(formats),
      error: (err) => {
        // Non-fatal — we fall back to showing no formats, which is the
        // honest default if the API is unreachable.
        this.logger.warn('Failed to load data format list', err);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadDataSources(): void {
    this.loading.set(true);
    this.dataService.list().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.dataSources.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.logger.error('Failed to load data sources:', err);
        this.snackBar.open('Failed to load data sources', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile.set(input.files[0]);
    }
  }

  triggerFileInput(): void {
    if (!this.selectedFile() && !this.uploading() && this.fileInput) {
      this.fileInput.nativeElement.click();
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      const file = files[0];
      const fileExt = '.' + (file.name.split('.').pop()?.toLowerCase() ?? '');
      const valid = this.validExtensions();

      if (valid.length === 0 || valid.includes(fileExt)) {
        this.selectedFile.set(file);
      } else {
        const names = this.availableFormats().map(f => f.displayName || f.id.toUpperCase()).join(', ');
        this.snackBar.open(`Invalid file format. Supported: ${names}`, 'Close', { duration: 3000 });
      }
    }
  }

  uploadFile(): void {
    const file = this.selectedFile();
    if (!file) {
      this.snackBar.open('Please select a file', 'Close', { duration: 3000 });
      return;
    }

    this.uploading.set(true);
    this.uploadProgress.set(0);

    this.dataService.uploadWithProgress(
      file,
      { analyze: true },
      (progress) => this.uploadProgress.set(progress.progress)
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.uploadProgress.set(100);
        this.snackBar.open(`${file.name} uploaded successfully`, 'Close', { duration: 3000 });
        this.uploading.set(false);
        this.uploadDialogVisible.set(false);
        this.selectedFile.set(null);
        this.loadDataSources();
      },
      error: (err) => {
        this.logger.error('Upload failed:', err);
        this.snackBar.open('Failed to upload file', 'Close', { duration: 3000 });
        this.uploading.set(false);
        this.uploadProgress.set(0);
      }
    });
  }

  clearSelectedFile(): void {
    this.selectedFile.set(null);
  }

  closeUploadDialog(): void {
    this.uploadDialogVisible.set(false);
    this.uploading.set(false);
    this.uploadProgress.set(0);
    this.selectedFile.set(null);
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

    this.dataService.preview(source.id, { rows: 100 }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.preview.set(data);
        this.previewLoading.set(false);
      },
      error: (err) => {
        this.logger.error('Preview failed:', err);
        this.snackBar.open('Failed to load preview', 'Close', { duration: 3000 });
        this.previewLoading.set(false);
      }
    });
  }

  downloadData(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.snackBar.open(`Preparing ${source.originalFilename}...`, 'Close', { duration: 2000 });
    this.dataService.download(source.id).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.snackBar.open('File download started', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.logger.error('Download failed:', err);
        this.snackBar.open('Failed to download file', 'Close', { duration: 3000 });
      }
    });
  }

  confirmDelete(source: DataSource, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      title: 'Delete Data Source',
      message: `Are you sure you want to delete "${source.name}"? This action cannot be undone.`,
      confirmText: 'Delete',
      confirmColor: 'warn'
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (confirmed) {
        this.deleteData(source);
      }
    });
  }

  private deleteData(source: DataSource): void {
    this.dataService.delete(source.id).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.snackBar.open(`${source.name} deleted`, 'Close', { duration: 3000 });
        this.loadDataSources();
      },
      error: (err) => {
        this.logger.error('Delete failed:', err);
        this.snackBar.open('Failed to delete file', 'Close', { duration: 3000 });
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

  getFormatClass(format: string): string {
    switch (format?.toLowerCase()) {
      case 'csv': return 'format-success';
      case 'json': return 'format-info';
      case 'xlsx': return 'format-warn';
      case 'parquet': return 'format-danger';
      default: return 'format-secondary';
    }
  }

  getFormatIcon(format: string): string {
    switch (format?.toLowerCase()) {
      case 'csv': return 'description';
      case 'json': return 'code';
      case 'xlsx': return 'table_chart';
      case 'parquet': return 'storage';
      case 'xml': return 'account_tree';
      default: return 'insert_drive_file';
    }
  }

  closeDetailsDialog(): void {
    this.detailDialogVisible.set(false);
  }

  closePreviewDialog(): void {
    this.previewDialogVisible.set(false);
  }
}
