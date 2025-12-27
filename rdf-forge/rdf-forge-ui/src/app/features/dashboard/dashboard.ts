import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { forkJoin, catchError, of } from 'rxjs';
import { PipelineService, JobService, DataService, ShaclService } from '../../core/services';
import { Job, Operation } from '../../core/models';

interface DashboardStats {
  pipelines: number;
  completedJobs: number;
  shapes: number;
  dataSources: number;
}

interface OperationGroup {
  type: string;
  label: string;
  icon: string;
  count: number;
  operations: Operation[];
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
    MatChipsModule,
    MatDividerModule
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
  operations = signal<Operation[]>([]);

  displayedColumns = ['pipelineName', 'status', 'startedAt', 'duration'];

  // Computed signals for new dashboard features
  isNewUser = computed(() => {
    const s = this.stats();
    return s.pipelines === 0 && s.completedJobs === 0 && s.shapes === 0 && s.dataSources === 0;
  });

  operationsCount = computed(() => this.operations().length);

  operationGroups = computed(() => {
    const ops = this.operations();
    const groups: Record<string, OperationGroup> = {};

    const typeConfig: Record<string, { label: string; icon: string }> = {
      'SOURCE': { label: 'Data Sources', icon: 'storage' },
      'TRANSFORM': { label: 'Transformations', icon: 'transform' },
      'CUBE': { label: 'Cube Operations', icon: 'view_in_ar' },
      'VALIDATION': { label: 'Validation', icon: 'verified' },
      'OUTPUT': { label: 'Destinations', icon: 'cloud_upload' }
    };

    ops.forEach(op => {
      const type = op.type || 'TRANSFORM';
      if (!groups[type]) {
        const config = typeConfig[type] || { label: type, icon: 'extension' };
        groups[type] = {
          type,
          label: config.label,
          icon: config.icon,
          count: 0,
          operations: []
        };
      }
      groups[type].count++;
      groups[type].operations.push(op);
    });

    return Object.values(groups).sort((a, b) => b.count - a.count);
  });

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);

    forkJoin({
      pipelines: this.pipelineService.list().pipe(catchError(() => of([]))),
      jobs: this.jobService.list().pipe(catchError(() => of([]))),
      shapes: this.shaclService.list().pipe(catchError(() => of([]))),
      dataSources: this.dataService.list().pipe(catchError(() => of([]))),
      operations: this.pipelineService.getOperations().pipe(catchError(() => of([])))
    }).subscribe(({ pipelines, jobs, shapes, dataSources, operations }) => {
      this.stats.set({
        pipelines: pipelines.length,
        completedJobs: jobs.filter(j => j.status === 'completed').length,
        shapes: shapes.length,
        dataSources: dataSources.length
      });

      this.operations.set(operations);

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

  startWorkflow(workflowType: string): void {
    switch (workflowType) {
      case 'csv-to-cube':
        this.router.navigate(['/cubes/new']);
        break;
      case 'validate-cube':
        this.router.navigate(['/shacl'], { queryParams: { action: 'validate' } });
        break;
      case 'publish-graphdb':
        this.router.navigate(['/pipelines/new'], { queryParams: { template: 'publish' } });
        break;
      default:
        this.router.navigate(['/pipelines/new']);
    }
  }

  getGroupColor(type: string): string {
    const colors: Record<string, string> = {
      'SOURCE': '#3b82f6',
      'TRANSFORM': '#8b5cf6',
      'CUBE': '#f59e0b',
      'VALIDATION': '#22c55e',
      'OUTPUT': '#ec4899'
    };
    return colors[type] || '#64748b';
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
