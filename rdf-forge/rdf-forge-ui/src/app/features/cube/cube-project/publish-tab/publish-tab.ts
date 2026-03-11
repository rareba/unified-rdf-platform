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
import { FormsModule } from '@angular/forms';
import { Subject, interval } from 'rxjs';
import { switchMap, takeWhile, takeUntil } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { Cube } from '../../../../core/models/cube.model';
import { Job, JobStatus } from '../../../../core/models/job.model';
import { CubeService } from '../../../../core/services/cube.service';
import { JobService } from '../../../../core/services/job.service';

interface RdfFormat {
  value: string;
  label: string;
  extension: string;
  mimeType: string;
}

const RDF_FORMATS: RdfFormat[] = [
  { value: 'turtle',   label: 'Turtle (.ttl)',     extension: '.ttl',    mimeType: 'text/turtle' },
  { value: 'ntriples', label: 'N-Triples (.nt)',   extension: '.nt',     mimeType: 'application/n-triples' },
  { value: 'jsonld',   label: 'JSON-LD (.jsonld)', extension: '.jsonld', mimeType: 'application/ld+json' },
  { value: 'trig',     label: 'TriG (.trig)',      extension: '.trig',   mimeType: 'application/trig' }
];

const TERMINAL_STATUSES: JobStatus[] = ['completed', 'failed', 'cancelled'];

function isTerminal(status: JobStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

@Component({
  selector: 'app-publish-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatSelectModule,
    MatFormFieldModule,
    MatListModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="publish-tab-container">

      <!-- Action cards row -->
      <div class="action-cards-row">

        <!-- Card 1: Publish to Triplestore -->
        <mat-card class="action-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="card-icon publish-icon">rocket_launch</mat-icon>
            <mat-card-title>Publish to Triplestore</mat-card-title>
            <mat-card-subtitle>Push observations to target endpoint</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (cube().lastPublished) {
              <p class="last-published">
                Last published: {{ cube().lastPublished | date:'medium' }}
              </p>
            } @else {
              <p class="no-publish-info">Not yet published.</p>
            }
          </mat-card-content>
          <mat-card-actions>
            <button
              mat-raised-button
              color="primary"
              [disabled]="publishDisabled()"
              (click)="publish()">
              @if (publishing()) {
                <mat-spinner diameter="18" class="btn-spinner"></mat-spinner>
              } @else {
                <mat-icon>rocket_launch</mat-icon>
              }
              Publish
            </button>
          </mat-card-actions>
        </mat-card>

        <!-- Card 2: Download RDF -->
        <mat-card class="action-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="card-icon download-icon">download</mat-icon>
            <mat-card-title>Download RDF</mat-card-title>
            <mat-card-subtitle>Export cube as RDF file</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-form-field appearance="outline" class="format-field">
              <mat-label>Format</mat-label>
              <mat-select [value]="selectedFormat()" (valueChange)="selectedFormat.set($event)">
                @for (fmt of formats; track fmt.value) {
                  <mat-option [value]="fmt.value">{{ fmt.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </mat-card-content>
          <mat-card-actions>
            <button
              mat-raised-button
              color="accent"
              [disabled]="downloading()"
              (click)="downloadRdf()">
              @if (downloading()) {
                <mat-spinner diameter="18" class="btn-spinner"></mat-spinner>
              } @else {
                <mat-icon>download</mat-icon>
              }
              Download
            </button>
          </mat-card-actions>
        </mat-card>

        <!-- Card 3: Unlist Cube -->
        <mat-card class="action-card warn-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="card-icon unlist-icon">visibility_off</mat-icon>
            <mat-card-title>Unlist Cube</mat-card-title>
            <mat-card-subtitle>Remove from published endpoint</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p class="unlist-description">
              Removes this cube from the triplestore. Observations will remain stored locally.
            </p>
          </mat-card-content>
          <mat-card-actions>
            <button
              mat-stroked-button
              color="warn"
              [disabled]="cube().status !== 'published' || publishing()"
              (click)="unlistCube()">
              <mat-icon>visibility_off</mat-icon>
              Unlist
            </button>
          </mat-card-actions>
        </mat-card>

      </div>

      <!-- Current publish job progress -->
      @if (currentJob(); as job) {
        <div class="current-job-section">
          <div class="section-label">Current Publish Job</div>
          <div class="current-job-status">
            <mat-chip [class]="'status-chip status-chip-' + job.status" [disableRipple]="true">
              {{ getStatusLabel(job.status) }}
            </mat-chip>
            @if (job.errorMessage) {
              <span class="error-msg">{{ job.errorMessage }}</span>
            }
          </div>
        </div>
      }

      <mat-divider></mat-divider>

      <!-- Publication history -->
      <div class="history-section">
        <div class="section-label">Publication History</div>

        @if (publishJobs().length === 0) {
          <div class="empty-history">
            <mat-icon>history</mat-icon>
            <span>No publish runs yet</span>
          </div>
        } @else {
          <mat-list class="job-list">
            @for (job of publishJobs(); track job.id; let i = $index) {
              <mat-list-item class="job-list-item">
                <div class="job-item-content">
                  <span class="job-index">#{{ publishJobs().length - i }}</span>

                  <mat-chip
                    [class]="'status-chip status-chip-' + job.status"
                    [disableRipple]="true">
                    {{ getStatusLabel(job.status) }}
                  </mat-chip>

                  <span class="job-timestamp">
                    {{ job.createdAt | date:'short' }}
                  </span>

                  @if (job.metrics) {
                    <span class="job-metrics">
                      {{ job.metrics.quadsGenerated | number }} quads
                    </span>
                  }

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

    </div>
  `,
  styles: [`
    .publish-tab-container {
      display: flex;
      flex-direction: column;
      gap: 24px;
      padding: 24px;
      max-width: 1100px;
    }

    .action-cards-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 20px;
    }

    @media (max-width: 900px) {
      .action-cards-row {
        grid-template-columns: 1fr;
      }
    }

    .action-card {
      display: flex;
      flex-direction: column;
    }

    .warn-card {
      border: 1px solid var(--mat-warn-color, #f44336);
    }

    .card-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
    }

    .publish-icon  { color: var(--mat-sys-primary, #1976d2); }
    .download-icon { color: var(--mat-sys-tertiary, #00796b); }
    .unlist-icon   { color: var(--mat-sys-error, #c62828); }

    mat-card-content {
      flex: 1;
    }

    mat-card-actions {
      padding: 8px 16px 16px;
    }

    .last-published,
    .no-publish-info,
    .unlist-description {
      font-size: 0.875rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      margin: 8px 0 0;
    }

    .format-field {
      width: 100%;
      margin-top: 8px;
    }

    .btn-spinner {
      display: inline-block;
      vertical-align: middle;
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
    }

    .section-label {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .history-section {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

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

    .job-index {
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

    .job-metrics {
      font-size: 0.8125rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
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
export class PublishTab implements OnInit, OnDestroy {
  private readonly cubeService = inject(CubeService);
  private readonly jobService = inject(JobService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  readonly cube = input.required<Cube>();
  readonly cubeUpdated = output<Cube>();

  readonly publishing = signal(false);
  readonly downloading = signal(false);
  readonly selectedFormat = signal('turtle');

  readonly currentJob = signal<Job | null>(null);
  readonly publishJobs = signal<Job[]>([]);

  readonly formats = RDF_FORMATS;

  publishDisabled(): boolean {
    const c = this.cube();
    return this.publishing() || !c.pipelineId || !c.observationCount;
  }

  ngOnInit(): void {
    const pipelineId = this.cube().pipelineId;
    if (pipelineId) {
      this.loadPublishHistory(pipelineId);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  publish(): void {
    const pipelineId = this.cube().pipelineId;
    if (!pipelineId) {
      return;
    }

    this.publishing.set(true);

    this.jobService
      .create(pipelineId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: job => {
          this.currentJob.set(job);
          this.publishJobs.update(list => [job, ...list]);
          this.pollJob(job.id);
        },
        error: err => {
          console.error('Failed to start publish job', err);
          this.publishing.set(false);
          this.snackBar.open('Failed to start publish job', 'Dismiss', { duration: 5000 });
        }
      });
  }

  downloadRdf(): void {
    const cube = this.cube();
    const format = this.selectedFormat();
    const fmt = RDF_FORMATS.find(f => f.value === format) ?? RDF_FORMATS[0];

    this.downloading.set(true);

    this.cubeService
      .exportCube(cube.id, format)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `${cube.name.replace(/\s+/g, '-').toLowerCase()}${fmt.extension}`;
          anchor.style.display = 'none';
          document.body.appendChild(anchor);
          anchor.click();
          document.body.removeChild(anchor);
          URL.revokeObjectURL(url);
          this.downloading.set(false);
        },
        error: err => {
          console.error('Failed to download RDF', err);
          this.downloading.set(false);
          this.snackBar.open('Failed to download RDF export', 'Dismiss', { duration: 5000 });
        }
      });
  }

  unlistCube(): void {
    const cube = this.cube();
    const confirmed = confirm(
      `Are you sure you want to unlist "${cube.name}"? It will be removed from the published endpoint.`
    );
    if (!confirmed) {
      return;
    }

    this.cubeService
      .unlistCube(cube.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.snackBar.open('Cube unlisted successfully', 'Dismiss', { duration: 4000 });
          this.cubeUpdated.emit(updated);
        },
        error: err => {
          console.error('Failed to unlist cube', err);
          this.snackBar.open('Failed to unlist cube', 'Dismiss', { duration: 5000 });
        }
      });
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

  viewJobLog(jobId: string): void {
    this.router.navigate(['/jobs', jobId]);
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
          this.publishJobs.update(list =>
            list.map(j => (j.id === job.id ? job : j))
          );

          if (isTerminal(job.status)) {
            this.publishing.set(false);

            if (job.status === 'completed') {
              this.snackBar.open('Published successfully', 'Dismiss', { duration: 4000 });
              this.cubeService
                .get(this.cube().id)
                .pipe(takeUntil(this.destroy$))
                .subscribe(refreshed => this.cubeUpdated.emit(refreshed));
            } else if (job.status === 'failed') {
              this.snackBar.open(
                job.errorMessage ?? 'Publish job failed',
                'Dismiss',
                { duration: 6000 }
              );
            }
          }
        },
        error: err => {
          console.error('Error polling publish job status', err);
          this.publishing.set(false);
        }
      });
  }

  private loadPublishHistory(pipelineId: string): void {
    this.jobService
      .list({ pipelineId })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: jobs => {
          const completed = jobs.filter(j => j.status === 'completed' || j.status === 'failed');
          this.publishJobs.set(completed);
        },
        error: err => console.error('Failed to load publish history', err)
      });
  }
}
