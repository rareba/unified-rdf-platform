import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { JobService, PipelineService } from '../../../core/services';
import { Job, JobLog, Pipeline } from '../../../core/models';

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
    MatSnackBarModule
  ],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
})
export class JobList implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly pipelineService = inject(PipelineService);
  private readonly snackBar = inject(MatSnackBar);
  private refreshInterval: ReturnType<typeof setInterval> | undefined;

  loading = signal(false);
  searchQuery = signal('');
  statusFilter = signal<string | null>(null);
  jobs = signal<Job[]>([]);
  pipelines = signal<Pipeline[]>([]);
  backendAvailable = signal(true);
  initialLoadComplete = signal(false);

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
  displayedColumns = ['id', 'pipeline', 'status', 'progress', 'metrics', 'startedAt', 'duration', 'actions'];
  pageSize = 15;
  pageIndex = 0;

  statusOptions = [
    { label: 'All Status', value: null },
    { label: 'Running', value: 'running' },
    { label: 'Completed', value: 'completed' },
    { label: 'Failed', value: 'failed' },
    { label: 'Pending', value: 'pending' },
    { label: 'Cancelled', value: 'cancelled' }
  ];

  filteredJobs = computed(() => {
    let result = this.jobs();
    const query = this.searchQuery().toLowerCase();
    if (query) {
      result = result.filter(j =>
        j.id.toLowerCase().includes(query) ||
        j.pipelineName.toLowerCase().includes(query)
      );
    }
    const status = this.statusFilter();
    if (status) {
      result = result.filter(j => j.status === status);
    }
    return result;
  });

  pagedJobs = computed(() => {
    const filtered = this.filteredJobs();
    const start = this.pageIndex * this.pageSize;
    return filtered.slice(start, start + this.pageSize);
  });

  // Stats computed properties
  runningCount = computed(() => this.jobs().filter(j => j.status === 'running').length);
  pendingCount = computed(() => this.jobs().filter(j => j.status === 'pending').length);
  completedToday = computed(() => {
    const today = new Date().toDateString();
    return this.jobs().filter(j => j.status === 'completed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length;
  });
  failedToday = computed(() => {
    const today = new Date().toDateString();
    return this.jobs().filter(j => j.status === 'failed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length;
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

  ngOnInit(): void {
    this.loadJobs();
    this.loadPipelines();
    this.refreshInterval = setInterval(() => this.loadJobs(), 5000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  loadJobs(): void {
    this.loading.set(true);
    this.jobService.list().subscribe({
      next: (data) => {
        this.enrichJobsWithPipelineNames(data);
        this.loading.set(false);
        this.backendAvailable.set(true);
        this.initialLoadComplete.set(true);
      },
      error: (err) => {
        console.error('Failed to load jobs:', err);
        this.loading.set(false);
        this.backendAvailable.set(false);
        this.initialLoadComplete.set(true);
        this.jobs.set([]);
      }
    });
  }

  loadPipelines(): void {
    this.pipelineService.list().subscribe({
      next: (data) => {
        this.pipelines.set(data);
        // Re-enrich jobs with pipeline names if jobs are already loaded
        const currentJobs = this.jobs();
        if (currentJobs.length > 0) {
          this.enrichJobsWithPipelineNames(currentJobs);
        }
      },
      error: (err) => console.error('Failed to load pipelines:', err)
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

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
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
    this.jobService.getLogs(jobId, { limit: 200 }).subscribe({
      next: (data) => {
        this.jobLogs.set(data);
        this.logsLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load logs:', err);
        this.logsLoading.set(false);
      }
    });
  }

  cancelJob(job: Job, event: Event): void {
    event.stopPropagation();
    if (confirm(`Are you sure you want to cancel job ${job.id.substring(0, 8)}?`)) {
      this.jobService.cancel(job.id).subscribe({
        next: () => {
          this.snackBar.open('Job cancelled successfully', 'Close', { duration: 3000 });
          this.loadJobs();
        },
        error: () => {
          this.snackBar.open('Failed to cancel job', 'Close', { duration: 3000 });
        }
      });
    }
  }

  retryJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.jobService.retry(job.id).subscribe({
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
    this.jobService.create(pipelineId, {}).subscribe({
      next: (job) => {
        this.snackBar.open('Job created and queued', 'Close', { duration: 3000 });
        this.creatingJob.set(false);
        this.newJobDialogVisible.set(false);
        this.loadJobs();
        this.router.navigate(['/jobs', job.id]);
      },
      error: (err) => {
        console.error('Failed to create job:', err);
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
    return 'status-' + status;
  }

  formatDate(date: Date | undefined): string {
    if (!date) return 'Pending';
    return new Date(date).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatFullDate(date: Date | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleString('en-US', {
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
    return 'log-' + level;
  }
}
