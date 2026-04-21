import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ReleaseService } from '../../core/services/release.service';
import { Release } from '../../core/models/release.model';
import { CommentThread } from '../collaboration/comment-thread';

interface DetailData {
  releaseId: string;
}

/**
 * Release detail dialog. Shows the manifest, build status and offers
 * {@code Build} / {@code Download} / {@code Archive} actions. Refreshes the
 * release record inline after any mutation so the caller sees the updated
 * state when the dialog closes.
 */
@Component({
  selector: 'app-release-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatSnackBarModule,
    CommentThread
  ],
  template: `
    <h2 mat-dialog-title>
      @if (release(); as r) {
        {{ r.name }}
        <span class="version">v{{ r.version }}</span>
      } @else {
        Release
      }
    </h2>

    <mat-dialog-content class="content">
      @if (loading()) {
        <div class="centered">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (release(); as r) {
        @if (building()) {
          <mat-progress-bar mode="indeterminate"></mat-progress-bar>
          <p class="progress-msg">Building release bundle…</p>
        }

        <section class="section">
          <h3>Status</h3>
          <mat-chip [class]="'status-' + r.status.toLowerCase()">{{ r.status }}</mat-chip>
          @if (r.publishedAt) {
            <span class="muted">Published {{ r.publishedAt | date:'medium' }}</span>
          }
          @if (r.artifactSizeBytes > 0) {
            <span class="muted">· {{ formatBytes(r.artifactSizeBytes) }}</span>
          }
        </section>

        @if (r.notes) {
          <section class="section">
            <h3>Release Notes</h3>
            <pre class="notes">{{ r.notes }}</pre>
          </section>
        }

        <section class="section">
          <h3>Manifest</h3>
          <pre class="manifest">{{ manifestJson() }}</pre>
        </section>

        @if (buildError(r)) {
          <section class="section error">
            <h3><mat-icon>error_outline</mat-icon> Build error</h3>
            <pre>{{ buildError(r) }}</pre>
          </section>
        }

        <!-- Phase 10: inline collaboration thread on the release asset. -->
        @if (r.projectId) {
          <section class="section">
            <app-comment-thread
              [projectId]="r.projectId"
              assetKind="RELEASE"
              [assetId]="r.id">
            </app-comment-thread>
          </section>
        }
      } @else {
        <p class="hint">Release not found.</p>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      @if (release(); as r) {
        @if (r.status === 'DRAFT' || r.status === 'FAILED') {
          <button mat-raised-button color="primary"
                  [disabled]="building()"
                  (click)="build()">
            <mat-icon>build</mat-icon>
            {{ building() ? 'Building…' : 'Build' }}
          </button>
        }
        @if (r.status === 'PUBLISHED') {
          <button mat-raised-button color="primary" (click)="download()">
            <mat-icon>download</mat-icon>
            Download zip
          </button>
        }
        @if (r.status !== 'ARCHIVED') {
          <button mat-button (click)="archive()">
            <mat-icon>archive</mat-icon>
            Archive
          </button>
        }
      }
      <button mat-button (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .content { min-width: 600px; }
    .centered { display: flex; justify-content: center; padding: 48px; }
    .version { font-size: 0.9rem; color: var(--rdf-text-secondary); margin-left: 8px; }
    .section { margin-top: 16px; }
    .section h3 { margin: 0 0 8px 0; display: flex; align-items: center; gap: 6px; font-size: 0.95rem; }
    .section.error { color: var(--mat-sys-error); }
    .muted { color: var(--rdf-text-secondary); font-size: 0.85rem; margin-left: 8px; }
    .notes, .manifest {
      background: var(--mat-sys-surface-variant);
      padding: 12px; border-radius: 4px;
      font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
      font-size: 0.82rem; white-space: pre-wrap; word-break: break-word;
      max-height: 320px; overflow: auto; margin: 0;
    }
    .progress-msg { margin: 8px 0 0 0; color: var(--rdf-text-secondary); font-size: 0.9rem; }
    .hint { color: var(--rdf-text-secondary); padding: 24px; text-align: center; }
    .status-published { background: var(--mat-sys-primary-container) !important; }
    .status-draft { background: var(--mat-sys-surface-variant) !important; }
    .status-failed { background: var(--mat-sys-error-container) !important; color: var(--mat-sys-on-error-container) !important; }
    .status-building { background: var(--mat-sys-tertiary-container) !important; }
    .status-archived { opacity: 0.6; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReleaseDetail implements OnInit {
  private readonly svc = inject(ReleaseService);
  private readonly ref = inject(MatDialogRef<ReleaseDetail>);
  private readonly data = inject<DetailData>(MAT_DIALOG_DATA);
  private readonly snack = inject(MatSnackBar);

  readonly release = signal<Release | null>(null);
  readonly loading = signal(true);
  readonly building = signal(false);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.svc.get(this.data.releaseId).subscribe({
      next: r => { this.release.set(r); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        this.snack.open('Failed to load release: ' + (err?.message ?? err),
          'Dismiss', { duration: 4000 });
      }
    });
  }

  manifestJson(): string {
    const r = this.release();
    if (!r || !r.manifest) return '{}';
    try {
      return JSON.stringify(r.manifest, null, 2);
    } catch {
      return '{}';
    }
  }

  buildError(r: Release): string | null {
    const manifest = r.manifest as Record<string, unknown> | undefined;
    const err = manifest?.['buildError'];
    return typeof err === 'string' ? err : null;
  }

  build(): void {
    this.building.set(true);
    this.svc.build(this.data.releaseId).subscribe({
      next: () => {
        this.building.set(false);
        this.load();
      },
      error: err => {
        this.building.set(false);
        this.snack.open('Build failed: ' + (err?.error?.detail ?? err?.message ?? err),
          'Dismiss', { duration: 6000 });
        this.load();
      }
    });
  }

  download(): void {
    const r = this.release();
    if (!r) return;
    this.svc.download(r.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = r.name.replace(/[^A-Za-z0-9_.-]/g, '_') + '-' + r.version + '.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: err => this.snack.open('Download failed: ' + (err?.message ?? err),
        'Dismiss', { duration: 4000 })
    });
  }

  archive(): void {
    const r = this.release();
    if (!r) return;
    this.svc.archive(r.id).subscribe({
      next: () => this.load(),
      error: err => this.snack.open('Archive failed: ' + (err?.message ?? err),
        'Dismiss', { duration: 4000 })
    });
  }

  close(): void { this.ref.close(); }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }
}
