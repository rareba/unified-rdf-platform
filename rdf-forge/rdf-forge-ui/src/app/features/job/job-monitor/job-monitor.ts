import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { JobService } from '../../../core/services';
import { Job, JobLog } from '../../../core/models';

@Component({
  selector: 'app-job-monitor',
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
    MatIconModule,
    MatChipsModule
  ],
  templateUrl: './job-monitor.html',
  styleUrl: './job-monitor.scss',
})
export class JobMonitor implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly snackBar = inject(MatSnackBar);
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

  logsError = signal(false);

  loadLogs(id: string): void {
    this.logsError.set(false);
    this.jobService.getLogs(id, { limit: 100 }).subscribe({
      next: (data) => this.logs.set(data),
      error: () => {
        this.logsError.set(true);
        // Only show error on first load, not during polling
        if (this.logs().length === 0) {
          this.snackBar.open('Failed to load job logs', 'Close', { duration: 3000 });
        }
      }
    });
  }

  cancelJob(): void {
    const job = this.job();
    if (!job) return;

    this.jobService.cancel(job.id).subscribe({
      next: () => {
        this.snackBar.open('Job cancelled', 'Close', { duration: 3000 });
        this.loadJob(job.id);
      },
      error: () => {
        this.snackBar.open('Failed to cancel job', 'Close', { duration: 3000 });
      }
    });
  }

  retryJob(): void {
    const job = this.job();
    if (!job) return;

    this.jobService.retry(job.id).subscribe({
      next: (newJob) => {
        this.snackBar.open('Job retry started', 'Close', { duration: 3000 });
        this.router.navigate(['/jobs', newJob.id]);
      },
      error: () => {
        this.snackBar.open('Failed to retry job', 'Close', { duration: 3000 });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/jobs']);
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'running': return 'status-info';
      case 'completed': return 'status-success';
      case 'failed': return 'status-error';
      case 'cancelled': return 'status-warn';
      default: return 'status-default';
    }
  }

  getLogClass(level: string): string {
    switch (level) {
      case 'debug': return 'log-debug';
      case 'info': return 'log-info';
      case 'warn': return 'log-warn';
      case 'error': return 'log-error';
      default: return 'log-info';
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
