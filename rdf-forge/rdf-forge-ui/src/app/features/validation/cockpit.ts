import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, finalize, of } from 'rxjs';

import {
  ReleaseGate,
  ValidationIssue,
  ValidationRun,
  ValidationSeverity,
  ValidationSuite
} from '../../core/models/validation.model';
import { ValidationService } from '../../core/services/validation.service';
import { SuiteEditor } from './suite-editor';
import { IssueDetailDialog } from './issue-detail-dialog';

/**
 * Phase 5 — Validation Cockpit.
 *
 * <p>Single project-scoped view combining every suite in the project,
 * its last-run health, its rule list and the live issue feed. Composed
 * from plain signals; no global store needed for Phase 5 since all
 * traffic is scoped to the current {@link #projectId}.
 *
 * <p>Layout (left → right):
 * <ol>
 *   <li>Suite list with last-run badge.</li>
 *   <li>Selected-suite detail (rules + history).</li>
 *   <li>Issue panel — filterable by severity.</li>
 * </ol>
 *
 * <p>TODOs (phase 5.1):
 * <ul>
 *   <li>Live progress push over WebSocket while a run is executing.</li>
 *   <li>Cross-service drill-down to the mapping rule that produced the
 *       offending resource (needs provenance hooks from pipeline-service).</li>
 * </ul>
 */
@Component({
  selector: 'rdf-validation-cockpit',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatListModule,
    MatTableModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressBarModule,
    MatSelectModule,
    MatFormFieldModule,
    SuiteEditor
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cockpit">
      <header class="header">
        <div class="title">
          <mat-icon>verified</mat-icon>
          <h2>Validation Cockpit</h2>
        </div>
        <div class="health">
          <span class="label">Project health:</span>
          <mat-chip [class]="healthClass()">{{ healthLabel() }}</mat-chip>
          <span class="sub">{{ health().suiteCount }} suite(s)</span>
        </div>
        <div class="actions">
          <button mat-stroked-button (click)="validateAll()"
                  [disabled]="busy() || suites().length === 0">
            <mat-icon>play_circle</mat-icon>&nbsp;Validate all
          </button>
          <button mat-raised-button color="primary" (click)="startCreate()">
            <mat-icon>add</mat-icon>&nbsp;New suite
          </button>
        </div>
      </header>

      @if (busy()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      }

      <section class="columns">
        <!-- Suite list -->
        <mat-card class="col suites">
          <mat-card-header><mat-card-title>Suites</mat-card-title></mat-card-header>
          <mat-card-content>
            @if (suites().length === 0 && !busy()) {
              <div class="empty">No suites yet. Create one to get started.</div>
            }
            <mat-list>
              @for (s of suites(); track s.id) {
                <mat-list-item (click)="selectSuite(s)"
                               [class.active]="selectedSuite()?.id === s.id">
                  <div class="suite-row">
                    <span class="name">{{ s.name }}</span>
                    <mat-chip class="mini" [class]="statusClassForSuite(s)">
                      {{ statusLabelForSuite(s) }}
                    </mat-chip>
                  </div>
                </mat-list-item>
              }
            </mat-list>
          </mat-card-content>
        </mat-card>

        <!-- Detail -->
        <mat-card class="col detail">
          @if (editing()) {
            <mat-card-header>
              <mat-card-title>{{ selectedSuite() ? 'Edit suite' : 'New suite' }}</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <rdf-suite-editor
                [suite]="selectedSuite()"
                [projectId]="projectId"
                (save)="onSave($event)"
                (cancel)="cancelEdit()">
              </rdf-suite-editor>
            </mat-card-content>
          } @else if (selectedSuite(); as s) {
            <mat-card-header>
              <mat-card-title>{{ s.name }}</mat-card-title>
              <mat-card-subtitle>
                Gate: {{ s.gate }} &middot; {{ s.rules.length }} rule(s)
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="detail-actions">
                <button mat-stroked-button (click)="runSelected()" [disabled]="busy()">
                  <mat-icon>play_arrow</mat-icon>&nbsp;Run now
                </button>
                <button mat-stroked-button (click)="startEdit()">
                  <mat-icon>edit</mat-icon>&nbsp;Edit
                </button>
                <button mat-stroked-button color="warn" (click)="deleteSelected()">
                  <mat-icon>delete</mat-icon>&nbsp;Delete
                </button>
              </div>

              <h4>Rules</h4>
              <table mat-table [dataSource]="s.rules" class="rules-table">
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Name</th>
                  <td mat-cell *matCellDef="let r">{{ r.name }}</td>
                </ng-container>
                <ng-container matColumnDef="type">
                  <th mat-header-cell *matHeaderCellDef>Type</th>
                  <td mat-cell *matCellDef="let r">{{ r.type }}</td>
                </ng-container>
                <ng-container matColumnDef="severity">
                  <th mat-header-cell *matHeaderCellDef>Severity</th>
                  <td mat-cell *matCellDef="let r">{{ r.severity }}</td>
                </ng-container>
                <ng-container matColumnDef="ref">
                  <th mat-header-cell *matHeaderCellDef>Ref</th>
                  <td mat-cell *matCellDef="let r" class="mono">{{ shortRef(r.resourceRef) }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="ruleColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: ruleColumns"></tr>
              </table>

              <mat-divider></mat-divider>

              <h4>Last 20 runs</h4>
              <table mat-table [dataSource]="history()" class="history-table">
                <ng-container matColumnDef="ranAt">
                  <th mat-header-cell *matHeaderCellDef>Time</th>
                  <td mat-cell *matCellDef="let r">{{ r.ranAt | date:'short' }}</td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let r">
                    <mat-chip class="mini" [class]="statusChipClass(r.status)">
                      {{ r.status }}
                    </mat-chip>
                  </td>
                </ng-container>
                <ng-container matColumnDef="counts">
                  <th mat-header-cell *matHeaderCellDef>Issues</th>
                  <td mat-cell *matCellDef="let r">
                    {{ r.errorCount }}E / {{ r.warningCount }}W / {{ r.infoCount }}I
                  </td>
                </ng-container>
                <ng-container matColumnDef="duration">
                  <th mat-header-cell *matHeaderCellDef>Duration</th>
                  <td mat-cell *matCellDef="let r">{{ r.durationMs }} ms</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="runColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: runColumns"
                    (click)="selectRun(row)"
                    [class.active]="selectedRun()?.id === row.id"></tr>
              </table>
            </mat-card-content>
          } @else {
            <mat-card-content>
              <div class="empty">Select a suite on the left, or create a new one.</div>
            </mat-card-content>
          }
        </mat-card>

        <!-- Issues panel -->
        <mat-card class="col issues">
          <mat-card-header>
            <mat-card-title>Issues</mat-card-title>
            <mat-card-subtitle>
              @if (selectedRun()) {
                {{ selectedRun()?.issueCount }} total
              } @else {
                No run selected
              }
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (selectedRun()) {
              <mat-form-field appearance="outline" class="severity-filter">
                <mat-label>Severity</mat-label>
                <mat-select [value]="severityFilter()"
                            (selectionChange)="setSeverityFilter($event.value)">
                  <mat-option [value]="null">All</mat-option>
                  <mat-option value="INFO">Info</mat-option>
                  <mat-option value="WARNING">Warning</mat-option>
                  <mat-option value="ERROR">Error</mat-option>
                  <mat-option value="FATAL">Fatal</mat-option>
                </mat-select>
              </mat-form-field>

              @if (issues().length === 0) {
                <div class="empty">No issues for this filter.</div>
              }
              <mat-list>
                @for (i of issues(); track i.id) {
                  <mat-list-item class="issue-row" (click)="openIssue(i)">
                    <mat-chip class="mini" [class]="'sev-' + i.severity">{{ i.severity }}</mat-chip>
                    <span class="issue-message">{{ i.message }}</span>
                    @if (i.resourceUri) {
                      <span class="issue-uri mono">{{ i.resourceUri }}</span>
                    }
                  </mat-list-item>
                }
              </mat-list>
            } @else {
              <div class="empty">Pick a run to view issues.</div>
            }
          </mat-card-content>
        </mat-card>
      </section>
    </div>
  `,
  styles: [`
    .cockpit { display: flex; flex-direction: column; gap: 12px; padding: 12px; }
    .header { display: flex; align-items: center; gap: 16px; }
    .header .title { display: flex; align-items: center; gap: 8px; flex: 1; }
    .header h2 { margin: 0; font-size: 18px; }
    .health { display: flex; align-items: center; gap: 8px; }
    .health .sub { color: rgba(0,0,0,0.6); font-size: 12px; }
    .actions { display: flex; gap: 8px; }
    .columns { display: grid; grid-template-columns: 280px 1fr 360px; gap: 12px; align-items: start; }
    .col { display: block; }
    .suite-row { display: flex; justify-content: space-between; align-items: center; width: 100%; }
    mat-list-item.active, tr.active { background: rgba(25, 118, 210, 0.08); }
    .mini { font-size: 10px; min-height: 20px; }
    .healthy { background: #c8e6c9; color: #1b5e20; }
    .warning { background: #fff59d; color: #795500; }
    .danger  { background: #ffcdd2; color: #b71c1c; }
    .unknown { background: rgba(0,0,0,0.08); color: rgba(0,0,0,0.6); }
    .sev-INFO    { background: #e3f2fd; color: #0d47a1; }
    .sev-WARNING { background: #fff8e1; color: #e65100; }
    .sev-ERROR   { background: #ffebee; color: #b71c1c; }
    .sev-FATAL   { background: #263238; color: #fff; }
    .detail-actions { display: flex; gap: 8px; margin-bottom: 12px; }
    .rules-table, .history-table { width: 100%; margin-bottom: 16px; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
    .severity-filter { width: 100%; }
    .issue-row { cursor: pointer; }
    .issue-message { flex: 1; margin: 0 8px; }
    .issue-uri { color: rgba(0,0,0,0.6); font-size: 11px; }
    .empty { padding: 16px; color: rgba(0,0,0,0.6); font-style: italic; text-align: center; }
  `]
})
export class Cockpit implements OnInit, OnChanges {
  private readonly svc = inject(ValidationService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  /** Project id the cockpit is scoped to. Required. */
  @Input() projectId: string | null = null;

  readonly suites = signal<ValidationSuite[]>([]);
  readonly selectedSuite = signal<ValidationSuite | null>(null);
  readonly history = signal<ValidationRun[]>([]);
  readonly selectedRun = signal<ValidationRun | null>(null);
  readonly issues = signal<ValidationIssue[]>([]);
  readonly severityFilter = signal<ValidationSeverity | null>(null);
  readonly latestRunBySuiteId = signal<Record<string, ValidationRun | null>>({});

  readonly busy = signal(false);
  readonly editing = signal(false);

  // Computed aggregate health for the header.
  readonly health = computed(() => {
    const map = this.latestRunBySuiteId();
    const suites = this.suites();
    let passed = 0, warning = 0, failed = 0;
    for (const s of suites) {
      const r = map[s.id];
      if (!r) continue;
      if (r.status === 'FAILED' || r.status === 'ERRORED') failed++;
      else if (r.warningCount > 0) warning++;
      else if (r.status === 'PASSED') passed++;
    }
    return {
      suiteCount: suites.length, passedCount: passed,
      warningCount: warning, failedCount: failed
    };
  });

  readonly ruleColumns = ['name', 'type', 'severity', 'ref'];
  readonly runColumns = ['ranAt', 'status', 'counts', 'duration'];

  /** Target graph/triplestore for runs — captured once, pinned to the suite. */
  private runTarget: { graph?: string; triplestoreId?: string } = {};

  ngOnInit(): void { this.reload(); }
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectId'] && !changes['projectId'].firstChange) this.reload();
  }

  reload(): void {
    if (!this.projectId) return;
    this.busy.set(true);
    this.svc.listSuites(this.projectId).pipe(
      catchError(() => of([] as ValidationSuite[])),
      finalize(() => this.busy.set(false))
    ).subscribe(list => {
      this.suites.set(list);
      // Prime the latest-run cache so suite badges render.
      for (const s of list) {
        this.svc.history(s.id, 1).pipe(
          catchError(() => of([] as ValidationRun[]))
        ).subscribe(runs => {
          const map = { ...this.latestRunBySuiteId() };
          map[s.id] = runs[0] ?? null;
          this.latestRunBySuiteId.set(map);
        });
      }
      if (!this.selectedSuite() && list.length > 0) {
        this.selectSuite(list[0]);
      }
    });
  }

  selectSuite(suite: ValidationSuite): void {
    this.selectedSuite.set(suite);
    this.editing.set(false);
    this.selectedRun.set(null);
    this.issues.set([]);
    this.svc.history(suite.id, 20).pipe(
      catchError(() => of([] as ValidationRun[]))
    ).subscribe(h => this.history.set(h));
  }

  selectRun(run: ValidationRun): void {
    this.selectedRun.set(run);
    this.loadIssues();
  }

  setSeverityFilter(value: ValidationSeverity | null): void {
    this.severityFilter.set(value);
    this.loadIssues();
  }

  private loadIssues(): void {
    const run = this.selectedRun();
    if (!run) return;
    this.svc.issues(run.id, this.severityFilter() ?? undefined, 200).pipe(
      catchError(() => of([] as ValidationIssue[]))
    ).subscribe(list => this.issues.set(list));
  }

  runSelected(): void {
    const suite = this.selectedSuite();
    if (!suite || !this.projectId) return;
    this.busy.set(true);
    this.svc.runSuite(suite.id, {
      targetGraph: this.runTarget.graph,
      targetTriplestoreId: this.runTarget.triplestoreId,
      triggeredBy: 'manual'
    }).pipe(
      catchError(err => {
        this.snack.open('Run failed: ' + (err?.message ?? err), 'Dismiss', { duration: 5000 });
        return of(null as ValidationRun | null);
      }),
      finalize(() => this.busy.set(false))
    ).subscribe(r => {
      if (r) {
        this.history.update(h => [r, ...h].slice(0, 20));
        const map = { ...this.latestRunBySuiteId() };
        map[suite.id] = r;
        this.latestRunBySuiteId.set(map);
        this.selectRun(r);
      }
    });
  }

  validateAll(): void {
    if (!this.projectId) return;
    this.busy.set(true);
    this.svc.validateAll(this.projectId, { triggeredBy: 'manual' }).pipe(
      finalize(() => this.busy.set(false)),
      catchError(err => {
        this.snack.open('Validate-all failed: ' + (err?.message ?? err),
          'Dismiss', { duration: 5000 });
        return of([] as ValidationRun[]);
      })
    ).subscribe(runs => {
      if (runs.length === 0) return;
      const map = { ...this.latestRunBySuiteId() };
      for (const r of runs) map[r.suiteId] = r;
      this.latestRunBySuiteId.set(map);
      this.snack.open(`Ran ${runs.length} suite(s)`, 'OK', { duration: 3000 });
    });
  }

  startCreate(): void {
    this.selectedSuite.set(null);
    this.editing.set(true);
  }

  startEdit(): void { this.editing.set(true); }

  cancelEdit(): void { this.editing.set(false); }

  onSave(value: Partial<ValidationSuite>): void {
    if (!this.projectId) return;
    const existing = this.selectedSuite();
    const payload = {
      name: value.name ?? '',
      description: value.description,
      rules: value.rules ?? [],
      gate: (value.gate ?? 'FAIL_ON_ERROR') as ReleaseGate
    };
    const req$ = existing
      ? this.svc.updateSuite(existing.id, payload)
      : this.svc.createSuite({ projectId: this.projectId, ...payload });
    this.busy.set(true);
    req$.pipe(
      finalize(() => this.busy.set(false)),
      catchError(err => {
        this.snack.open('Save failed: ' + (err?.message ?? err),
          'Dismiss', { duration: 5000 });
        return of(null as ValidationSuite | null);
      })
    ).subscribe(saved => {
      if (!saved) return;
      this.editing.set(false);
      this.selectedSuite.set(saved);
      this.reload();
    });
  }

  deleteSelected(): void {
    const suite = this.selectedSuite();
    if (!suite) return;
    if (!confirm(`Delete suite "${suite.name}"?`)) return;
    this.busy.set(true);
    this.svc.deleteSuite(suite.id).pipe(
      finalize(() => this.busy.set(false)),
      catchError(err => {
        this.snack.open('Delete failed: ' + (err?.message ?? err),
          'Dismiss', { duration: 5000 });
        return of(null);
      })
    ).subscribe(() => {
      this.selectedSuite.set(null);
      this.reload();
    });
  }

  openIssue(issue: ValidationIssue): void {
    this.dialog.open(IssueDetailDialog, {
      data: { issue, projectId: this.projectId },
      width: '640px'
    });
  }

  // ----- UI helpers ----------------------------------------------------------

  healthLabel(): string {
    const h = this.health();
    if (h.suiteCount === 0) return 'No suites';
    if (h.failedCount > 0) return `${h.failedCount} failing`;
    if (h.warningCount > 0) return `${h.warningCount} with warnings`;
    if (h.passedCount > 0) return 'All green';
    return 'Unknown';
  }

  healthClass(): string {
    const h = this.health();
    if (h.suiteCount === 0) return 'unknown';
    if (h.failedCount > 0) return 'danger';
    if (h.warningCount > 0) return 'warning';
    if (h.passedCount > 0) return 'healthy';
    return 'unknown';
  }

  statusLabelForSuite(s: ValidationSuite): string {
    const r = this.latestRunBySuiteId()[s.id];
    if (!r) return 'never';
    return r.status.toLowerCase();
  }

  statusClassForSuite(s: ValidationSuite): string {
    const r = this.latestRunBySuiteId()[s.id];
    if (!r) return 'unknown';
    if (r.status === 'FAILED' || r.status === 'ERRORED') return 'danger';
    if (r.warningCount > 0) return 'warning';
    if (r.status === 'PASSED') return 'healthy';
    return 'unknown';
  }

  statusChipClass(status: string): string {
    switch (status) {
      case 'PASSED': return 'healthy';
      case 'FAILED':
      case 'ERRORED': return 'danger';
      default: return 'unknown';
    }
  }

  shortRef(ref: string): string {
    if (!ref) return '';
    if (ref.length <= 48) return ref;
    return ref.slice(0, 22) + '…' + ref.slice(-20);
  }
}
