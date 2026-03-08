import { Component, inject, OnInit, OnDestroy, signal, viewChild, ElementRef, AfterViewChecked, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { Subscription, Subject, takeUntil } from 'rxjs';
import { JobService, ConnectionStatus, LogStreamMessage } from '../../../core/services';
import { Job, JobLog } from '../../../core/models';
import { SkeletonLoaderComponent } from '../../../shared/components/skeleton-loader/skeleton-loader';
import { LoggerService } from '../../../core/services/logger.service';

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

// Extended interface for WebSocket messages with index signature
interface WebSocketMessage extends LogStreamMessage {
  [key: string]: unknown;
}

interface LogFilter {
  level: LogLevel | null;
  search: string;
}

@Component({
  selector: 'app-job-monitor',
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatBadgeModule,
    MatSnackBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonToggleModule,
    SkeletonLoaderComponent
  ],
  templateUrl: './job-monitor.html',
  styleUrl: './job-monitor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobMonitor implements OnInit, OnDestroy, AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly jobService = inject(JobService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly logger = inject(LoggerService);

  readonly logsContainer = viewChild<ElementRef<HTMLDivElement>>('logsContainer');

  loading = signal(true);
  job = signal<Job | null>(null);
  logs = signal<JobLog[]>([]);
  filteredLogs = signal<JobLog[]>([]);
  connectionStatus = signal<ConnectionStatus>({ connected: false, reconnecting: false });
  autoScroll = signal(true);
  newLogCount = signal(0);

  // Filters
  levelFilter = signal<LogLevel | null>(null);
  searchQuery = signal('');
  logLevels: { value: LogLevel; label: string; color: string }[] = [
    { value: 'DEBUG', label: 'Debug', color: '#9e9e9e' },
    { value: 'INFO', label: 'Info', color: '#2196f3' },
    { value: 'WARN', label: 'Warn', color: '#ff9800' },
    { value: 'ERROR', label: 'Error', color: '#f44336' }
  ];

  private logSubscription: Subscription | null = null;
  private statusSubscription: Subscription | null = null;
  private routeSubscription: Subscription | null = null;
  private destroy$ = new Subject<void>();
  private shouldScroll = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadJob(id);
      this.connectToWebSocket(id);
    }

    // Subscribe to filter changes
    this.setupFilters();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll && this.autoScroll()) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.logSubscription?.unsubscribe();
    this.statusSubscription?.unsubscribe();
    this.routeSubscription?.unsubscribe();
    this.jobService.disconnect();
  }

  private setupFilters(): void {
    // Re-filter logs when filters change
    this.levelFilter.pipe(takeUntil(this.destroy$)).subscribe(() => this.applyFilters());
    this.searchQuery.pipe(takeUntil(this.destroy$)).subscribe(() => this.applyFilters());
  }

  private applyFilters(): void {
    let filtered = this.logs();
    const level = this.levelFilter();
    const search = this.searchQuery().toLowerCase().trim();

    if (level) {
      filtered = filtered.filter(log => log.level?.toUpperCase() === level);
    }

    if (search) {
      filtered = filtered.filter(log =>
        log.message?.toLowerCase().includes(search) ||
        log.step?.toLowerCase().includes(search) ||
        log.level?.toLowerCase().includes(search)
      );
    }

    this.filteredLogs.set(filtered);
  }

  setLevelFilter(level: LogLevel | null): void {
    this.levelFilter.set(level);
  }

  clearSearch(): void {
    this.searchQuery.set('');
  }

  private loadJob(id: string): void {
    this.jobService.get(id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.job.set(data);
        this.loading.set(false);

        // Load initial logs if job is not running
        if (data.status !== 'running') {
          this.loadHistoricalLogs(id);
        }
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load job details', 'Close', { duration: 3000 });
      }
    });
  }

  private loadHistoricalLogs(id: string): void {
    this.jobService.getLogs(id, { limit: 1000 }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (data) => {
        this.logs.set(data);
        this.applyFilters();
        this.shouldScroll = true;
      },
      error: () => {
        this.snackBar.open('Failed to load job logs', 'Close', { duration: 3000 });
      }
    });
  }

  private connectToWebSocket(jobId: string): void {
    // Subscribe to connection status
    this.statusSubscription = this.jobService.connectionStatus$.pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (status) => {
        this.connectionStatus.set(status);
      }
    });

    // Subscribe to log stream
    this.logSubscription = this.jobService.logStream$.pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (message) => {
        this.handleWebSocketMessage(message as WebSocketMessage);
      },
      error: (error) => {
        this.logger.error('WebSocket error:', error);
      }
    });

    // Connect to WebSocket
    this.jobService.connectToJobLogs(jobId);
  }

  private handleWebSocketMessage(message: WebSocketMessage): void {
    const job = this.job();

    switch (message.type) {
      case 'subscription':
        // Initial subscription confirmation with historical logs
        if (message['historicalLogs'] && Array.isArray(message['historicalLogs'])) {
          const historicalLogs = message['historicalLogs'] as JobLog[];
          if (historicalLogs.length > 0) {
            this.logs.set(historicalLogs);
            this.applyFilters();
            this.shouldScroll = true;
          }
        }
        break;

      case 'historical':
        // Historical logs response
        if (message['logs'] && Array.isArray(message['logs'])) {
          const logs = message['logs'] as JobLog[];
          this.logs.set(logs);
          this.applyFilters();
          this.shouldScroll = true;
        }
        break;

      case 'log':
        // New log entry
        const newLog: JobLog = {
          id: (message['id'] as string) || `ws-${Date.now()}`,
          timestamp: new Date(message['timestamp'] as string),
          level: (message['level'] as JobLog['level']) || 'info',
          step: message['step'] as string | undefined,
          message: (message['message'] as string) || '',
          details: message['details'] as Record<string, unknown> | undefined
        };

        this.logs.update(current => {
          // Check if log already exists to avoid duplicates
          const exists = current.some(l => l.id === newLog.id);
          if (exists) return current;
          return [...current, newLog];
        });

        this.applyFilters();

        if (!this.autoScroll()) {
          this.newLogCount.update(count => count + 1);
        }
        this.shouldScroll = true;
        break;

      case 'status':
        // Status update
        if (job && message['status']) {
          this.job.set({
            ...job,
            status: message['status'] as Job['status'],
            progress: (message['progress'] as number) ?? job.progress
          });
        }
        break;

      case 'completion':
        // Job completion
        if (job) {
          this.job.set({
            ...job,
            status: (message['success'] as boolean) ? 'completed' : 'failed',
            progress: 100
          });
        }
        // Refresh job details to get final state
        this.loadJob(job!.id);
        break;
    }
  }

  private scrollToBottom(): void {
    const container = this.logsContainer()?.nativeElement;
    if (container) {
      container.scrollTop = container.scrollHeight;
      this.newLogCount.set(0);
    }
  }

  onScroll(): void {
    const container = this.logsContainer()?.nativeElement;
    if (container) {
      const isAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 50;
      this.autoScroll.set(isAtBottom);
      if (isAtBottom) {
        this.newLogCount.set(0);
      }
    }
  }

  scrollToBottomManual(): void {
    this.autoScroll.set(true);
    this.scrollToBottom();
  }

  cancelJob(): void {
    const job = this.job();
    if (!job) return;

    this.jobService.cancel(job.id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
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

    this.jobService.retry(job.id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
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

  downloadLogs(): void {
    const logs = this.logs();
    const job = this.job();
    if (!logs.length || !job) return;

    const content = logs.map(log =>
      `[${this.formatDate(log.timestamp)}] [${log.level.toUpperCase()}]${log.step ? ` [${log.step}]` : ''} ${log.message}`
    ).join('\n');

    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `job-${job.id.substring(0, 8)}-logs.txt`;
    a.click();
    URL.revokeObjectURL(url);

    this.snackBar.open('Logs downloaded', 'Close', { duration: 2000 });
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
    switch (level?.toLowerCase()) {
      case 'debug': return 'log-debug';
      case 'info': return 'log-info';
      case 'warn': return 'log-warn';
      case 'error': return 'log-error';
      default: return 'log-info';
    }
  }

  getConnectionStatusClass(): string {
    const status = this.connectionStatus();
    if (status.connected) return 'status-success';
    if (status.reconnecting) return 'status-warn';
    return 'status-error';
  }

  getConnectionStatusIcon(): string {
    const status = this.connectionStatus();
    if (status.connected) return 'cloud_done';
    if (status.reconnecting) return 'sync';
    return 'cloud_off';
  }

  getConnectionStatusText(): string {
    const status = this.connectionStatus();
    if (status.connected) return 'Connected';
    if (status.reconnecting) return 'Reconnecting...';
    return 'Disconnected';
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
