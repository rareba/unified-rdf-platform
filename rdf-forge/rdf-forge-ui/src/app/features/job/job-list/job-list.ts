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
import { MessageService } from 'primeng/api';
import { JobService } from '../../../core/services';
import { Job } from '../../../core/models';

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
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
})
export class JobList implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly messageService = inject(MessageService);
  private refreshInterval: ReturnType<typeof setInterval> | undefined;

  loading = signal(false);
  searchQuery = signal('');
  statusFilter = signal<string | null>(null);
  jobs = signal<Job[]>([]);

  statusOptions = [
    { label: 'All', value: null },
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

  runningCount = computed(() => this.jobs().filter(j => j.status === 'running').length);
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

  ngOnInit(): void {
    this.loadJobs();
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
      error: () => {
        this.loading.set(false);
      }
    });
  }

  openJob(job: Job): void {
    this.router.navigate(['/jobs', job.id]);
  }

  cancelJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.jobService.cancel(job.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Cancelled', detail: 'Job cancelled' });
        this.loadJobs();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to cancel job' });
      }
    });
  }

  retryJob(job: Job, event: Event): void {
    event.stopPropagation();
    this.jobService.retry(job.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Retrying', detail: 'Job retry started' });
        this.loadJobs();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to retry job' });
      }
    });
  }

  viewLogs(job: Job, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/jobs', job.id, 'logs']);
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

  getTriggerSeverity(trigger: string): 'info' | 'success' | 'warn' | 'secondary' {
    switch (trigger) {
      case 'manual': return 'info';
      case 'schedule': return 'success';
      case 'api': return 'warn';
      default: return 'secondary';
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
}
