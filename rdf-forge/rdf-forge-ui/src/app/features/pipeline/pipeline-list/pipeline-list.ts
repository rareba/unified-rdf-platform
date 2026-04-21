import { Component, inject, OnInit, OnDestroy, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PipelineService, JobService } from '../../../core/services';
import { Pipeline, Job } from '../../../core/models';
@Component({
  selector: 'app-pipeline-list',
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSortModule,
    MatChipsModule,
    MatCardModule,
    MatDialogModule,
    MatMenuModule,
    MatDividerModule
  ],
  templateUrl: './pipeline-list.html',
  styleUrl: './pipeline-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PipelineList implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly jobService = inject(JobService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  loading = signal(true);
  refreshing = signal(false);
  searchQuery = signal('');
  statusFilter = signal<string | null>(null);
  pipelines = signal<Pipeline[]>([]);
  selectedPipeline = signal<Pipeline | null>(null);
  deleting = signal(false);

  displayedColumns: string[] = ['name', 'status', 'stepsCount', 'lastRun', 'tags', 'actions'];
  pageSize = signal(10);
  pageIndex = signal(0);
  sortField = signal<string>('name');
  sortDirection = signal<'asc' | 'desc'>('asc');

  statusOptions = [
    { label: 'All', value: null },
    { label: 'Active', value: 'active' },
    { label: 'Draft', value: 'draft' },
    { label: 'Archived', value: 'archived' }
  ];

  filteredPipelines = computed(() => {
    let result = this.pipelines();
    const query = this.searchQuery().toLowerCase().trim();
    if (query) {
      result = result.filter(p =>
        p.name.toLowerCase().includes(query) ||
        p.description?.toLowerCase().includes(query) ||
        p.tags?.some(t => t.toLowerCase().includes(query))
      );
    }
    const status = this.statusFilter();
    if (status) {
      result = result.filter(p => p.status === status);
    }
    return result;
  });

  sortedPipelines = computed(() => {
    const data = [...this.filteredPipelines()];
    const field = this.sortField();
    const direction = this.sortDirection();

    data.sort((a: any, b: any) => {
      const aValue = a[field];
      const bValue = b[field];

      if (aValue === bValue) return 0;
      if (aValue == null) return 1;
      if (bValue == null) return -1;

      const comparison = aValue < bValue ? -1 : 1;
      return direction === 'asc' ? comparison : -comparison;
    });

    return data;
  });

  paginatedPipelines = computed(() => {
    const data = this.sortedPipelines();
    const start = this.pageIndex() * this.pageSize();
    const end = start + this.pageSize();
    return data.slice(start, end);
  });

  ngOnInit(): void {
    this.loadPipelines();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.pageIndex.set(0);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadPipelines(): void {
    this.loading.set(true);
    this.pipelineService.list().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (pipelines) => {
        // Load jobs to get last run info
        this.jobService.list({ size: 1000 }).pipe(
          takeUntil(this.destroy$)
        ).subscribe({
          next: (jobs) => {
            // Compute lastRun for each pipeline from jobs
            const lastRunMap = new Map<string, Date>();
            jobs.forEach((job: Job) => {
              if (job.completedAt && (job.status?.toLowerCase() === 'completed' || job.status?.toLowerCase() === 'failed')) {
                const completedAt = new Date(job.completedAt);
                const existing = lastRunMap.get(job.pipelineId);
                if (!existing || completedAt > existing) {
                  lastRunMap.set(job.pipelineId, completedAt);
                }
              }
            });

            // Enrich pipelines with lastRun
            const enrichedPipelines = pipelines.map(p => ({
              ...p,
              lastRun: lastRunMap.get(p.id) || p.lastRun
            }));

            this.pipelines.set(enrichedPipelines);
            this.loading.set(false);
          },
          error: () => {
            // If jobs fail to load, still show pipelines
            this.pipelines.set(pipelines);
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.snackBar.open('Failed to load pipelines', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  refreshPipelines(): void {
    this.refreshing.set(true);
    this.loadPipelines();
    setTimeout(() => this.refreshing.set(false), 500);
  }

  onSearchChange(value: string): void {
    this.searchSubject.next(value);
  }

  createPipeline(): void {
    this.router.navigate(['/pipelines/new']);
  }

  openPipeline(pipeline: Pipeline): void {
    this.router.navigate(['/pipelines', pipeline.id]);
  }

  runPipeline(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();

    // Confirm before running
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Run Pipeline',
        message: `Are you sure you want to run "${pipeline.name}"?`,
        confirmText: 'Run',
        confirmColor: 'primary'
      }
    });

    dialogRef.afterClosed().pipe(
      takeUntil(this.destroy$)
    ).subscribe(result => {
      if (result) {
        this.pipelineService.run(pipeline.id).pipe(
          takeUntil(this.destroy$)
        ).subscribe({
          next: () => {
            this.snackBar.open(`Pipeline "${pipeline.name}" started`, 'View Jobs', {
              duration: 3000
            }).onAction().pipe(takeUntil(this.destroy$)).subscribe(() => {
              this.router.navigate(['/jobs']);
            });
          },
          error: () => {
            this.snackBar.open('Failed to run pipeline', 'Close', { duration: 3000 });
          }
        });
      }
    });
  }

  duplicatePipeline(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Clone Pipeline',
        message: `Create a copy of "${pipeline.name}"?`,
        confirmText: 'Clone',
        confirmColor: 'primary'
      }
    });

    dialogRef.afterClosed().pipe(
      takeUntil(this.destroy$)
    ).subscribe(result => {
      if (result) {
        this.pipelineService.duplicate(pipeline.id).pipe(
          takeUntil(this.destroy$)
        ).subscribe({
          next: (newPipeline) => {
            this.snackBar.open(`Pipeline "${pipeline.name}" duplicated`, 'Edit', {
              duration: 3000
            }).onAction().pipe(takeUntil(this.destroy$)).subscribe(() => {
              this.router.navigate(['/pipelines', newPipeline.id]);
            });
            this.loadPipelines();
          },
          error: () => {
            this.snackBar.open('Failed to duplicate pipeline', 'Close', { duration: 3000 });
          }
        });
      }
    });
  }

  confirmDelete(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();
    this.selectedPipeline.set(pipeline);

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Pipeline',
        message: `Are you sure you want to delete "${pipeline.name}"? This action cannot be undone.`,
        confirmText: 'Delete',
        confirmColor: 'warn'
      }
    });

    dialogRef.afterClosed().pipe(
      takeUntil(this.destroy$)
    ).subscribe(result => {
      if (result) {
        this.deletePipeline(pipeline);
      }
    });
  }

  deletePipeline(pipeline: Pipeline): void {
    this.deleting.set(true);
    this.pipelineService.delete(pipeline.id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.snackBar.open(`Pipeline "${pipeline.name}" deleted`, 'Close', { duration: 3000 });
        this.loadPipelines();
        this.deleting.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to delete pipeline', 'Close', { duration: 3000 });
        this.deleting.set(false);
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.pageIndex.set(event.pageIndex);
  }

  onSortChange(sort: Sort): void {
    this.sortField.set(sort.active || 'name');
    this.sortDirection.set(sort.direction || 'asc');
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'active': return 'status-active';
      case 'draft': return 'status-draft';
      case 'archived': return 'status-archived';
      default: return 'status-info';
    }
  }

  formatDate(date: Date | undefined): string {
    if (!date) return 'Never';
    return new Date(date).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}

// Simple confirm dialog component
import { Component as NgComponent, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

interface ConfirmDialogData {
  title: string;
  message: string;
  confirmText: string;
  confirmColor: 'primary' | 'accent' | 'warn';
}

@NgComponent({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button [color]="data.confirmColor" [mat-dialog-close]="true">
        {{ data.confirmText }}
      </button>
    </mat-dialog-actions>
  `
})
class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {}
}
