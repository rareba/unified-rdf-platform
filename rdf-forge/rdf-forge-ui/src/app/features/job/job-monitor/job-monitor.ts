import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ProgressBarModule } from 'primeng/progressbar';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { JobService } from '../../../core/services';
import { Job, JobLog } from '../../../core/models';

@Component({
  selector: 'app-job-monitor',
  imports: [
    CommonModule,
    CardModule,
    TagModule,
    ButtonModule,
    ProgressBarModule,
    TableModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './job-monitor.html',
  styleUrl: './job-monitor.scss',
})
export class JobMonitor implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly messageService = inject(MessageService);
  private refreshInterval: ReturnType<typeof setInterval> | undefined;

  loading = signal(true);
  job = signal<Job | null>(null);
  logs = signal<JobLog[]>([]);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadJob(id);
      this.loadLogs(id);
      this.refreshInterval = setInterval(() => {
        if (this.job()?.status === 'running') {
          this.loadJob(id);
          this.loadLogs(id);
        }
      }, 3000);
    }
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  loadJob(id: string): void {
    this.jobService.get(id).subscribe({
      next: (data) => {
        this.job.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  loadLogs(id: string): void {
    this.jobService.getLogs(id, { limit: 100 }).subscribe({
      next: (data) => this.logs.set(data),
      error: () => { /* ignored */ }
    });
  }

  cancelJob(): void {
    const job = this.job();
    if (!job) return;

    this.jobService.cancel(job.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Cancelled', detail: 'Job cancelled' });
        this.loadJob(job.id);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to cancel job' });
      }
    });
  }

  retryJob(): void {
    const job = this.job();
    if (!job) return;

    this.jobService.retry(job.id).subscribe({
      next: (newJob) => {
        this.messageService.add({ severity: 'success', summary: 'Retrying', detail: 'Job retry started' });
        this.router.navigate(['/jobs', newJob.id]);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to retry job' });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/jobs']);
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

  getLogSeverity(level: string): 'info' | 'success' | 'warn' | 'danger' | 'secondary' {
    switch (level) {
      case 'debug': return 'secondary';
      case 'info': return 'info';
      case 'warn': return 'warn';
      case 'error': return 'danger';
      default: return 'info';
    }
  }

  formatDate(date: Date | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleString();
  }

  formatDuration(ms: number | undefined): string {
    if (!ms) return '-';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${Math.round(ms / 1000)}s`;
    if (ms < 3600000) return `${Math.round(ms / 60000)}m`;
    return `${Math.round(ms / 3600000)}h`;
  }
}
