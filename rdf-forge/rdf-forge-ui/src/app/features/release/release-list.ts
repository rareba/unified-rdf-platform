import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ReleaseService } from '../../core/services/release.service';
import { Release, ReleaseStatus } from '../../core/models/release.model';
import { ReleaseForm } from './release-form';
import { ReleaseDetail } from './release-detail';

/**
 * Project-scoped list of {@link Release} entities. Rendered inside the
 * Project Workspace "Publish" tab. Provides inline build + download actions
 * and opens a full detail dialog for manifest inspection.
 */
@Component({
  selector: 'app-release-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatMenuModule,
    MatDialogModule,
    MatSnackBarModule
  ],
  template: `
    <div class="release-list">
      <div class="header">
        <div>
          <h2>Releases</h2>
          <p class="subtitle">
            Bundle your project's mappings, shapes, ontologies and data sources
            into an immutable versioned zip, with a validation gate and
            provenance manifest.
          </p>
        </div>
        <button mat-raised-button color="primary"
                [disabled]="!projectId()"
                (click)="openCreate()">
          <mat-icon>add</mat-icon>
          New Release
        </button>
      </div>

      @if (loading()) {
        <div class="centered">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (releases().length === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon class="empty-icon">cloud_upload</mat-icon>
            <h3>No releases yet</h3>
            <p>Create your first release to ship a reproducible bundle.</p>
            <button mat-raised-button color="primary"
                    [disabled]="!projectId()"
                    (click)="openCreate()">
              <mat-icon>add</mat-icon>
              Create Release
            </button>
          </mat-card-content>
        </mat-card>
      } @else {
        <mat-card>
          <mat-card-content>
            <table mat-table [dataSource]="releases()" class="full-width">
              <ng-container matColumnDef="version">
                <th mat-header-cell *matHeaderCellDef>Version</th>
                <td mat-cell *matCellDef="let r">
                  <a href="javascript:void(0)" class="release-link" (click)="openDetail(r)">
                    {{ r.version }}
                  </a>
                </td>
              </ng-container>

              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let r">{{ r.name }}</td>
              </ng-container>

              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let r">
                  <mat-chip [class]="statusClass(r.status)">{{ r.status }}</mat-chip>
                </td>
              </ng-container>

              <ng-container matColumnDef="publishedAt">
                <th mat-header-cell *matHeaderCellDef>Published</th>
                <td mat-cell *matCellDef="let r">
                  {{ r.publishedAt ? (r.publishedAt | date:'short') : '—' }}
                </td>
              </ng-container>

              <ng-container matColumnDef="size">
                <th mat-header-cell *matHeaderCellDef>Size</th>
                <td mat-cell *matCellDef="let r">
                  {{ r.artifactSizeBytes > 0 ? formatBytes(r.artifactSizeBytes) : '—' }}
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let r">
                  @if (r.status === 'DRAFT' || r.status === 'FAILED') {
                    <button mat-icon-button
                            (click)="build(r); $event.stopPropagation()"
                            aria-label="build release"
                            title="Build bundle">
                      <mat-icon>build</mat-icon>
                    </button>
                  }
                  @if (r.status === 'PUBLISHED') {
                    <button mat-icon-button
                            (click)="download(r); $event.stopPropagation()"
                            aria-label="download"
                            title="Download zip">
                      <mat-icon>download</mat-icon>
                    </button>
                  }
                  <button mat-icon-button [matMenuTriggerFor]="menu"
                          (click)="$event.stopPropagation()"
                          aria-label="release actions">
                    <mat-icon>more_vert</mat-icon>
                  </button>
                  <mat-menu #menu>
                    <button mat-menu-item (click)="openDetail(r)">
                      <mat-icon>info</mat-icon> Details
                    </button>
                    @if (r.status !== 'ARCHIVED') {
                      <button mat-menu-item (click)="archive(r)">
                        <mat-icon>archive</mat-icon> Archive
                      </button>
                    }
                    <button mat-menu-item (click)="remove(r)">
                      <mat-icon>delete</mat-icon> Delete
                    </button>
                  </mat-menu>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"
                  class="clickable-row"
                  (click)="openDetail(row)"></tr>
            </table>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .release-list { padding: 16px; }
    .header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 16px; gap: 16px;
    }
    .header h2 { margin: 0 0 4px 0; }
    .subtitle { color: var(--rdf-text-secondary); margin: 0; font-size: 0.9rem; max-width: 640px; }
    .centered { display: flex; justify-content: center; padding: 48px; }
    .empty {
      text-align: center; padding: 48px 16px;
    }
    .empty mat-card-content { display: flex; flex-direction: column; align-items: center; gap: 12px; }
    .empty .empty-icon { font-size: 64px; width: 64px; height: 64px; color: var(--rdf-text-secondary); }
    .empty h3 { margin: 0; }
    .empty p { margin: 0; color: var(--rdf-text-secondary); }
    .full-width { width: 100%; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: rgba(0,0,0,0.03); }
    .release-link { font-weight: 500; text-decoration: none; color: var(--mat-sys-primary); cursor: pointer; }
    .status-published { background: var(--mat-sys-primary-container) !important; }
    .status-draft { background: var(--mat-sys-surface-variant) !important; }
    .status-failed { background: var(--mat-sys-error-container) !important; color: var(--mat-sys-on-error-container) !important; }
    .status-building { background: var(--mat-sys-tertiary-container) !important; }
    .status-archived { opacity: 0.6; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReleaseList implements OnInit {
  private readonly svc = inject(ReleaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly projectId = input.required<string>();
  readonly releases = signal<Release[]>([]);
  readonly loading = signal(false);
  readonly columns = ['version', 'name', 'status', 'publishedAt', 'size', 'actions'];

  constructor() {
    effect(() => {
      const pid = this.projectId();
      if (pid) this.reload(pid);
    });
  }

  ngOnInit(): void { /* effect handles initial load */ }

  reload(projectId: string): void {
    this.loading.set(true);
    this.svc.listByProject(projectId).subscribe({
      next: list => { this.releases.set(list); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        this.snack.open('Failed to load releases: ' + (err?.message ?? err),
          'Dismiss', { duration: 4000 });
      }
    });
  }

  openCreate(): void {
    const pid = this.projectId();
    if (!pid) return;
    const ref = this.dialog.open(ReleaseForm, {
      width: '560px',
      data: { projectId: pid }
    });
    ref.afterClosed().subscribe((created: Release | undefined) => {
      if (created) this.reload(pid);
    });
  }

  openDetail(r: Release): void {
    const ref = this.dialog.open(ReleaseDetail, {
      width: '720px',
      maxHeight: '80vh',
      data: { releaseId: r.id }
    });
    ref.afterClosed().subscribe(() => {
      const pid = this.projectId();
      if (pid) this.reload(pid);
    });
  }

  build(r: Release): void {
    this.snack.open('Building release ' + r.version + '…', undefined, { duration: 2000 });
    this.svc.build(r.id).subscribe({
      next: resp => {
        if (resp.artifactUri) {
          this.snack.open('Release ' + r.version + ' built (' + this.formatBytes(resp.artifactSizeBytes) + ')',
            'Dismiss', { duration: 4000 });
        } else {
          this.snack.open('Release build finished but returned no artifact — check manifest for errors',
            'Dismiss', { duration: 6000 });
        }
        const pid = this.projectId();
        if (pid) this.reload(pid);
      },
      error: err => this.snack.open('Build failed: ' + (err?.error?.detail ?? err?.message ?? err),
        'Dismiss', { duration: 6000 })
    });
  }

  download(r: Release): void {
    this.svc.download(r.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (r.name.replace(/[^A-Za-z0-9_.-]/g, '_')) + '-' + r.version + '.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: err => this.snack.open('Download failed: ' + (err?.message ?? err),
        'Dismiss', { duration: 4000 })
    });
  }

  archive(r: Release): void {
    this.svc.archive(r.id).subscribe({
      next: () => {
        const pid = this.projectId();
        if (pid) this.reload(pid);
      },
      error: err => this.snack.open('Archive failed: ' + (err?.message ?? err),
        'Dismiss', { duration: 4000 })
    });
  }

  remove(r: Release): void {
    if (!confirm(`Delete release ${r.version}? This cannot be undone.`)) return;
    this.svc.delete(r.id).subscribe({
      next: () => {
        const pid = this.projectId();
        if (pid) this.reload(pid);
      },
      error: err => this.snack.open('Delete failed: ' + (err?.message ?? err),
        'Dismiss', { duration: 4000 })
    });
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  statusClass(status: ReleaseStatus): string {
    return 'status-' + status.toLowerCase();
  }
}
