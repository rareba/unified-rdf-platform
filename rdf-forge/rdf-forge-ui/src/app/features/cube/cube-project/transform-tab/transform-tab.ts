import {
  Component,
  input,
  output,
  signal,
  inject,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, interval } from 'rxjs';
import { switchMap, takeWhile, takeUntil } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { Cube } from '../../../../core/models/cube.model';
import { Job, JobStatus } from '../../../../core/models/job.model';
import { CubeService } from '../../../../core/services/cube.service';
import { JobService } from '../../../../core/services/job.service';
import { MiniPipelinePreview } from './mini-pipeline-preview';

const TERMINAL_STATUSES: JobStatus[] = ['completed', 'failed', 'cancelled'];

function isTerminal(status: JobStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

@Component({
  selector: 'app-transform-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatCardModule,
    MatListModule,
    MatChipsModule,
    MatSnackBarModule,
    MiniPipelinePreview
  ],
  template: `
    <div class="transform-layout">

      <!-- Main content -->
      <div class="transform-main">

        <!-- Actions row -->
        <div class="actions-row">
          <button
            mat-raised-button
            color="primary"
            [disabled]="running()"
            (click)="runTransform()">
            <mat-icon>play_arrow</mat-icon>
            Run Transform
          </button>
        </div>

        <!-- Drift warning -->
        @if (hasDrift()) {
          <mat-card class="drift-warning">
            <mat-card-content class="drift-content">
              <mat-icon class="warning-icon">warning</mat-icon>
              <span>
                Column mappings have changed since last pipeline generation.
                The pipeline will be regenerated on next run.
              </span>
            </mat-card-content>
          </mat-card>
        }

        <!-- Currently running job -->
        @if (currentJob(); as job) {
          <div class="current-job-section">
            <div class="section-label">Current Run</div>
            <mat-progress-bar
              [mode]="job.status === 'running' ? 'indeterminate' : 'determinate'"
              [value]="job.progress ?? 0">
            </mat-progress-bar>
            <div class="current-job-status">
              <span [class]="'status-text status-' + job.status">
                {{ getStatusLabel(job.status) }}
              </span>
              @if (job.errorMessage) {
                <span class="error-msg">{{ job.errorMessage }}</span>
              }
            </div>
          </div>
        }

        <!-- Job history -->
        <div class="section-label">Job History</div>

        @if (jobs().length === 0) {
          <div class="empty-history">
            <mat-icon>history</mat-icon>
            <span>No runs yet</span>
          </div>
        } @else {
          <mat-list class="job-list">
            @for (job of jobs(); track job.id; let i = $index) {
              <mat-list-item class="job-list-item">
                <div class="job-item-content">
                  <span class="job-version">#{{ jobs().length - i }}</span>

                  <mat-chip
                    [class]="'status-chip status-chip-' + job.status"
                    [disableRipple]="true">
                    {{ getStatusLabel(job.status) }}
                  </mat-chip>

                  <span class="job-timestamp">
                    {{ job.createdAt | date:'short' }}
                  </span>

                  <a
                    class="view-log-link"
                    (click)="viewJobLog(job.id)"
                    (keydown.enter)="viewJobLog(job.id)"
                    role="link"
                    tabindex="0">
                    View log
                  </a>
                </div>
              </mat-list-item>
            }
          </mat-list>
        }
      </div>

      <!-- Sidebar: mini pipeline preview -->
      <app-mini-pipeline-preview [cube]="cube()"></app-mini-pipeline-preview>

    </div>
  `,
  styles: [`
    .transform-layout {
      display: flex;
      flex-direction: row;
      height: 100%;
      overflow: hidden;
    }

    .transform-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px;
      overflow-y: auto;
    }

    .actions-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .drift-warning {
      background: #fff8e1;
      border: 1px solid #f59e0b;
    }

    .drift-content {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px !important;
    }

    .warning-icon {
      color: #f59e0b;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .current-job-section {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .current-job-status {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 0.875rem;
    }

    .section-label {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      margin-top: 4px;
    }

    .status-text {
      font-weight: 500;
      text-transform: capitalize;
    }

    .status-text.status-running   { color: #1976d2; }
    .status-text.status-completed { color: #2e7d32; }
    .status-text.status-failed    { color: #c62828; }
    .status-text.status-cancelled { color: #f59e0b; }
    .status-text.status-pending   { color: #757575; }

    .error-msg {
      color: #c62828;
      font-size: 0.8125rem;
    }

    .empty-history {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 32px 16px;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      font-size: 0.875rem;
    }

    .empty-history mat-icon {
      font-size: 36px;
      width: 36px;
      height: 36px;
    }

    .job-list {
      padding: 0;
    }

    .job-list-item {
      border-bottom: 1px solid var(--mat-divider-color, rgba(0,0,0,.08));
    }

    .job-item-content {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      padding: 8px 0;
    }

    .job-version {
      font-size: 0.8125rem;
      font-weight: 500;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      min-width: 28px;
    }

    .status-chip {
      font-size: 0.75rem;
      height: 22px;
      min-height: 22px;
      padding: 0 8px;
    }

    .status-chip-completed { background: #e8f5e9 !important; color: #2e7d32 !important; }
    .status-chip-running   { background: #e3f2fd !important; color: #1565c0 !important; }
    .status-chip-failed    { background: #ffebee !important; color: #c62828 !important; }
    .status-chip-cancelled { background: #fff8e1 !important; color: #f59e0b !important; }
    .status-chip-pending   { background: #f5f5f5 !important; color: #616161 !important; }

    .job-timestamp {
      font-size: 0.8125rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      flex: 1;
    }

    .view-log-link {
      font-size: 0.8125rem;
      color: var(--mat-sys-primary, #1976d2);
      cursor: pointer;
      text-decoration: none;
    }

    .view-log-link:hover {
      text-decoration: underline;
    }
  `]
})
export class TransformTab implements OnInit, OnDestroy {
  private readonly cubeService = inject(CubeService);
  private readonly jobService = inject(JobService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  readonly cube = input.required<Cube>();
  readonly cubeUpdated = output<Cube>();

  readonly jobs = signal<Job[]>([]);
  readonly currentJob = signal<Job | null>(null);
  readonly running = signal(false);

  ngOnInit(): void {
    const pipelineId = this.cube().pipelineId;
    if (pipelineId) {
      this.loadJobHistory(pipelineId);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  hasDrift(): boolean {
    const c = this.cube();
    return (
      c.mappingsVersion !== undefined &&
      c.metadata?.lastGeneratedMappingsVersion !== undefined &&
      c.mappingsVersion !== c.metadata.lastGeneratedMappingsVersion
    );
  }

  getStatusLabel(status: JobStatus): string {
    switch (status) {
      case 'completed': return 'Completed';
      case 'running':   return 'Running';
      case 'failed':    return 'Failed';
      case 'cancelled': return 'Canceled';
      case 'pending':   return 'Pending';
      default:          return status;
    }
  }

  runTransform(): void {
    this.running.set(true);

    const cube = this.cube();
    const needsGeneration = !cube.pipelineId || this.hasDrift();

    const generateIfNeeded$ = needsGeneration
      ? this.cubeService.generatePipeline(cube.id)
      : null;

    if (generateIfNeeded$) {
      generateIfNeeded$
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: artifact => {
            // Reload cube so pipelineId is fresh, then start job
            this.cubeService.get(cube.id)
              .pipe(takeUntil(this.destroy$))
              .subscribe({
                next: refreshed => {
                  this.cubeUpdated.emit(refreshed);
                  this.startJob(artifact.id);
                },
                error: err => {
                  console.error('Failed to refresh cube after pipeline generation', err);
                  this.startJob(artifact.id);
                }
              });
          },
          error: err => {
            console.error('Failed to generate pipeline', err);
            this.running.set(false);
            this.snackBar.open('Failed to generate pipeline', 'Dismiss', { duration: 5000 });
          }
        });
    } else {
      this.startJob(cube.pipelineId!);
    }
  }

  private startJob(pipelineId: string): void {
    this.jobService.create(pipelineId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: job => {
          this.currentJob.set(job);
          this.jobs.update(list => [job, ...list]);
          this.pollJob(job.id);
        },
        error: err => {
          console.error('Failed to start job', err);
          this.running.set(false);
          this.snackBar.open('Failed to start transform job', 'Dismiss', { duration: 5000 });
        }
      });
  }

  private pollJob(jobId: string): void {
    interval(3000)
      .pipe(
        switchMap(() => this.jobService.get(jobId)),
        takeWhile(job => !isTerminal(job.status), true),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: job => {
          this.currentJob.set(job);
          this.jobs.update(list =>
            list.map(j => (j.id === job.id ? job : j))
          );

          if (isTerminal(job.status)) {
            this.running.set(false);

            if (job.status === 'completed') {
              this.snackBar.open('Transform completed successfully', 'Dismiss', { duration: 4000 });
              this.cubeService.get(this.cube().id)
                .pipe(takeUntil(this.destroy$))
                .subscribe(refreshed => this.cubeUpdated.emit(refreshed));
            } else if (job.status === 'failed') {
              this.snackBar.open(
                job.errorMessage ?? 'Transform failed',
                'Dismiss',
                { duration: 6000 }
              );
            }
          }
        },
        error: err => {
          console.error('Error polling job status', err);
          this.running.set(false);
        }
      });
  }

  private loadJobHistory(pipelineId: string): void {
    this.jobService.list({ pipelineId })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: jobs => this.jobs.set(jobs),
        error: err => console.error('Failed to load job history', err)
      });
  }

  viewJobLog(jobId: string): void {
    this.router.navigate(['/jobs', jobId]);
  }
}
