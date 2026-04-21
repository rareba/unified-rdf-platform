import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DocsService } from '../../core/services/docs.service';

/**
 * Renders the generated Semantic API documentation for a project.
 *
 * <p>The backend returns a self-contained, HTML-escaped document
 * (see {@code DocGenService.renderHtml}). The viewer wraps it with a
 * {@link DomSanitizer#bypassSecurityTrustHtml} call — this is safe because
 * every user-supplied string passes through {@code HtmlUtils.htmlEscape}
 * on the server before it reaches the browser.
 */
@Component({
  selector: 'app-docs-viewer',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule
  ],
  template: `
    <mat-card class="docs-viewer">
      <mat-card-header>
        <mat-card-title>
          <mat-icon>description</mat-icon>
          Semantic API Documentation
        </mat-card-title>
        <mat-card-subtitle>
          Auto-generated from ontologies, mappings, and published endpoints.
        </mat-card-subtitle>
        <span class="spacer"></span>
        <button mat-stroked-button (click)="regenerate()" [disabled]="loading()">
          <mat-icon>refresh</mat-icon>
          Regenerate
        </button>
      </mat-card-header>

      <mat-card-content>
        @if (loading()) {
          <div class="center">
            <mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner>
          </div>
        } @else if (error()) {
          <div class="error">
            <mat-icon color="warn">error</mat-icon>
            <span>{{ error() }}</span>
          </div>
        } @else if (safeHtml(); as html) {
          <div class="rendered" [innerHTML]="html"></div>
        } @else {
          <p class="muted">No documentation yet — click Regenerate to build it.</p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .spacer { flex: 1; }
    .docs-viewer { margin: 1rem; }
    .center { display: flex; justify-content: center; padding: 2rem; }
    .error { display: flex; gap: .5rem; align-items: center; padding: 1rem; }
    .rendered { padding: 1rem; background: #fafafa; border-radius: 4px; overflow: auto; max-height: 70vh; }
    .rendered :global(h1) { margin-top: 0; }
    .muted { color: #666; padding: 1rem; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DocsViewer implements OnChanges {
  @Input({ required: true }) projectId!: string;

  private readonly docsService = inject(DocsService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly safeHtml = signal<SafeHtml | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectId'] && this.projectId) {
      this.load();
    }
  }

  load(): void {
    if (!this.projectId) return;
    this.loading.set(true);
    this.error.set(null);
    this.docsService.getHtml(this.projectId).subscribe({
      next: html => {
        // NOTE: html is already OWASP-escaped on the server via HtmlUtils.htmlEscape.
        this.safeHtml.set(this.sanitizer.bypassSecurityTrustHtml(html));
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.message ?? 'Failed to generate docs');
        this.loading.set(false);
      }
    });
  }

  regenerate(): void { this.load(); }
}
