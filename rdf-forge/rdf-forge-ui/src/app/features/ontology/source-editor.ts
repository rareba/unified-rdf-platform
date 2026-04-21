import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { OntologyService } from '../../core/services/ontology.service';
import { RDF_FORMAT_OPTIONS, RdfFormat } from '../../core/models';

/**
 * Read-only viewer for the serialized ontology content. A dropdown allows
 * requesting a different serialization format (handled server-side by Jena).
 */
@Component({
  selector: 'app-ontology-source-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="toolbar">
      <mat-form-field appearance="outline" class="format-picker">
        <mat-label>Format</mat-label>
        <mat-select [(value)]="selectedFormat" (selectionChange)="reload()">
          @for (opt of formats; track opt.value) {
            <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <span class="spacer"></span>

      <button mat-stroked-button (click)="copy()" [disabled]="!source()">
        <mat-icon>content_copy</mat-icon>
        Copy
      </button>
      <button mat-stroked-button (click)="download()" [disabled]="!source()">
        <mat-icon>download</mat-icon>
        Download
      </button>
    </div>

    @if (loading()) {
      <div class="center">
        <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
      </div>
    } @else if (error()) {
      <pre class="source error">{{ error() }}</pre>
    } @else {
      <pre class="source">{{ source() }}</pre>
    }
  `,
  styles: [`
    .toolbar {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }
    .spacer { flex: 1 1 auto; }
    .format-picker { width: 200px; }
    .source {
      font-family: 'JetBrains Mono', 'Consolas', monospace;
      font-size: 0.85em;
      background: var(--rdf-surface-variant, rgba(0,0,0,0.04));
      padding: 12px;
      border-radius: 4px;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 60vh;
      overflow: auto;
    }
    .source.error { color: #b91c1c; }
    .center {
      display: flex;
      justify-content: center;
      padding: 24px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SourceEditor implements OnChanges {
  private readonly service = inject(OntologyService);
  private readonly snack = inject(MatSnackBar);

  @Input({ required: true }) ontologyId!: string;
  @Input() defaultFormat: RdfFormat = 'TURTLE';
  @Input() fileName = 'ontology';

  readonly formats = RDF_FORMAT_OPTIONS;
  readonly source = signal<string>('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  selectedFormat: RdfFormat = 'TURTLE';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['ontologyId']?.currentValue || changes['defaultFormat']) {
      this.selectedFormat = this.defaultFormat;
      this.reload();
    }
  }

  reload(): void {
    if (!this.ontologyId) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.exportContent(this.ontologyId, this.selectedFormat).subscribe({
      next: res => {
        this.source.set(res.content ?? '');
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.detail ?? err?.message ?? 'Failed to load source');
        this.loading.set(false);
      }
    });
  }

  copy(): void {
    const text = this.source();
    if (!text || !navigator.clipboard) return;
    navigator.clipboard.writeText(text).then(
      () => this.snack.open('Copied to clipboard', 'Close', { duration: 1500 })
    );
  }

  download(): void {
    const text = this.source();
    if (!text) return;
    const ext = this.formats.find(f => f.value === this.selectedFormat)?.ext ?? 'txt';
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.fileName}.${ext}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
