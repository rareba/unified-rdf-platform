import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { LineageService } from '../../core/services/lineage.service';
import {
  LineageEdge,
  LineageGraph,
  LineageNode,
  LineageNodeKind
} from '../../core/models/lineage.model';

/**
 * Positioned node used for rendering — the raw {@link LineageNode} plus (x,y)
 * computed by the layout.
 */
interface PositionedNode extends LineageNode {
  x: number;
  y: number;
}

/**
 * Project-scoped lineage/provenance graph viewer. Layout is a simple
 * kind-banded strategy: nodes of the same kind sit on a horizontal band,
 * evenly spaced. It's deterministic and non-animated which keeps the SVG
 * rendering fast even without a force-directed library. When the project
 * grows past a few dozen nodes we swap this for ngx-graph.
 */
@Component({
  selector: 'app-lineage-graph',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatChipsModule
  ],
  template: `
    <div class="lineage">
      <div class="header">
        <div>
          <h2>Lineage</h2>
          <p class="subtitle">
            Traces how data sources, mappings, ontologies and releases
            connect in this project. Click a node to focus its neighbourhood.
          </p>
        </div>
        <button mat-stroked-button (click)="reloadProject()" [disabled]="loading()">
          <mat-icon>refresh</mat-icon>
          Refresh
        </button>
      </div>

      @if (focusedId()) {
        <mat-card class="focus-banner">
          <mat-card-content>
            <mat-icon>filter_center_focus</mat-icon>
            <span>Focused on <strong>{{ focusedLabel() }}</strong></span>
            <button mat-button (click)="clearFocus()">
              <mat-icon>close</mat-icon>
              Clear focus
            </button>
          </mat-card-content>
        </mat-card>
      }

      @if (loading()) {
        <div class="centered">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (!graph() || graph()!.nodes.length === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon class="empty-icon">account_tree</mat-icon>
            <h3>No lineage yet</h3>
            <p>Add mappings, pipelines or releases to populate the graph.</p>
          </mat-card-content>
        </mat-card>
      } @else {
        <mat-card class="graph-card">
          <mat-card-content>
            <div class="legend">
              @for (k of legendKinds; track k) {
                <mat-chip [class]="'kind-' + k.toLowerCase()">{{ k }}</mat-chip>
              }
            </div>
            <svg class="graph"
                 [attr.viewBox]="'0 0 ' + canvasW() + ' ' + canvasH()"
                 preserveAspectRatio="xMidYMid meet">
              <!-- Edges -->
              @for (e of visibleEdges(); track e.from + '→' + e.to + ':' + e.kind) {
                @if (edgeCoords(e); as coords) {
                  <g class="edge">
                    <line [attr.x1]="coords.x1" [attr.y1]="coords.y1"
                          [attr.x2]="coords.x2" [attr.y2]="coords.y2"
                          [class]="'edge-' + e.kind.toLowerCase()"
                          stroke-width="1.5"
                          marker-end="url(#arrow)"></line>
                    <text [attr.x]="(coords.x1 + coords.x2) / 2"
                          [attr.y]="(coords.y1 + coords.y2) / 2 - 4"
                          class="edge-label">{{ e.kind }}</text>
                  </g>
                }
              }
              <!-- Nodes -->
              @for (n of positionedNodes(); track n.id) {
                <g class="node"
                   [class.focused]="n.id === focusedId()"
                   (click)="focus(n)"
                   [attr.transform]="'translate(' + n.x + ',' + n.y + ')'">
                  <rect x="-60" y="-22" width="120" height="44" rx="8"
                        [class]="'kind-' + n.kind.toLowerCase()"></rect>
                  <text class="node-kind" y="-4">{{ n.kind }}</text>
                  <text class="node-label" y="12">{{ truncate(n.label) }}</text>
                </g>
              }
              <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5"
                        markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor"></path>
                </marker>
              </defs>
            </svg>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .lineage { padding: 16px; }
    .header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 16px; gap: 16px;
    }
    .header h2 { margin: 0 0 4px 0; }
    .subtitle { color: var(--rdf-text-secondary); margin: 0; font-size: 0.9rem; max-width: 640px; }
    .centered { display: flex; justify-content: center; padding: 48px; }
    .empty { text-align: center; padding: 48px 16px; }
    .empty mat-card-content { display: flex; flex-direction: column; align-items: center; gap: 12px; }
    .empty .empty-icon { font-size: 64px; width: 64px; height: 64px; color: var(--rdf-text-secondary); }
    .focus-banner mat-card-content {
      display: flex; align-items: center; gap: 8px; padding: 8px 16px;
    }
    .focus-banner { margin-bottom: 12px; }
    .legend {
      display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px;
    }
    .graph-card { overflow: hidden; }
    .graph {
      width: 100%; height: auto; max-height: 70vh;
      background: var(--mat-sys-surface);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 4px;
    }
    .node { cursor: pointer; }
    .node rect { stroke: var(--mat-sys-outline); stroke-width: 1; transition: filter 0.15s; }
    .node.focused rect { stroke-width: 3; filter: brightness(1.05); }
    .node-kind {
      text-anchor: middle;
      font-size: 9px; font-weight: 600;
      fill: var(--mat-sys-on-surface);
      text-transform: uppercase;
    }
    .node-label {
      text-anchor: middle;
      font-size: 11px; fill: var(--mat-sys-on-surface);
    }
    .edge-label {
      text-anchor: middle;
      font-size: 9px; fill: var(--rdf-text-secondary);
      pointer-events: none;
    }
    .edge line { color: var(--mat-sys-outline); stroke: currentColor; }
    .edge-used_by line, line.edge-used_by { stroke: #0288d1; }
    .edge-derived_from line, line.edge-derived_from { stroke: #7b1fa2; }
    .edge-validated_by line, line.edge-validated_by { stroke: #388e3c; }
    .edge-produced line, line.edge-produced { stroke: #f57c00; }
    .edge-belongs_to line, line.edge-belongs_to { stroke: #9e9e9e; stroke-dasharray: 4 4; }
    .edge-references line, line.edge-references { stroke: #5d4037; stroke-dasharray: 2 2; }

    /* Node fills per kind */
    rect.kind-project { fill: #eceff1; }
    rect.kind-data_source { fill: #e1f5fe; }
    rect.kind-mapping { fill: #fff3e0; }
    rect.kind-ontology { fill: #f3e5f5; }
    rect.kind-shape { fill: #e8f5e9; }
    rect.kind-pipeline { fill: #fff8e1; }
    rect.kind-job { fill: #fce4ec; }
    rect.kind-triplestore { fill: #e0f2f1; }
    rect.kind-release { fill: #e8eaf6; }

    /* Legend chips match rect fills */
    mat-chip.kind-project { background: #eceff1 !important; }
    mat-chip.kind-data_source { background: #e1f5fe !important; }
    mat-chip.kind-mapping { background: #fff3e0 !important; }
    mat-chip.kind-ontology { background: #f3e5f5 !important; }
    mat-chip.kind-shape { background: #e8f5e9 !important; }
    mat-chip.kind-pipeline { background: #fff8e1 !important; }
    mat-chip.kind-job { background: #fce4ec !important; }
    mat-chip.kind-triplestore { background: #e0f2f1 !important; }
    mat-chip.kind-release { background: #e8eaf6 !important; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineageGraphComponent implements OnInit {
  private readonly svc = inject(LineageService);

  readonly projectId = input.required<string>();

  readonly graph = signal<LineageGraph | null>(null);
  readonly loading = signal(false);
  readonly focusedId = signal<string | null>(null);

  /** Banded kinds in fixed display order so layout is stable across reloads. */
  readonly legendKinds: LineageNodeKind[] = [
    'PROJECT',
    'DATA_SOURCE',
    'ONTOLOGY',
    'MAPPING',
    'SHAPE',
    'PIPELINE',
    'JOB',
    'TRIPLESTORE',
    'RELEASE'
  ];

  readonly positionedNodes = computed<PositionedNode[]>(() => {
    const g = this.graph();
    if (!g) return [];
    // Group nodes by kind preserving the legend order.
    const byKind = new Map<LineageNodeKind, LineageNode[]>();
    for (const k of this.legendKinds) byKind.set(k, []);
    for (const n of g.nodes) {
      const bucket = byKind.get(n.kind) ?? [];
      bucket.push(n);
      byKind.set(n.kind, bucket);
    }
    const bandH = 100;
    const nodeSpacing = 150;
    const result: PositionedNode[] = [];
    let bandIndex = 0;
    for (const k of this.legendKinds) {
      const bucket = byKind.get(k) ?? [];
      if (bucket.length === 0) continue;
      const y = 60 + bandIndex * bandH;
      const rowWidth = Math.max(1, bucket.length) * nodeSpacing;
      const startX = Math.max(80, (this.canvasW() - rowWidth) / 2 + nodeSpacing / 2);
      bucket.forEach((n, i) => {
        result.push({ ...n, x: startX + i * nodeSpacing, y });
      });
      bandIndex++;
    }
    return result;
  });

  readonly canvasW = computed(() => {
    const g = this.graph();
    if (!g) return 800;
    // Widen based on the biggest band so cramped kinds aren't clipped.
    const byKind = new Map<LineageNodeKind, number>();
    for (const n of g.nodes) byKind.set(n.kind, (byKind.get(n.kind) ?? 0) + 1);
    const biggest = Math.max(1, ...Array.from(byKind.values()));
    return Math.max(800, biggest * 160 + 120);
  });

  readonly canvasH = computed(() => {
    const g = this.graph();
    if (!g) return 400;
    const kindsUsed = new Set(g.nodes.map(n => n.kind)).size;
    return Math.max(300, 100 + kindsUsed * 100);
  });

  readonly visibleEdges = computed<LineageEdge[]>(() => {
    const g = this.graph();
    if (!g) return [];
    const focused = this.focusedId();
    if (!focused) return g.edges;
    return g.edges.filter(e => e.from === focused || e.to === focused);
  });

  readonly focusedLabel = computed(() => {
    const g = this.graph();
    const id = this.focusedId();
    if (!g || !id) return '';
    return g.nodes.find(n => n.id === id)?.label ?? id;
  });

  constructor() {
    effect(() => {
      const pid = this.projectId();
      if (pid) this.reloadProject();
    });
  }

  ngOnInit(): void { /* effect handles initial load */ }

  reloadProject(): void {
    const pid = this.projectId();
    if (!pid) return;
    this.loading.set(true);
    this.focusedId.set(null);
    this.svc.forProject(pid).subscribe({
      next: g => { this.graph.set(g); this.loading.set(false); },
      error: () => { this.graph.set(null); this.loading.set(false); }
    });
  }

  focus(n: LineageNode): void {
    this.focusedId.set(n.id === this.focusedId() ? null : n.id);
  }

  clearFocus(): void { this.focusedId.set(null); }

  edgeCoords(e: LineageEdge): { x1: number; y1: number; x2: number; y2: number } | null {
    const from = this.positionedNodes().find(n => n.id === e.from);
    const to = this.positionedNodes().find(n => n.id === e.to);
    if (!from || !to) return null;
    return { x1: from.x, y1: from.y, x2: to.x, y2: to.y };
  }

  truncate(s: string): string {
    return s.length > 18 ? s.substring(0, 16) + '…' : s;
  }
}
