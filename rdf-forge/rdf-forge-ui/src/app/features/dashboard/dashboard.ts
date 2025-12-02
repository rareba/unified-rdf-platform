import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { forkJoin, catchError, of } from 'rxjs';
import { PipelineService, JobService, DataService, ShaclService } from '../../core/services';
import { Job } from '../../core/models';

interface DashboardStats {
  pipelines: number;
  completedJobs: number;
  shapes: number;
  dataSources: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, CardModule, TableModule, TagModule, ButtonModule, SkeletonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly jobService = inject(JobService);
  private readonly dataService = inject(DataService);
  private readonly shaclService = inject(ShaclService);

  loading = signal(true);
  stats = signal<DashboardStats>({ pipelines: 0, completedJobs: 0, shapes: 0, dataSources: 0 });
  recentJobs = signal<Job[]>([]);

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);

    forkJoin({
      pipelines: this.pipelineService.list().pipe(catchError(() => of([]))),
      jobs: this.jobService.list().pipe(catchError(() => of([]))),
      shapes: this.shaclService.list().pipe(catchError(() => of([]))),
      dataSources: this.dataService.list().pipe(catchError(() => of([])))
    }).subscribe(({ pipelines, jobs, shapes, dataSources }) => {
      this.stats.set({
        pipelines: pipelines.length,
        completedJobs: jobs.filter(j => j.status === 'completed').length,
        shapes: shapes.length,
        dataSources: dataSources.length
      });
      this.recentJobs.set(jobs.slice(0, 5));
      this.loading.set(false);
    });
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary'> = {
      completed: 'success',
      running: 'info',
      failed: 'danger',
      pending: 'warn',
      cancelled: 'secondary'
    };
    return map[status] || 'info';
  }

  formatDate(date: Date | undefined): string {
    if (!date) return '-';
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(date));
  }

  formatDuration(ms: number | undefined): string {
    if (!ms) return '-';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${Math.round(ms / 1000)}s`;
    if (ms < 3600000) return `${Math.round(ms / 60000)}m`;
    return `${Math.round(ms / 3600000)}h`;
  }

  navigateTo(path: string): void {
    this.router.navigate([path]);
  }
}
