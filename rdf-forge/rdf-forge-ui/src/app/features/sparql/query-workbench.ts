import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { SavedQueryService } from '../../core/services/saved-query.service';
import { TriplestoreService } from '../../core/services/triplestore.service';
import {
  SavedQuery,
  SavedQueryCreateRequest,
  SavedQueryParameterSpec,
  SavedQueryParameterType,
  SavedQueryRunParameter,
  SavedQueryRunResponse,
  SavedQueryType
} from '../../core/models/saved-query.model';
import { TriplestoreConnection } from '../../core/models/triplestore.model';

/** Matches placeholders like ?foo and $foo — we highlight these as parameters. */
const PARAM_PATTERN = /[?$]([A-Za-z_][A-Za-z0-9_]*)/g;

@Component({
  selector: 'rdf-query-workbench',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="workbench">
      <!-- Left: saved queries -->
      <aside class="pane left">
        <header class="pane-head">
          <h3>Saved Queries</h3>
          <button mat-icon-button color="primary" (click)="newQuery()" aria-label="New query" matTooltip="New query">
            <mat-icon>add</mat-icon>
          </button>
        </header>

        <mat-form-field appearance="outline" class="filter">
          <mat-label>Filter by tag</mat-label>
          <input matInput [(ngModel)]="tagFilter" (change)="refreshList()" />
        </mat-form-field>

        @if (queries().length === 0) {
          <div class="empty">No saved queries yet.</div>
        } @else {
          <ul class="saved-list">
            @for (q of queries(); track q.id) {
              <li (click)="loadQuery(q)" [class.active]="selectedQueryId() === q.id">
                <div class="title">{{ q.name }}</div>
                <div class="meta">
                  <span class="type">{{ q.type }}</span>
                  @if (q.tags && q.tags.length > 0) {
                    <span class="tags">
                      @for (t of q.tags; track t) {
                        <span class="tag">{{ t }}</span>
                      }
                    </span>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </aside>

      <!-- Center: editor + params + results -->
      <section class="pane center">
        <header class="center-head">
          <mat-form-field appearance="outline" class="name-field">
            <mat-label>Query name</mat-label>
            <input matInput [(ngModel)]="queryName" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="type-field">
            <mat-label>Type</mat-label>
            <mat-select [(value)]="queryType">
              <mat-option value="SELECT">SELECT</mat-option>
              <mat-option value="ASK">ASK</mat-option>
              <mat-option value="CONSTRUCT">CONSTRUCT</mat-option>
              <mat-option value="DESCRIBE">DESCRIBE</mat-option>
              <mat-option value="UPDATE">UPDATE</mat-option>
            </mat-select>
          </mat-form-field>

          <div class="actions">
            <button mat-stroked-button (click)="onRun()" [disabled]="running()">
              <mat-icon>play_arrow</mat-icon> Run
            </button>
            <button mat-stroked-button (click)="saveAs()">
              <mat-icon>save_as</mat-icon> Save As
            </button>
            <button mat-flat-button color="primary" (click)="save()" [disabled]="!canSave()">
              <mat-icon>save</mat-icon> Save
            </button>
            <button mat-stroked-button (click)="exportResults()" [disabled]="!lastResult()">
              <mat-icon>download</mat-icon> Export
            </button>
          </div>
        </header>

        <!-- Editor (textarea fallback — Monaco wiring is a TODO). -->
        <div class="editor-wrap">
          <textarea
            class="editor"
            spellcheck="false"
            [(ngModel)]="queryText"
            (ngModelChange)="onQueryTextChange()"
            placeholder="SELECT * WHERE { ?s ?p ?o } LIMIT 10"
            aria-label="SPARQL query editor"></textarea>
        </div>

        <!-- Parameters -->
        @if (detectedParams().length > 0) {
          <div class="params">
            <strong>Parameters</strong>
            <div class="param-grid">
              @for (p of detectedParams(); track p) {
                <div class="param-row">
                  <label>{{ p }}</label>
                  <select [ngModel]="paramType(p)" (ngModelChange)="setParamType(p, $event)">
                    <option value="literal">literal</option>
                    <option value="uri">uri</option>
                    <option value="number">number</option>
                  </select>
                  <input
                    type="text"
                    [ngModel]="paramValue(p)"
                    (ngModelChange)="setParamValue(p, $event)"
                    placeholder="value"
                    [attr.aria-label]="'Parameter ' + p + ' value'" />
                </div>
              }
            </div>
          </div>
        }

        <!-- Results -->
        <div class="results">
          @if (running()) {
            <div class="running"><mat-spinner diameter="32"></mat-spinner><span>Running...</span></div>
          } @else if (lastError()) {
            <div class="error">{{ lastError() }}</div>
          } @else if (lastResult(); as r) {
            @switch (r.type) {
              @case ('ASK') {
                <div class="ask-badge" [class.yes]="r.askResult === true" [class.no]="r.askResult === false">
                  ASK → {{ r.askResult === true ? 'true' : 'false' }}
                </div>
              }
              @case ('SELECT') {
                @if (r.bindings && r.bindings.length > 0 && r.variables) {
                  <table class="select-table">
                    <thead>
                      <tr>
                        @for (v of r.variables; track v) {
                          <th>{{ v }}</th>
                        }
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of r.bindings; track $index) {
                        <tr>
                          @for (v of r.variables; track v) {
                            <td>{{ cellValue(row[v]) }}</td>
                          }
                        </tr>
                      }
                    </tbody>
                  </table>
                } @else {
                  <div class="empty">No results.</div>
                }
              }
              @default {
                <!-- CONSTRUCT / DESCRIBE / UPDATE: fallback textual view. -->
                <pre class="ttl">{{ r.rdf ?? (r.bindings | json) }}</pre>
              }
            }

            <div class="result-meta">
              <span>Duration: {{ r.durationMs }} ms</span>
              @if (r.bindings) { <span>Rows: {{ r.bindings.length }}</span> }
              <span>{{ r.executedAt | date:'medium' }}</span>
            </div>
          } @else {
            <div class="empty">Run a query to see results.</div>
          }
        </div>
      </section>

      <!-- Right: triplestore & graph -->
      <aside class="pane right">
        <mat-form-field appearance="outline">
          <mat-label>Triplestore</mat-label>
          <mat-select [(value)]="triplestoreId">
            @for (ts of triplestores(); track ts.id) {
              <mat-option [value]="ts.id">{{ ts.name }} ({{ ts.type }})</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Graph (optional)</mat-label>
          <input matInput [(ngModel)]="graph" placeholder="http://example.org/graph" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput rows="3" [(ngModel)]="description"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Tags (comma-separated)</mat-label>
          <input matInput [(ngModel)]="tagInput" />
        </mat-form-field>
      </aside>
    </div>
  `,
  styles: [`
    .workbench { display: grid; grid-template-columns: 280px 1fr 300px; gap: 12px; padding: 16px; height: calc(100vh - 72px); }
    .pane { background: #fff; border: 1px solid rgba(0,0,0,.08); border-radius: 6px; padding: 12px; overflow: auto; display: flex; flex-direction: column; gap: 12px; }
    .pane-head { display: flex; align-items: center; justify-content: space-between; }
    .filter { width: 100%; }
    .saved-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
    .saved-list li { padding: 8px; border-radius: 4px; cursor: pointer; border: 1px solid transparent; }
    .saved-list li:hover { background: rgba(0,0,0,.04); }
    .saved-list li.active { background: rgba(25,118,210,.1); border-color: rgba(25,118,210,.4); }
    .saved-list .title { font-weight: 500; }
    .saved-list .meta { font-size: 11px; color: rgba(0,0,0,.6); display: flex; gap: 8px; }
    .saved-list .tag { background: rgba(0,0,0,.06); padding: 1px 6px; border-radius: 10px; margin-right: 4px; }
    .center-head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .name-field { flex: 1 1 200px; }
    .type-field { width: 140px; }
    .actions { display: flex; gap: 6px; margin-left: auto; }
    .editor-wrap { flex: 1 1 auto; min-height: 180px; }
    .editor { width: 100%; height: 100%; min-height: 180px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; border: 1px solid rgba(0,0,0,.12); border-radius: 4px; padding: 10px; resize: vertical; }
    .params { border: 1px dashed rgba(0,0,0,.12); padding: 8px; border-radius: 4px; }
    .param-grid { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; }
    .param-row { display: grid; grid-template-columns: 140px 120px 1fr; gap: 8px; align-items: center; }
    .param-row label { font-family: monospace; }
    .param-row select, .param-row input { padding: 4px; border: 1px solid rgba(0,0,0,.2); border-radius: 3px; }
    .results { border-top: 1px solid rgba(0,0,0,.08); padding-top: 10px; min-height: 120px; }
    .running { display: flex; gap: 8px; align-items: center; }
    .empty { color: rgba(0,0,0,.5); text-align: center; padding: 16px; }
    .error { color: #c62828; padding: 8px; background: #ffebee; border-radius: 4px; }
    .ask-badge { font-size: 20px; padding: 16px; border-radius: 4px; text-align: center; font-weight: 500; }
    .ask-badge.yes { background: #e8f5e9; color: #2e7d32; }
    .ask-badge.no  { background: #ffebee; color: #c62828; }
    .select-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .select-table th, .select-table td { text-align: left; padding: 4px 8px; border-bottom: 1px solid rgba(0,0,0,.08); }
    .select-table th { background: rgba(0,0,0,.04); position: sticky; top: 0; }
    .ttl { white-space: pre-wrap; font-size: 12px; max-height: 360px; overflow: auto; background: rgba(0,0,0,.03); padding: 10px; }
    .result-meta { display: flex; gap: 12px; font-size: 11px; color: rgba(0,0,0,.6); padding-top: 6px; }
  `]
})
export class QueryWorkbench implements OnInit {
  private readonly queryService = inject(SavedQueryService);
  private readonly triplestoreService = inject(TriplestoreService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  // State
  readonly queries = signal<SavedQuery[]>([]);
  readonly triplestores = signal<TriplestoreConnection[]>([]);
  readonly running = signal(false);
  readonly lastResult = signal<SavedQueryRunResponse | null>(null);
  readonly lastError = signal<string | null>(null);
  readonly selectedQueryId = signal<string | null>(null);
  private readonly paramTypes = signal<Record<string, SavedQueryParameterType>>({});
  private readonly paramValues = signal<Record<string, string>>({});

  // Bound to form
  queryName = '';
  description = '';
  queryText = '';
  queryType: SavedQueryType = 'SELECT';
  triplestoreId = '';
  graph = '';
  tagInput = '';
  tagFilter = '';

  readonly projectId = computed<string | null>(() => {
    return this.route.snapshot.queryParamMap.get('projectId');
  });

  /** Parameter names detected in the current query text (e.g. ?foo → "foo"). */
  readonly detectedParams = computed(() => {
    const seen = new Set<string>();
    const text = this.queryText;
    let match: RegExpExecArray | null;
    const re = new RegExp(PARAM_PATTERN);
    while ((match = re.exec(text)) !== null) {
      seen.add(match[1]);
    }
    return Array.from(seen);
  });

  ngOnInit(): void {
    this.triplestoreService.list().subscribe({
      next: list => {
        this.triplestores.set(list);
        const def = list.find(t => t.isDefault);
        if (def && !this.triplestoreId) this.triplestoreId = def.id;
      },
      error: () => this.triplestores.set([])
    });
    this.refreshList();
  }

  refreshList(): void {
    const pid = this.projectId();
    if (!pid) { this.queries.set([]); return; }
    const tags = this.tagFilter
      ? this.tagFilter.split(',').map(t => t.trim()).filter(t => !!t)
      : undefined;
    this.queryService.list(pid, tags).subscribe({
      next: list => this.queries.set(list),
      error: () => this.queries.set([])
    });
  }

  loadQuery(q: SavedQuery): void {
    this.selectedQueryId.set(q.id);
    this.queryName = q.name;
    this.description = q.description ?? '';
    this.queryType = q.type;
    this.queryText = q.queryText;
    this.tagInput = (q.tags ?? []).join(', ');
    // Seed parameter types/defaults from the saved query spec.
    const types: Record<string, SavedQueryParameterType> = {};
    const values: Record<string, string> = {};
    const params = q.parameters ?? {};
    for (const [k, spec] of Object.entries(params)) {
      types[k] = (spec as SavedQueryParameterSpec).type;
      if ((spec as SavedQueryParameterSpec).default !== undefined) {
        values[k] = (spec as SavedQueryParameterSpec).default ?? '';
      }
    }
    this.paramTypes.set(types);
    this.paramValues.set(values);
  }

  newQuery(): void {
    this.selectedQueryId.set(null);
    this.queryName = '';
    this.description = '';
    this.queryText = '';
    this.queryType = 'SELECT';
    this.tagInput = '';
    this.paramTypes.set({});
    this.paramValues.set({});
  }

  paramType(name: string): SavedQueryParameterType {
    return this.paramTypes()[name] ?? 'literal';
  }

  paramValue(name: string): string {
    return this.paramValues()[name] ?? '';
  }

  setParamType(name: string, type: SavedQueryParameterType): void {
    this.paramTypes.update(m => ({ ...m, [name]: type }));
  }

  setParamValue(name: string, value: string): void {
    this.paramValues.update(m => ({ ...m, [name]: value }));
  }

  onQueryTextChange(): void {
    // Detected params recompute automatically via signal.
  }

  canSave(): boolean {
    return !!this.queryName && !!this.queryText && !!this.projectId();
  }

  onRun(): void {
    if (!this.triplestoreId) {
      this.snackBar.open('Select a triplestore first', 'OK', { duration: 3000 });
      return;
    }
    if (!this.queryText) return;

    const parameters: Record<string, SavedQueryRunParameter> = {};
    for (const p of this.detectedParams()) {
      parameters[p] = { type: this.paramType(p), value: this.paramValue(p) };
    }

    this.running.set(true);
    this.lastError.set(null);
    this.lastResult.set(null);

    const saved = this.selectedQueryId();
    const obs = saved
      ? this.queryService.run(saved, {
          triplestoreId: this.triplestoreId,
          graph: this.graph || undefined,
          parameters
        })
      : this.queryService.runInline({
          queryText: this.queryText,
          triplestoreId: this.triplestoreId,
          graph: this.graph || undefined,
          parameters
        });

    obs.subscribe({
      next: (r) => {
        this.lastResult.set(r);
        this.running.set(false);
      },
      error: (err) => {
        this.lastError.set(err?.error?.detail ?? err?.message ?? 'Query failed');
        this.running.set(false);
      }
    });
  }

  save(): void {
    const pid = this.projectId();
    if (!pid) return;
    const req: SavedQueryCreateRequest = this.buildCreateRequest(pid);
    const existingId = this.selectedQueryId();
    if (existingId) {
      this.queryService.update(existingId, {
        name: req.name,
        description: req.description,
        type: req.type,
        queryText: req.queryText,
        parameters: req.parameters,
        tags: req.tags
      }).subscribe({
        next: (updated) => {
          this.snackBar.open('Saved', 'OK', { duration: 2000 });
          this.selectedQueryId.set(updated.id);
          this.refreshList();
        },
        error: (err) => this.snackBar.open('Save failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 })
      });
    } else {
      this.queryService.create(req).subscribe({
        next: (created) => {
          this.snackBar.open('Created', 'OK', { duration: 2000 });
          this.selectedQueryId.set(created.id);
          this.refreshList();
        },
        error: (err) => this.snackBar.open('Create failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 })
      });
    }
  }

  saveAs(): void {
    // Force a create path by clearing the selected id.
    this.selectedQueryId.set(null);
    const suggestedName = `${this.queryName || 'query'} (copy)`;
    this.queryName = suggestedName;
    this.save();
  }

  exportResults(): void {
    const r = this.lastResult();
    if (!r) return;
    let body = '';
    let mime = 'text/plain';
    let ext = 'txt';
    if (r.type === 'SELECT' && r.variables && r.bindings) {
      body = this.toCsv(r.variables, r.bindings);
      mime = 'text/csv';
      ext = 'csv';
    } else if ((r.type === 'CONSTRUCT' || r.type === 'DESCRIBE') && r.rdf) {
      body = r.rdf;
      mime = 'text/turtle';
      ext = 'ttl';
    } else {
      body = JSON.stringify(r, null, 2);
      mime = 'application/json';
      ext = 'json';
    }
    const blob = new Blob([body], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.queryName || 'query'}.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  }

  /** Ctrl/Cmd+Enter runs the query. */
  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.onRun();
    }
  }

  cellValue(cell: { value: string } | undefined): string {
    return cell ? cell.value : '';
  }

  private buildCreateRequest(projectId: string): SavedQueryCreateRequest {
    const params: Record<string, SavedQueryParameterSpec> = {};
    for (const p of this.detectedParams()) {
      params[p] = { type: this.paramType(p), default: this.paramValue(p) || undefined };
    }
    return {
      projectId,
      name: this.queryName,
      description: this.description || undefined,
      type: this.queryType,
      queryText: this.queryText,
      parameters: Object.keys(params).length > 0 ? params : undefined,
      tags: this.tagInput
        ? this.tagInput.split(',').map(t => t.trim()).filter(t => !!t)
        : undefined
    };
  }

  private toCsv(vars: string[], bindings: Record<string, { value: string }>[]): string {
    const escape = (s: string) => `"${s.replace(/"/g, '""')}"`;
    const rows: string[] = [vars.map(escape).join(',')];
    for (const b of bindings) {
      rows.push(vars.map(v => escape(b[v]?.value ?? '')).join(','));
    }
    return rows.join('\n');
  }
}
