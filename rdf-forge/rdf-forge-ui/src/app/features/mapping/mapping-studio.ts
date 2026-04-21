import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MappingService } from '../../core/services/mapping.service';
import {
  Mapping,
  MappingRule,
  TripleDto,
  TripleExplain,
  RowExplain
} from '../../core/models/mapping.model';
import { RuleEditor } from './rule-editor';

interface SampleRow { [col: string]: unknown }

/**
 * Universal Mapping Studio — the 4-panel workbench:
 *
 * <ul>
 *   <li><b>Left — Source</b>: the sample rows (pasted JSON or CSV) that
 *       preview/explain operate against. Highlights the columns consumed by
 *       the selected rule.</li>
 *   <li><b>Center-left — Rules</b>: add/edit/delete/reorder rules. Each row
 *       is collapsible to show its transform inline.</li>
 *   <li><b>Center-right — Preview</b>: live-generated triples, refreshed on
 *       rule or source changes with a 500 ms debounce.</li>
 *   <li><b>Right — Explain</b>: the trace for the clicked triple, including
 *       rule id, source value, transforms applied, final value.</li>
 * </ul>
 *
 * Inline error surface: preview failures are shown inside the Preview panel
 * with the offending rule id, not via snackbar toasts — this keeps the UX
 * focused on the rule the user just edited.
 */
@Component({
  selector: 'app-mapping-studio',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTabsModule,
    MatTooltipModule,
    MatChipsModule,
    MatExpansionModule
  ],
  template: `
    <div class="studio">
      <header class="header">
        <div class="title-row">
          <a mat-icon-button [routerLink]="['/projects', projectIdFromMapping()]">
            <mat-icon>arrow_back</mat-icon>
          </a>
          <h2>{{ mapping()?.name ?? 'Loading…' }}</h2>
          @if (mapping()) {
            <mat-chip>v{{ mapping()!.version }}</mat-chip>
            <mat-chip class="type-chip">{{ mapping()!.mappingType }}</mat-chip>
          }
        </div>
        <div class="actions">
          <button mat-button (click)="validate()" [disabled]="!mapping()">
            <mat-icon>check_circle</mat-icon> Validate
          </button>
          <button mat-raised-button color="primary" (click)="save()"
                  [disabled]="!mapping() || saving()">
            <mat-icon>save</mat-icon>
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </header>

      @if (loading()) {
        <div class="centered"><mat-spinner diameter="48"></mat-spinner></div>
      } @else if (mapping()) {
        <div class="panels">

          <!-- ── Source panel ─────────────────────────────── -->
          <mat-card class="panel source-panel">
            <mat-card-header>
              <mat-card-title>Source</mat-card-title>
              <mat-card-subtitle>Paste sample rows as JSON array</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Sample JSON rows</mat-label>
                <textarea matInput rows="8"
                          [ngModel]="sampleJson()"
                          (ngModelChange)="onSampleJsonChange($event)"
                          placeholder='[{"id":"1","name":"Alice"}]'></textarea>
              </mat-form-field>
              @if (sampleParseError()) {
                <div class="error-inline">
                  <mat-icon>error_outline</mat-icon>
                  {{ sampleParseError() }}
                </div>
              }
              @if (sampleRows().length > 0) {
                <div class="columns">
                  <span class="label">Columns:</span>
                  @for (col of columns(); track col) {
                    <mat-chip [class.highlighted]="highlightedColumns().includes(col)">
                      {{ col }}
                    </mat-chip>
                  }
                </div>
                <table mat-table [dataSource]="sampleRows()" class="sample-table">
                  @for (col of columns(); track col) {
                    <ng-container [matColumnDef]="col">
                      <th mat-header-cell *matHeaderCellDef>{{ col }}</th>
                      <td mat-cell *matCellDef="let r"
                          [class.highlighted]="highlightedColumns().includes(col)">
                        {{ r[col] }}
                      </td>
                    </ng-container>
                  }
                  <tr mat-header-row *matHeaderRowDef="columns()"></tr>
                  <tr mat-row *matRowDef="let row; columns: columns()"></tr>
                </table>
              }
            </mat-card-content>
          </mat-card>

          <!-- ── Rules panel ──────────────────────────────── -->
          <mat-card class="panel rules-panel">
            <mat-card-header>
              <mat-card-title>Rules</mat-card-title>
              <mat-card-subtitle>{{ mapping()!.rules.length }} total</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <button mat-raised-button color="primary" (click)="addRule()" class="add-rule">
                <mat-icon>add</mat-icon> Add rule
              </button>

              @if (mapping()!.rules.length === 0) {
                <p class="hint">No rules yet. Add one to start generating triples.</p>
              }

              @for (rule of mapping()!.rules; track rule.id; let i = $index) {
                <mat-expansion-panel class="rule-card"
                                     [class.highlighted]="highlightedRuleId() === rule.id">
                  <mat-expansion-panel-header>
                    <mat-panel-title>
                      <mat-icon class="rule-icon">{{ ruleIcon(rule) }}</mat-icon>
                      {{ rule.id }}
                    </mat-panel-title>
                    <mat-panel-description>
                      <span class="rule-type">{{ rule.type }}</span>
                      @if (rule.source) { <span class="rule-source">← {{ rule.source }}</span> }
                      @if (rule.target) { <span class="rule-target">→ {{ shortIri(rule.target) }}</span> }
                    </mat-panel-description>
                  </mat-expansion-panel-header>

                  <div class="rule-details">
                    @if (rule.uriTemplate) {
                      <div><strong>Template:</strong> <code>{{ rule.uriTemplate }}</code></div>
                    }
                    @if (rule.datatype) {
                      <div><strong>Datatype:</strong> {{ rule.datatype }}</div>
                    }
                    @if (rule.language) {
                      <div><strong>Language:</strong> {{ rule.language }}</div>
                    }
                    @if (rule.transform) {
                      <div>
                        <strong>Transform:</strong> {{ rule.transform.type }}
                        @if (rule.transform.params) {
                          <code>{{ rule.transform.params | json }}</code>
                        }
                      </div>
                    }
                    <div class="rule-actions">
                      <button mat-icon-button (click)="editRule(i)" aria-label="edit rule"><mat-icon>edit</mat-icon></button>
                      <button mat-icon-button (click)="moveRule(i, -1)"
                              [disabled]="i === 0" aria-label="move up"><mat-icon>arrow_upward</mat-icon></button>
                      <button mat-icon-button (click)="moveRule(i, +1)"
                              [disabled]="i === mapping()!.rules.length - 1"
                              aria-label="move down"><mat-icon>arrow_downward</mat-icon></button>
                      <button mat-icon-button (click)="deleteRule(i)" aria-label="delete rule"><mat-icon>delete</mat-icon></button>
                    </div>
                  </div>
                </mat-expansion-panel>
              }
            </mat-card-content>
          </mat-card>

          <!-- ── Preview panel ────────────────────────────── -->
          <mat-card class="panel preview-panel">
            <mat-card-header>
              <mat-card-title>RDF Preview</mat-card-title>
              <mat-card-subtitle>
                {{ preview()?.triples?.length ?? 0 }} triples from
                {{ preview()?.sampleSize ?? 0 }} rows
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              @if (previewLoading()) {
                <div class="centered-small"><mat-spinner diameter="24"></mat-spinner></div>
              }
              @if (previewError()) {
                <div class="error-inline">
                  <mat-icon>error</mat-icon>
                  <div>
                    <strong>Preview failed</strong>
                    <div>{{ previewError() }}</div>
                  </div>
                </div>
              } @else if (preview() && preview()!.triples.length > 0) {
                <table mat-table [dataSource]="preview()!.triples" class="preview-table">
                  <ng-container matColumnDef="subject">
                    <th mat-header-cell *matHeaderCellDef>Subject</th>
                    <td mat-cell *matCellDef="let t">{{ shortIri(t.subject) }}</td>
                  </ng-container>
                  <ng-container matColumnDef="predicate">
                    <th mat-header-cell *matHeaderCellDef>Predicate</th>
                    <td mat-cell *matCellDef="let t">{{ shortIri(t.predicate) }}</td>
                  </ng-container>
                  <ng-container matColumnDef="object">
                    <th mat-header-cell *matHeaderCellDef>Object</th>
                    <td mat-cell *matCellDef="let t">
                      @if (t.objectType === 'URI') {
                        {{ shortIri(t.object) }}
                      } @else {
                        "{{ t.object }}"
                        @if (t.datatype) { <span class="dt">^^{{ shortIri(t.datatype) }}</span> }
                        @if (t.language) { <span class="dt">&#64;{{ t.language }}</span> }
                      }
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="previewColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: previewColumns; let i = index"
                      class="clickable-row"
                      [class.selected]="selectedTripleIndex() === i"
                      (click)="onTripleClick(i, row)"></tr>
                </table>
              } @else if (preview()) {
                <p class="hint">No triples produced. Add rules or sample data.</p>
              } @else {
                <p class="hint">Add sample rows and rules to see live triples.</p>
              }
            </mat-card-content>
          </mat-card>

          <!-- ── Explain panel ────────────────────────────── -->
          <mat-card class="panel explain-panel">
            <mat-card-header>
              <mat-card-title>Explain</mat-card-title>
              <mat-card-subtitle>
                @if (selectedRow()) {
                  Row {{ selectedRow()!.rowIndex }}
                } @else {
                  Click a triple to inspect
                }
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              @if (explainLoading()) {
                <div class="centered-small"><mat-spinner diameter="24"></mat-spinner></div>
              }
              @if (selectedRow()) {
                <div class="explain-block">
                  <h4>Source row</h4>
                  <pre>{{ selectedRow()!.row | json }}</pre>
                </div>
                <div class="explain-block">
                  <h4>Triples ({{ selectedRow()!.triples.length }})</h4>
                  @for (te of selectedRow()!.triples; track te.trace.ruleId) {
                    <div class="trace-card"
                         [class.selected]="selectedTripleKey() === traceKey(te)">
                      <div class="trace-head">
                        <mat-chip>{{ te.trace.ruleType }}</mat-chip>
                        <code>{{ te.trace.ruleId }}</code>
                      </div>
                      <div class="trace-body">
                        @if (te.trace.source) {
                          <div><strong>Source:</strong> <code>{{ te.trace.source }}</code>
                            @if (te.trace.sourceValue !== null && te.trace.sourceValue !== undefined) {
                              = <code>{{ te.trace.sourceValue }}</code>
                            }
                          </div>
                        }
                        @if (te.trace.uriTemplateUsed) {
                          <div><strong>Template:</strong> <code>{{ te.trace.uriTemplateUsed }}</code></div>
                        }
                        @if (te.trace.transforms && te.trace.transforms.length) {
                          <div>
                            <strong>Transforms:</strong>
                            @for (step of te.trace.transforms; track $index) {
                              <span class="transform-step">
                                {{ step.type }}:
                                <code>{{ step.inputValue }}</code> → <code>{{ step.outputValue }}</code>
                              </span>
                            }
                          </div>
                        }
                        <div><strong>Triple:</strong></div>
                        <div class="trace-triple">
                          {{ shortIri(te.triple.subject) }} {{ shortIri(te.triple.predicate) }}
                          @if (te.triple.objectType === 'URI') {
                            {{ shortIri(te.triple.object) }}
                          } @else {
                            "{{ te.triple.object }}"
                          }
                        </div>
                      </div>
                    </div>
                  }
                </div>
              } @else {
                <p class="hint">Select a triple to see which rule produced it.</p>
              }
            </mat-card-content>
          </mat-card>
        </div>
      }
    </div>
  `,
  styles: [`
    .studio { display: flex; flex-direction: column; height: 100%; padding: 16px; gap: 16px; }
    .header {
      display: flex; justify-content: space-between; align-items: center;
    }
    .title-row { display: flex; align-items: center; gap: 12px; h2 { margin: 0; } }
    .actions { display: flex; gap: 8px; }
    .type-chip { font-weight: 600; }
    .panels {
      display: grid;
      grid-template-columns: 1fr 1fr 1.1fr 1fr;
      gap: 12px;
      flex: 1;
      min-height: 0;
    }
    .panel {
      display: flex; flex-direction: column; min-height: 0; overflow: hidden;
      mat-card-content { overflow: auto; flex: 1; }
    }
    .full-width { width: 100%; }
    .columns { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0; align-items: center; }
    .columns .label { font-size: 0.85rem; color: var(--rdf-text-secondary); }
    .columns mat-chip.highlighted,
    .sample-table td.highlighted { background: rgba(255, 193, 7, 0.25) !important; }
    .sample-table { width: 100%; font-size: 0.85rem; }
    .preview-table { width: 100%; font-size: 0.85rem; }
    .preview-table .clickable-row { cursor: pointer; }
    .preview-table .clickable-row.selected { background: rgba(33, 150, 243, 0.12) !important; }
    .rule-card { margin-bottom: 8px; }
    .rule-card.highlighted { outline: 2px solid var(--mat-sys-primary); border-radius: 4px; }
    .rule-icon { margin-right: 8px; }
    .rule-type {
      background: var(--mat-sys-surface-container);
      padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; margin-right: 8px;
    }
    .rule-source, .rule-target { font-family: monospace; font-size: 0.85rem; margin-right: 6px; }
    .rule-details > div { margin: 4px 0; font-size: 0.85rem; }
    .rule-actions { display: flex; gap: 2px; margin-top: 6px; }
    .add-rule { margin-bottom: 12px; }
    .hint { color: var(--rdf-text-secondary); font-style: italic; }
    .error-inline {
      display: flex; gap: 8px; align-items: flex-start;
      background: #fff3f3; color: #8b0000; padding: 12px; border-radius: 4px;
      border-left: 4px solid #d32f2f; font-size: 0.9rem;
    }
    .centered { display: flex; justify-content: center; padding: 64px; }
    .centered-small { display: flex; justify-content: center; padding: 16px; }
    .dt { color: var(--rdf-text-secondary); font-size: 0.85em; }
    .explain-block { margin-bottom: 16px; }
    .explain-block pre {
      background: var(--mat-sys-surface-container); padding: 8px; border-radius: 4px;
      font-size: 0.8rem; overflow: auto; max-height: 120px;
    }
    .trace-card {
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 4px; padding: 8px; margin-bottom: 8px;
    }
    .trace-card.selected { border-color: var(--mat-sys-primary); }
    .trace-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
    .trace-body > div { margin: 4px 0; font-size: 0.85rem; }
    .trace-triple {
      background: var(--mat-sys-surface-container); padding: 6px; border-radius: 4px;
      font-family: monospace; font-size: 0.8rem; word-break: break-all;
    }
    .transform-step {
      display: inline-block; margin-right: 8px; font-size: 0.8rem;
      code { background: var(--mat-sys-surface-container); padding: 2px 4px; border-radius: 2px; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MappingStudio implements OnInit, OnDestroy {
  private readonly svc = inject(MappingService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();
  private readonly previewTrigger$ = new Subject<void>();

  readonly mapping = signal<Mapping | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly previewLoading = signal(false);
  readonly explainLoading = signal(false);
  readonly preview = signal<{ triples: TripleDto[]; sampleSize: number } | null>(null);
  readonly previewError = signal<string | null>(null);

  readonly sampleJson = signal('[\n  {"id": "1", "name": "Alice"},\n  {"id": "2", "name": "Bob"}\n]');
  readonly sampleRows = signal<SampleRow[]>([]);
  readonly sampleParseError = signal<string | null>(null);
  readonly columns = computed(() => {
    const rows = this.sampleRows();
    if (!rows.length) return [];
    const set = new Set<string>();
    rows.forEach(r => Object.keys(r).forEach(k => set.add(k)));
    return Array.from(set);
  });

  readonly selectedTripleIndex = signal<number | null>(null);
  readonly selectedRow = signal<RowExplain | null>(null);
  readonly selectedTripleKey = signal<string | null>(null);
  readonly highlightedColumns = signal<string[]>([]);
  readonly highlightedRuleId = signal<string | null>(null);

  readonly previewColumns = ['subject', 'predicate', 'object'];

  private mappingId: string | null = null;

  projectIdFromMapping(): string { return this.mapping()?.projectId ?? ''; }

  ngOnInit(): void {
    this.parseSample(this.sampleJson());
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.mappingId = params.get('id');
      if (this.mappingId) this.load(this.mappingId);
    });
    this.previewTrigger$.pipe(
      takeUntil(this.destroy$),
      debounceTime(500)
    ).subscribe(() => this.runPreview());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private load(id: string): void {
    this.loading.set(true);
    this.svc.get(id).subscribe({
      next: m => {
        this.mapping.set(m);
        this.loading.set(false);
        this.schedulePreview();
      },
      error: err => {
        this.loading.set(false);
        this.snack.open('Failed to load mapping: ' + (err?.message ?? err), 'Dismiss',
          { duration: 4000 });
      }
    });
  }

  onSampleJsonChange(value?: string): void {
    if (value !== undefined) this.sampleJson.set(value);
    this.parseSample(this.sampleJson());
    this.schedulePreview();
  }

  private parseSample(raw: string): void {
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        this.sampleParseError.set('Sample must be a JSON array');
        this.sampleRows.set([]);
        return;
      }
      this.sampleRows.set(parsed as SampleRow[]);
      this.sampleParseError.set(null);
    } catch (e) {
      this.sampleParseError.set('Invalid JSON: ' + (e as Error).message);
    }
  }

  private schedulePreview(): void {
    if (!this.mapping() || this.sampleRows().length === 0) {
      this.preview.set(null);
      return;
    }
    this.previewTrigger$.next();
  }

  private runPreview(): void {
    const m = this.mapping();
    if (!m || this.sampleRows().length === 0) return;
    this.previewLoading.set(true);
    this.previewError.set(null);
    this.svc.preview(m.id, {
      sourceRows: this.sampleRows() as Record<string, unknown>[],
      sampleLimit: 10
    }).subscribe({
      next: resp => {
        this.preview.set({ triples: resp.triples, sampleSize: resp.sampleSize });
        this.previewLoading.set(false);
      },
      error: err => {
        this.previewLoading.set(false);
        const detail = err?.error?.detail ?? err?.message ?? String(err);
        this.previewError.set(detail);
      }
    });
  }

  onTripleClick(index: number, triple: TripleDto): void {
    const m = this.mapping();
    if (!m || this.sampleRows().length === 0) return;
    this.selectedTripleIndex.set(index);
    this.explainLoading.set(true);
    const triplesPerRow = Math.max(1, Math.floor(
      (this.preview()?.triples.length ?? 1) / Math.max(1, this.preview()?.sampleSize ?? 1)
    ));
    const rowGuess = Math.min(
      this.sampleRows().length - 1,
      Math.floor(index / triplesPerRow)
    );
    this.svc.explain(m.id, {
      sourceRows: this.sampleRows() as Record<string, unknown>[],
      sourceRowIndex: rowGuess
    }).subscribe({
      next: resp => {
        this.explainLoading.set(false);
        const row = resp.rows[0] ?? null;
        this.selectedRow.set(row);
        if (row) {
          // Find the matching TripleExplain to highlight cells and rule.
          const match = row.triples.find(te => this.tripleEq(te.triple, triple));
          if (match) {
            this.selectedTripleKey.set(this.traceKey(match));
            this.highlightedRuleId.set(match.trace.ruleId);
            this.highlightedColumns.set(match.trace.source ? [match.trace.source] : []);
          }
        }
      },
      error: err => {
        this.explainLoading.set(false);
        this.snack.open('Explain failed: ' + (err?.message ?? err), 'Dismiss',
          { duration: 4000 });
      }
    });
  }

  traceKey(te: TripleExplain): string {
    return te.trace.ruleId + '|' + te.triple.subject + '|' + te.triple.object;
  }

  private tripleEq(a: TripleDto, b: TripleDto): boolean {
    return a.subject === b.subject
      && a.predicate === b.predicate
      && a.object === b.object
      && a.objectType === b.objectType;
  }

  addRule(): void {
    const m = this.mapping();
    if (!m) return;
    const newRule: MappingRule = {
      id: 'rule-' + (m.rules.length + 1),
      type: 'COLUMN_TO_LITERAL',
      source: null,
      target: null,
      uriTemplate: null,
      datatype: null,
      language: null,
      transform: null
    };
    this.openEditor(newRule, -1);
  }

  editRule(index: number): void {
    const m = this.mapping();
    if (!m) return;
    this.openEditor({ ...m.rules[index] }, index);
  }

  private openEditor(rule: MappingRule, index: number): void {
    const ref = this.dialog.open(RuleEditor, {
      width: '640px',
      data: {
        rule,
        availableColumns: this.columns(),
        targetPredicates: []
      }
    });
    ref.afterClosed().subscribe((updated: MappingRule | undefined) => {
      if (!updated) return;
      const m = this.mapping();
      if (!m) return;
      const rules = [...m.rules];
      if (index === -1) rules.push(updated);
      else rules[index] = updated;
      this.mapping.set({ ...m, rules });
      this.schedulePreview();
    });
  }

  deleteRule(index: number): void {
    const m = this.mapping();
    if (!m) return;
    const rules = m.rules.filter((_, i) => i !== index);
    this.mapping.set({ ...m, rules });
    this.schedulePreview();
  }

  moveRule(index: number, delta: number): void {
    const m = this.mapping();
    if (!m) return;
    const target = index + delta;
    if (target < 0 || target >= m.rules.length) return;
    const rules = [...m.rules];
    const [row] = rules.splice(index, 1);
    rules.splice(target, 0, row);
    this.mapping.set({ ...m, rules });
    this.schedulePreview();
  }

  save(): void {
    const m = this.mapping();
    if (!m) return;
    this.saving.set(true);
    this.svc.update(m.id, {
      name: m.name,
      description: m.description,
      sourceType: m.sourceType,
      sourceConfig: m.sourceConfig,
      targetNamespace: m.targetNamespace,
      targetOntologies: m.targetOntologies,
      rules: m.rules
    }).subscribe({
      next: updated => {
        this.saving.set(false);
        this.mapping.set(updated);
        this.snack.open('Saved', 'Dismiss', { duration: 2000 });
      },
      error: err => {
        this.saving.set(false);
        this.snack.open('Save failed: ' + (err?.error?.detail ?? err?.message ?? err),
          'Dismiss', { duration: 6000 });
      }
    });
  }

  validate(): void {
    const m = this.mapping();
    if (!m) return;
    this.svc.validate(m.id, { availableColumns: this.columns() }).subscribe({
      next: resp => {
        if (resp.valid) {
          this.snack.open('Mapping is valid', 'Dismiss', { duration: 2000 });
        } else {
          const first = resp.issues[0];
          this.snack.open(
            `Invalid (${resp.issues.length} issue${resp.issues.length === 1 ? '' : 's'}): ${first.message}`,
            'Dismiss', { duration: 6000 });
        }
      },
      error: err => this.snack.open('Validate failed: ' + (err?.message ?? err), 'Dismiss',
        { duration: 4000 })
    });
  }

  ruleIcon(r: MappingRule): string {
    switch (r.type) {
      case 'FIXED_URI': return 'link';
      case 'COLUMN_TO_URI': return 'arrow_outward';
      case 'COLUMN_TO_LITERAL': return 'text_fields';
      case 'NESTED': return 'account_tree';
      case 'CONSTANT': return 'flag';
      default: return 'rule';
    }
  }

  /** Short pretty-print for well-known namespaces; keeps the tables readable. */
  shortIri(iri: string): string {
    if (!iri) return iri;
    const known: Record<string, string> = {
      'http://www.w3.org/2001/XMLSchema#': 'xsd:',
      'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf:',
      'http://www.w3.org/2000/01/rdf-schema#': 'rdfs:',
      'http://www.w3.org/2004/02/skos/core#': 'skos:',
      'http://purl.org/linked-data/cube#': 'qb:',
      'https://cube.link/': 'cube:',
      'http://xmlns.com/foaf/0.1/': 'foaf:',
      'http://www.w3.org/ns/shacl#': 'sh:'
    };
    for (const [ns, pfx] of Object.entries(known)) {
      if (iri.startsWith(ns)) return pfx + iri.substring(ns.length);
    }
    return iri;
  }
}
