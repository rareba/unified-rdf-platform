import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { CardModule } from 'primeng/card';
import { TabsModule } from 'primeng/tabs';
import { TimelineModule } from 'primeng/timeline';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageService, ConfirmationService } from 'primeng/api';
import { JobService, PipelineService } from '../../../core/services';
import { Job, JobLog, Pipeline } from '../../../core/models';

@Component({
  selector: 'app-job-list',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    ProgressBarModule,
    TooltipModule,
    ToastModule,
    DialogModule,
    CardModule,
    TabsModule,
    TimelineModule,
    ConfirmDialogModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
})
export class JobList implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly pipelineService = inject(PipelineService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private refreshInterval: ReturnType<typeof setInterval> | undefined;

  loading = signal(false);
  searchQuery = signal('');
  statusFilter = signal<string | null>(null);
  jobs = signal<Job[]>([]);
  pipelines = signal<Pipeline[]>([]);

  // Dialogs
  logsDialogVisible = signal(false);
  detailsDialogVisible = signal(false);
  newJobDialogVisible = signal(false);
  selectedJob = signal<Job | null>(null);
  jobLogs = signal<JobLog[]>([]);
  logsLoading = signal(false);
  selectedPipelineId = signal<string | null>(null);
  creatingJob = signal(false);

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
        this.jobs.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load jobs:', err);
        this.loading.set(false);
      }
    });
  }

  loadPipelines(): void {
    this.pipelineService.list().subscribe({
      next: (data) => this.pipelines.set(data),
      error: (err) => console.error('Failed to load pipelines:', err)
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
    this.confirmationService.confirm({
      message: `Are you sure you want to cancel job ${job.id.substring(0, 8)}?`,
      header: 'Confirm Cancel',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.jobService.cancel(job.id).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Cancelled', detail: 'Job cancelled successfully' });
            this.loadJobs();
          },
          error: () => {
            this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to cancel job' });
          }
        });
      }
    });
  }

  retryJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.jobService.retry(job.id).subscribe({
      next: (newJob) => {
        this.messageService.add({ severity: 'success', summary: 'Retrying', detail: 'Job retry started' });
        this.loadJobs();
        this.router.navigate(['/jobs', newJob.id]);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to retry job' });
      }
    });
  }

  openNewJobDialog(): void {
    this.selectedPipelineId.set(null);
    this.newJobDialogVisible.set(true);
  }

  createJob(): void {
    const pipelineId = this.selectedPipelineId();
    if (!pipelineId) {
      this.messageService.add({ severity: 'warn', summary: 'Warning', detail: 'Please select a pipeline' });
      return;
    }

    this.creatingJob.set(true);
    this.jobService.create(pipelineId, {}).subscribe({
      next: (job) => {
        this.messageService.add({ severity: 'success', summary: 'Created', detail: 'Job created and queued' });
        this.creatingJob.set(false);
        this.newJobDialogVisible.set(false);
        this.loadJobs();
        this.router.navigate(['/jobs', job.id]);
      },
      error: (err) => {
        console.error('Failed to create job:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create job' });
        this.creatingJob.set(false);
      }
    });
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'running': return 'info';
      case 'completed': return 'success';
      case 'failed': return 'danger';
      case 'cancelled': return 'warn';
      default: return 'secondary';
    }
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'running': return 'pi pi-spin pi-spinner';
      case 'completed': return 'pi pi-check';
      case 'failed': return 'pi pi-times';
      case 'cancelled': return 'pi pi-ban';
      default: return 'pi pi-clock';
    }
  }

  getTriggerIcon(trigger: string): string {
    switch (trigger) {
      case 'manual': return 'pi pi-user';
      case 'schedule': return 'pi pi-calendar';
      case 'api': return 'pi pi-code';
      default: return 'pi pi-question';
    }
  }

  getTriggerSeverity(trigger: string): 'info' | 'success' | 'warn' | 'secondary' {
    switch (trigger) {
      case 'manual': return 'info';
      case 'schedule': return 'success';
      case 'api': return 'warn';
      default: return 'secondary';
    }
  }

  getLogSeverity(level: string): 'info' | 'success' | 'warn' | 'danger' | 'secondary' {
    switch (level) {
      case 'debug': return 'secondary';
      case 'info': return 'info';
      case 'warn': return 'warn';
      case 'error': return 'danger';
      default: return 'info';
    }
  }

  getStepIcon(status: string): string {
    switch (status) {
      case 'running': return 'pi pi-spin pi-spinner';
      case 'completed': return 'pi pi-check-circle';
      case 'failed': return 'pi pi-times-circle';
      case 'skipped': return 'pi pi-forward';
      default: return 'pi pi-circle';
    }
  }

  getStepColor(status: string): string {
    switch (status) {
      case 'running': return '#3b82f6';
      case 'completed': return '#22c55e';
      case 'failed': return '#ef4444';
      case 'skipped': return '#94a3b8';
      default: return '#cbd5e1';
    }
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
      this.messageService.add({ severity: 'info', summary: 'Copied', detail: 'Job ID copied to clipboard' });
    });
  }

  // Template helpers for Object operations
  hasVariables(variables: Record<string, unknown> | undefined): boolean {
    return variables ? Object.keys(variables).length > 0 : false;
  }

  getVariableKeys(variables: Record<string, unknown>): string[] {
    return Object.keys(variables);
  }
}
