import { Component, inject, OnInit, OnDestroy, signal, computed, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, Sort, MatSort } from '@angular/material/sort';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule, MatChipListbox } from '@angular/material/chips';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatMenuModule } from '@angular/material/menu';
import { SelectionModel } from '@angular/cdk/collections';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { JobService, PipelineService } from '../../../core/services';
import { Job, JobLog, Pipeline } from '../../../core/models';
import { LoggerService } from '../../../core/services/logger.service';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { SkeletonLoaderComponent } from '../../../shared/components/skeleton-loader/skeleton-loader';

type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-job-list',
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatCardModule,
    MatPaginatorModule,
    MatSortModule,
    MatDialogModule,
    MatSnackBarModule,
    MatChipsModule,
    MatCheckboxModule,
    MatMenuModule,
    SkeletonLoaderComponent
  ],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobList implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly pipelineService = inject(PipelineService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly logger = inject(LoggerService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<string>();

  readonly sort = viewChild<MatSort>(MatSort);
  readonly paginator = viewChild<MatPaginator>(MatPaginator);

  loading = signal(false);
  refreshing = signal(false);
  searchQuery = signal('');
  statusFilters = signal<JobStatus[]>([]);
  jobs = signal<Job[]>([]);
  pipelines = signal<Pipeline[]>([]);
  backendAvailable = signal(true);
  initialLoadComplete = signal(false);
  totalElements = signal(0);

  // Selection
  selection = new SelectionModel<Job>(true, []);

  // Dialogs
  logsDialogVisible = signal(false);
  detailsDialogVisible = signal(false);
  newJobDialogVisible = signal(false);
  selectedJob = signal<Job | null>(null);
  jobLogs = signal<JobLog[]>([]);
  logsLoading = signal(false);
  selectedPipelineId = signal<string | null>(null);
  creatingJob = signal(false);

  // Table
  displayedColumns = ['select', 'id', 'pipeline', 'status', 'progress', 'metrics', 'startedAt', 'duration', 'actions'];
  dataSource = new MatTableDataSource<Job>([]);
  pageSize = 15;
  pageIndex = 0;
  sortField = 'startedAt';
  sortDirection: 'asc' | 'desc' = 'desc';

  statusOptions: { label: string; value: JobStatus; color: string }[] = [
    { label: 'Pending', value: 'PENDING', color: 'default' },
    { label: 'Running', value: 'RUNNING', color: 'accent' },
    { label: 'Completed', value: 'COMPLETED', color: 'primary' },
    { label: 'Failed', value: 'FAILED', color: 'warn' },
    { label: 'Cancelled', value: 'CANCELLED', color: '' }
  ];

  filteredJobs = computed(() => {
    let result = this.jobs();
    const query = this.searchQuery().toLowerCase().trim();
    if (query) {
      result = result.filter(j =>
        j.id.toLowerCase().includes(query) ||
        j.pipelineName.toLowerCase().includes(query)
      );
    }
    const statusFilters = this.statusFilters();
    if (statusFilters.length > 0) {
      result = result.filter(j => statusFilters.includes(j.status?.toUpperCase() as JobStatus));
    }
    return result;
  });

  pagedJobs = computed(() => {
    const filtered = this.filteredJobs();
    const start = this.pageIndex * this.pageSize;
    return filtered.slice(start, start + this.pageSize);
  });

  // Stats computed properties
  runningCount = computed(() => this.jobs().filter(j => j.status?.toLowerCase() === 'running').length);
  pendingCount = computed(() => this.jobs().filter(j => j.status?.toLowerCase() === 'pending').length);
  completedToday = computed(() => {
    const today = new Date().toDateString();
    return this.jobs().filter(j => j.status?.toLowerCase() === 'completed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length;
  });
  failedToday = computed(() => {
    const today = new Date().toDateString();
    return this.jobs().filter(j => j.status?.toLowerCase() === 'failed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length;
  });
  avgDuration = computed(() => {
    const completed = this.jobs().filter(j => j.duration);
    if (completed.length === 0) return '-';
    const avg = completed.reduce((sum, j) => sum + (j.duration || 0), 0) / completed.length;
    return this.formatDuration(avg);
  });
  totalRowsProcessed = computed(() => {
    return this.jobs().reduce((sum, j) => sum + (j.metrics?.rowsProcessed || 0), 0);
  });

  isAllSelected = computed(() => {
    const filtered = this.filteredJobs();
    return filtered.length > 0 && filtered.every(job => this.selection.isSelected(job));
  });

  hasSelection = computed(() => this.selection.selected.length > 0);

  ngOnInit(): void {
    this.loadJobs();
    this.loadPipelines();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.pageIndex = 0;
      if (this.paginator()) {
        this.paginator()!.pageIndex = 0;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.selection.clear();
  }

  loadJobs(): void {
    this.loading.set(true);
    this.jobService.list({
      page: this.pageIndex,
      size: this.pageSize,
      sort: `${this.sortField},${this.sortDirection}`
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response: Job[] | SpringPage<Job>) => {
        // Handle both array and Spring Data format
        let data: Job[];
        if (Array.isArray(response)) {
          data = response;
          this.totalElements.set(response.length);
        } else {
          data = response.content || [];
          this.totalElements.set(response.totalElements || 0);
        }
        this.enrichJobsWithPipelineNames(data);
        this.loading.set(false);
        this.backendAvailable.set(true);
        this.initialLoadComplete.set(true);
      },
      error: (err) => {
        this.logger.error('Failed to load jobs:', err);
        this.loading.set(false);
        this.backendAvailable.set(false);
        this.initialLoadComplete.set(true);
        this.jobs.set([]);
        this.snackBar.open('Failed to load jobs. Click retry to try again.', 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => this.loadJobs());
      }
    });
  }

  refreshJobs(): void {
    this.refreshing.set(true);
    this.jobService.list({
      page: this.pageIndex,
      size: this.pageSize,
      sort: `${this.sortField},${this.sortDirection}`
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response: Job[] | SpringPage<Job>) => {
        let data: Job[];
        if (Array.isArray(response)) {
          data = response;
          this.totalElements.set(response.length);
        } else {
          data = response.content || [];
          this.totalElements.set(response.totalElements || 0);
        }
        this.enrichJobsWithPipelineNames(data);
        this.refreshing.set(false);
        this.snackBar.open('Jobs refreshed', 'Close', { duration: 2000 });
      },
      error: () => {
        this.refreshing.set(false);
        this.snackBar.open('Failed to refresh jobs', 'Close', { duration: 3000 });
      }
    });
  }

  loadPipelines(): void {
    this.pipelineService.list().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.pipelines.set(data);
        // Re-enrich jobs with pipeline names if jobs are already loaded
        const currentJobs = this.jobs();
        if (currentJobs.length > 0) {
          this.enrichJobsWithPipelineNames(currentJobs);
        }
      },
      error: (err) => this.logger.error('Failed to load pipelines:', err)
    });
  }

  private enrichJobsWithPipelineNames(jobs: Job[]): void {
    const pipelineNameMap = new Map(this.pipelines().map(p => [p.id, p.name]));
    const enrichedJobs = jobs.map(job => ({
      ...job,
      pipelineName: pipelineNameMap.get(job.pipelineId) || job.pipelineName || 'Unknown Pipeline'
    }));
    this.jobs.set(enrichedJobs);
  }

  onSearchChange(value: string): void {
    this.searchSubject.next(value);
  }

  onStatusFilterChange(status: JobStatus): void {
    const current = this.statusFilters();
    const index = current.indexOf(status);
    if (index === -1) {
      this.statusFilters.set([...current, status]);
    } else {
      this.statusFilters.set(current.filter(s => s !== status));
    }
    this.pageIndex = 0;
    if (this.paginator()) {
      this.paginator()!.pageIndex = 0;
    }
  }

  clearStatusFilters(): void {
    this.statusFilters.set([]);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
  }

  onSortChange(sort: Sort): void {
    this.sortField = sort.active || 'startedAt';
    this.sortDirection = sort.direction || 'desc';
    // Reload jobs with new sort
    this.loadJobs();
  }

  // Selection methods
  toggleAllRows(): void {
    if (this.isAllSelected()) {
      this.filteredJobs().forEach(job => this.selection.deselect(job));
    } else {
      this.filteredJobs().forEach(job => this.selection.select(job));
    }
  }

  toggleRow(job: Job): void {
    this.selection.toggle(job);
  }

  // Bulk actions
  cancelSelected(): void {
    const selected = this.selection.selected.filter(j => j.status?.toLowerCase() === 'running');
    if (selected.length === 0) {
      this.snackBar.open('No running jobs selected', 'Close', { duration: 3000 });
      return;
    }

    this.confirmationService.confirm({
      title: 'Cancel Jobs',
      message: `Are you sure you want to cancel ${selected.length} job(s)?`,
      confirmText: 'Cancel Jobs',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (!confirmed) return;
      let completed = 0;
      selected.forEach(job => {
        this.jobService.cancel(job.id).subscribe({
          next: () => {
            completed++;
            if (completed === selected.length) {
              this.snackBar.open(`${completed} job(s) cancelled`, 'Close', { duration: 3000 });
              this.selection.clear();
              this.loadJobs();
            }
          },
          error: () => {
            completed++;
          }
        });
      });
    });
  }

  retrySelected(): void {
    const selected = this.selection.selected.filter(j => j.status?.toLowerCase() === 'failed');
    if (selected.length === 0) {
      this.snackBar.open('No failed jobs selected', 'Close', { duration: 3000 });
      return;
    }

    let completed = 0;
    selected.forEach(job => {
      this.jobService.retry(job.id).subscribe({
        next: () => {
          completed++;
          if (completed === selected.length) {
            this.snackBar.open(`${completed} job(s) retried`, 'Close', { duration: 3000 });
            this.selection.clear();
            this.loadJobs();
          }
        },
        error: () => {
          completed++;
        }
      });
    });
  }

  deleteSelected(): void {
    const selected = this.selection.selected;
    if (selected.length === 0) return;

    this.confirmationService.confirm({
      title: 'Delete Jobs',
      message: `Are you sure you want to delete ${selected.length} job(s)?`,
      confirmText: 'Delete',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (!confirmed) return;
      let completed = 0;
      selected.forEach(job => {
        this.jobService.delete(job.id).subscribe({
          next: () => {
            completed++;
            if (completed === selected.length) {
              this.snackBar.open(`${completed} job(s) deleted`, 'Close', { duration: 3000 });
              this.selection.clear();
              this.loadJobs();
            }
          },
          error: () => {
            completed++;
          }
        });
      });
    });
  }

  openJob(job: Job): void {
    this.router.navigate(['/jobs', job.id]);
  }

  viewDetails(job: Job, event: Event): void {
    event.stopPropagation();
    this.selectedJob.set(job);
    this.detailsDialogVisible.set(true);
  }

  viewLogs(job: Job, event: Event): void {
    event.stopPropagation();
    this.selectedJob.set(job);
    this.logsDialogVisible.set(true);
    this.loadLogs(job.id);
  }

  loadLogs(jobId: string): void {
    this.logsLoading.set(true);
    this.jobService.getLogs(jobId, { limit: 200 }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.jobLogs.set(data);
        this.logsLoading.set(false);
      },
      error: (err) => {
        this.logger.error('Failed to load logs:', err);
        this.logsLoading.set(false);
        this.snackBar.open('Failed to load logs', 'Close', { duration: 3000 });
      }
    });
  }

  cancelJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      title: 'Cancel Job',
      message: `Are you sure you want to cancel job ${job.id.substring(0, 8)}?`,
      confirmText: 'Cancel Job',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.cancel(job.id).pipe(
        takeUntil(this.destroy$)
      ).subscribe({
        next: () => {
          this.snackBar.open('Job cancelled successfully', 'Close', { duration: 3000 });
          this.loadJobs();
        },
        error: () => {
          this.snackBar.open('Failed to cancel job', 'Close', { duration: 3000 });
        }
      });
    });
  }

  retryJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.jobService.retry(job.id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (newJob) => {
        this.snackBar.open('Job retry started', 'Close', { duration: 3000 });
        this.loadJobs();
        this.router.navigate(['/jobs', newJob.id]);
      },
      error: () => {
        this.snackBar.open('Failed to retry job', 'Close', { duration: 3000 });
      }
    });
  }

  openNewJobDialog(): void {
    this.selectedPipelineId.set(null);
    this.newJobDialogVisible.set(true);
  }

  closeNewJobDialog(): void {
    this.newJobDialogVisible.set(false);
  }

  createJob(): void {
    const pipelineId = this.selectedPipelineId();
    if (!pipelineId) {
      this.snackBar.open('Please select a pipeline', 'Close', { duration: 3000 });
      return;
    }

    this.creatingJob.set(true);
    this.jobService.create(pipelineId, {}).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (job) => {
        this.snackBar.open('Job created and queued', 'Close', { duration: 3000 });
        this.creatingJob.set(false);
        this.newJobDialogVisible.set(false);
        this.loadJobs();
        this.router.navigate(['/jobs', job.id]);
      },
      error: (err) => {
        this.logger.error('Failed to create job:', err);
        this.snackBar.open('Failed to create job', 'Close', { duration: 3000 });
        this.creatingJob.set(false);
      }
    });
  }

  closeDetailsDialog(): void {
    this.detailsDialogVisible.set(false);
  }

  closeLogsDialog(): void {
    this.logsDialogVisible.set(false);
  }

  getStatusClass(status: string): string {
    return 'status-' + status?.toLowerCase();
  }

  formatDate(date: Date | undefined): string {
    if (!date) return 'Pending';
    return new Date(date).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatFullDate(date: Date | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  formatDuration(ms: number | undefined): string {
    if (!ms) return '-';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${Math.round(ms / 1000)}s`;
    if (ms < 3600000) return `${Math.round(ms / 60000)}m`;
    return `${Math.round(ms / 3600000)}h`;
  }

  formatNumber(num: number | undefined): string {
    if (!num) return '0';
    if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`;
    if (num >= 1000) return `${(num / 1000).toFixed(1)}K`;
    return num.toString();
  }

  getRunningTime(startedAt: Date | undefined): string {
    if (!startedAt) return '-';
    const elapsed = Date.now() - new Date(startedAt).getTime();
    return this.formatDuration(elapsed);
  }

  copyJobId(job: Job, event: Event): void {
    event.stopPropagation();
    navigator.clipboard.writeText(job.id).then(() => {
      this.snackBar.open('Job ID copied to clipboard', 'Close', { duration: 2000 });
    });
  }

  hasVariables(variables: Record<string, unknown> | undefined): boolean {
    return variables ? Object.keys(variables).length > 0 : false;
  }

  getVariableKeys(variables: Record<string, unknown>): string[] {
    return Object.keys(variables);
  }

  getLogClass(level: string): string {
    return 'log-' + level.toLowerCase();
  }
}
