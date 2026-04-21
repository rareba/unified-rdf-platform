import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OntologyService } from '../../core/services/ontology.service';
import { Ontology } from '../../core/models';
import { BrowsePanel } from './browse-panel';
import { NamespaceManager } from './namespace-manager';
import { SourceEditor } from './source-editor';

/**
 * Ontology workspace page with tabs: Overview | Browse | Namespaces | Source.
 */
@Component({
  selector: 'app-ontology-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    BrowsePanel,
    NamespaceManager,
    SourceEditor
  ],
  template: `
    @if (loading()) {
      <div class="center">
        <mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner>
      </div>
    } @else if (error()) {
      <div class="error">
        <mat-icon>error</mat-icon>
        <span>{{ error() }}</span>
      </div>
    } @else if (ontology(); as o) {
      <div class="page">
        <div class="header">
          <button mat-icon-button [routerLink]="backLink()" matTooltip="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <div class="title">
            <h1>{{ o.name }}</h1>
            @if (o.description) {
              <p class="desc">{{ o.description }}</p>
            }
            <div class="meta">
              <mat-chip>{{ o.format }}</mat-chip>
              <mat-chip>v{{ o.version }}</mat-chip>
              @if (o.prefix) {
                <mat-chip>prefix: {{ o.prefix }}</mat-chip>
              }
              <code class="ns">{{ o.namespace }}</code>
            </div>
          </div>
        </div>

        <mat-tab-group class="tabs">
          <mat-tab label="Overview">
            <div class="tab-body">
              <mat-card>
                <mat-card-content>
                  <div class="stats">
                    <div class="stat">
                      <mat-icon>data_object</mat-icon>
                      <div>
                        <div class="value">{{ stat('tripleCount') }}</div>
                        <div class="label">Triples</div>
                      </div>
                    </div>
                    <div class="stat">
                      <mat-icon>class</mat-icon>
                      <div>
                        <div class="value">{{ stat('classCount') }}</div>
                        <div class="label">Classes</div>
                      </div>
                    </div>
                    <div class="stat">
                      <mat-icon>tune</mat-icon>
                      <div>
                        <div class="value">{{ stat('propertyCount') }}</div>
                        <div class="label">Properties</div>
                      </div>
                    </div>
                    <div class="stat">
                      <mat-icon>bookmarks</mat-icon>
                      <div>
                        <div class="value">{{ stat('skosConceptCount') }}</div>
                        <div class="label">SKOS Concepts</div>
                      </div>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </mat-tab>

          <mat-tab label="Browse">
            <div class="tab-body">
              <app-ontology-browse-panel [ontologyId]="o.id"></app-ontology-browse-panel>
            </div>
          </mat-tab>

          <mat-tab label="Namespaces">
            <div class="tab-body">
              <app-namespace-manager [ontologyId]="o.id"></app-namespace-manager>
            </div>
          </mat-tab>

          <mat-tab label="Source">
            <div class="tab-body">
              <app-ontology-source-editor
                [ontologyId]="o.id"
                [defaultFormat]="o.format"
                [fileName]="o.name"></app-ontology-source-editor>
            </div>
          </mat-tab>
        </mat-tab-group>
      </div>
    }
  `,
  styles: [`
    .page { padding: 16px; }
    .center, .error {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 32px;
      gap: 8px;
    }
    .error { color: #b91c1c; }
    .header {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 16px;
    }
    .title h1 {
      margin: 0;
    }
    .desc {
      margin: 4px 0;
      color: var(--rdf-text-secondary);
    }
    .meta {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-top: 8px;
    }
    .ns {
      font-size: 0.85em;
      color: var(--rdf-text-secondary);
      word-break: break-all;
    }
    .tabs { margin-top: 8px; }
    .tab-body { padding: 16px 0; }
    .stats {
      display: flex;
      gap: 24px;
      flex-wrap: wrap;
    }
    .stat {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 16px;
    }
    .stat mat-icon {
      color: var(--rdf-primary, #3b82f6);
      font-size: 32px;
      width: 32px;
      height: 32px;
    }
    .stat .value {
      font-size: 1.5em;
      font-weight: 600;
    }
    .stat .label {
      font-size: 0.8em;
      color: var(--rdf-text-secondary);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OntologyDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(OntologyService);

  readonly ontology = signal<Ontology | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.loading.set(true);
    this.service.get(id).subscribe({
      next: o => {
        this.ontology.set(o);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.detail ?? err?.message ?? 'Ontology not found');
        this.loading.set(false);
      }
    });
  }

  backLink(): string[] {
    const o = this.ontology();
    if (o?.projectId) return ['/projects', o.projectId, 'ontology'];
    return ['/'];
  }

  stat(key: string): string {
    const v = this.ontology()?.metadata?.[key];
    return typeof v === 'number' ? String(v) : '-';
  }
}
