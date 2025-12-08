import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
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
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatChipsModule
  ],
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

  displayedColumns = ['pipelineName', 'status', 'startedAt', 'duration'];

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

      // Create pipeline name lookup map
      const pipelineNameMap = new Map(pipelines.map(p => [p.id, p.name]));

      // Enrich jobs with pipeline names
      const enrichedJobs = jobs.slice(0, 5).map(job => ({
        ...job,
        pipelineName: pipelineNameMap.get(job.pipelineId) || 'Unknown Pipeline'
      }));

      this.recentJobs.set(enrichedJobs);
      this.loading.set(false);
    });
  }

  getStatusColor(status: string): string {
    const map: Record<string, string> = {
      completed: 'primary',
      running: 'accent',
      failed: 'warn',
      pending: '',
      cancelled: ''
    };
    return map[status] || '';
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
