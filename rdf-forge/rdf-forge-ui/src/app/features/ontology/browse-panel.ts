import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { OntologyService } from '../../core/services/ontology.service';
import { TermDetail, TermKind, TermResult } from '../../core/models';

/**
 * Two-pane term browser: left pane is a type-filtered, search-filtered list of
 * terms, right pane is the detail for the currently-selected term.
 */
@Component({
  selector: 'app-ontology-browse-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatListModule,
    MatDividerModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatButtonModule
  ],
  template: `
    <div class="browser">
      <div class="left">
        <mat-button-toggle-group [value]="kind()" (change)="setKind($event.value)">
          <mat-button-toggle value="classes">
            <mat-icon>class</mat-icon> Classes
          </mat-button-toggle>
          <mat-button-toggle value="properties">
            <mat-icon>tune</mat-icon> Properties
          </mat-button-toggle>
          <mat-button-toggle value="skos-concepts">
            <mat-icon>bookmarks</mat-icon> SKOS
          </mat-button-toggle>
        </mat-button-toggle-group>

        <mat-form-field appearance="outline" class="search">
          <mat-label>Search</mat-label>
          <input matInput [value]="searchTerm()"
                 (input)="onSearchInput($event)"
                 placeholder="label or URI...">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <div class="list-wrap">
          @if (loading()) {
            <div class="center">
              <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
            </div>
          } @else if (terms().length === 0) {
            <div class="empty">
              <mat-icon>inventory_2</mat-icon>
              <span>No terms found.</span>
            </div>
          } @else {
            <mat-list>
              @for (t of terms(); track t.uri) {
                <mat-list-item
                    [class.active]="isActive(t)"
                    (click)="select(t)"
                    matTooltip="{{ t.uri }}">
                  <mat-icon matListItemIcon>{{ iconFor(t) }}</mat-icon>
                  <div matListItemTitle>{{ t.label || shortUri(t.uri) }}</div>
                  @if (t.comment) {
                    <div matListItemLine class="desc">{{ t.comment }}</div>
                  }
                </mat-list-item>
              }
            </mat-list>
          }
        </div>
      </div>

      <mat-divider vertical></mat-divider>

      <div class="right">
        @if (detail(); as d) {
          <div class="term-detail">
            <h3>
              <mat-icon>label</mat-icon>
              {{ d.label || shortUri(d.uri) }}
            </h3>
            <div class="uri">
              <code>{{ d.uri }}</code>
              <button mat-icon-button (click)="copy(d.uri)" matTooltip="Copy URI">
                <mat-icon>content_copy</mat-icon>
              </button>
            </div>

            @if (d.types && d.types.length > 0) {
              <div class="section">
                <h4>Types</h4>
                @for (t of d.types; track t) {
                  <mat-chip>{{ shortUri(t) }}</mat-chip>
                }
              </div>
            }

            @if (d.comment) {
              <div class="section">
                <h4>Comment</h4>
                <p>{{ d.comment }}</p>
              </div>
            }

            @if (d.altLabels && d.altLabels.length > 0) {
              <div class="section">
                <h4>Alt labels</h4>
                @for (l of d.altLabels; track l) {
                  <mat-chip>{{ l }}</mat-chip>
                }
              </div>
            }

            @if (d.domain && d.domain.length > 0) {
              <div class="section">
                <h4>Domain</h4>
                @for (u of d.domain; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }

            @if (d.range && d.range.length > 0) {
              <div class="section">
                <h4>Range</h4>
                @for (u of d.range; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }

            @if (d.broader && d.broader.length > 0) {
              <div class="section">
                <h4>Broader</h4>
                @for (u of d.broader; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }

            @if (d.narrower && d.narrower.length > 0) {
              <div class="section">
                <h4>Narrower</h4>
                @for (u of d.narrower; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }

            @if (d.exactMatch && d.exactMatch.length > 0) {
              <div class="section">
                <h4>skos:exactMatch</h4>
                @for (u of d.exactMatch; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }

            @if (d.closeMatch && d.closeMatch.length > 0) {
              <div class="section">
                <h4>skos:closeMatch</h4>
                @for (u of d.closeMatch; track u) {
                  <mat-chip matTooltip="{{ u }}">{{ shortUri(u) }}</mat-chip>
                }
              </div>
            }
          </div>
        } @else {
          <div class="placeholder">
            <mat-icon>help_outline</mat-icon>
            <p>Select a term on the left to view its details.</p>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .browser {
      display: flex;
      flex-direction: row;
      height: 100%;
      min-height: 480px;
    }
    .left {
      flex: 0 0 360px;
      display: flex;
      flex-direction: column;
      padding: 12px;
      gap: 8px;
      min-height: 0;
    }
    .right {
      flex: 1 1 auto;
      padding: 16px;
      overflow: auto;
    }
    .search { width: 100%; }
    .list-wrap {
      flex: 1 1 auto;
      overflow: auto;
      min-height: 0;
    }
    .center, .empty, .placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px;
      color: var(--rdf-text-secondary);
      gap: 8px;
    }
    .active {
      background: var(--rdf-surface-variant, rgba(59, 130, 246, 0.1));
    }
    .desc {
      font-size: 0.8em;
      color: var(--rdf-text-secondary);
    }
    .term-detail h3 {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0 0 8px;
    }
    .uri {
      display: flex;
      gap: 6px;
      align-items: center;
      margin-bottom: 12px;
      font-size: 0.85em;
      word-break: break-all;
    }
    .section {
      margin-bottom: 16px;
    }
    .section h4 {
      margin: 0 0 6px;
      font-size: 0.85em;
      text-transform: uppercase;
      color: var(--rdf-text-secondary);
    }
    mat-chip {
      margin: 0 4px 4px 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrowsePanel implements OnInit {
  private readonly service = inject(OntologyService);

  @Input({ required: true }) ontologyId!: string;
  @Output() readonly termSelected = new EventEmitter<TermDetail>();

  readonly kind = signal<TermKind>('classes');
  readonly searchTerm = signal<string>('');
  readonly terms = signal<TermResult[]>([]);
  readonly loading = signal(false);
  readonly detail = signal<TermDetail | null>(null);
  readonly activeUri = signal<string | null>(null);

  private debounceHandle: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.reload();
  }

  setKind(k: TermKind): void {
    if (k === this.kind()) return;
    this.kind.set(k);
    this.searchTerm.set('');
    this.detail.set(null);
    this.activeUri.set(null);
    this.reload();
  }

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value ?? '';
    this.searchTerm.set(value);
    if (this.debounceHandle !== null) clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.reload(), 250);
  }

  isActive(t: TermResult): boolean {
    return this.activeUri() === t.uri;
  }

  select(t: TermResult): void {
    this.activeUri.set(t.uri);
    this.service.termDetail(this.ontologyId, t.uri).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.termSelected.emit(detail);
      },
      error: () => {
        this.detail.set({ uri: t.uri, type: t.type, label: t.label });
      }
    });
  }

  copy(text: string): void {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).catch(() => {});
    }
  }

  iconFor(t: TermResult): string {
    switch (t.type) {
      case 'CLASS': return 'class';
      case 'PROPERTY': return 'tune';
      case 'SKOS_CONCEPT': return 'bookmark';
      default: return 'label';
    }
  }

  shortUri(uri: string | undefined): string {
    if (!uri) return '';
    const hash = uri.lastIndexOf('#');
    if (hash >= 0 && hash < uri.length - 1) return uri.substring(hash + 1);
    const slash = uri.lastIndexOf('/');
    if (slash >= 0 && slash < uri.length - 1) return uri.substring(slash + 1);
    return uri;
  }

  private reload(): void {
    if (!this.ontologyId) return;
    this.loading.set(true);
    const q = this.searchTerm() || undefined;
    const stream = this.loaderFor(this.kind(), q);
    stream.subscribe({
      next: list => {
        this.terms.set(list ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.terms.set([]);
        this.loading.set(false);
      }
    });
  }

  private loaderFor(kind: TermKind, q: string | undefined) {
    switch (kind) {
      case 'classes': return this.service.classes(this.ontologyId, q);
      case 'properties': return this.service.properties(this.ontologyId, q);
      case 'skos-concepts': return this.service.skosConcepts(this.ontologyId, q);
    }
  }
}
