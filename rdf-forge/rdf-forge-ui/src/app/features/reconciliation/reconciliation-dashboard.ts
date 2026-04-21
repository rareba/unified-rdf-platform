import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ReconciliationService } from '../../core/services/reconciliation.service';
import {
  CandidateListFilter,
  MatchCandidate,
  MatchPredicate,
  MatchStatus,
  MatchStats,
  MatcherInfo
} from '../../core/models/reconciliation.model';
import { CandidateDetailDialog } from './candidate-detail-dialog';
import { ManualCandidateDialog } from './manual-candidate-dialog';

@Component({
  selector: 'rdf-reconciliation-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule
  ],
  template: `
    <div class="dash">
      <header class="dash-head">
        <h2>Reconciliation</h2>
        <div class="actions">
          <button mat-stroked-button (click)="openSuggest()">
            <mat-icon>manage_search</mat-icon> Run Suggestions
          </button>
          <button mat-stroked-button (click)="openManual()">
            <mat-icon>add_link</mat-icon> Manual Entry
          </button>
          <button mat-stroked-button (click)="refresh()">
            <mat-icon>refresh</mat-icon> Refresh
          </button>
        </div>
      </header>

      <section class="stats">
        <mat-card class="stat-card pending">
          <div class="stat-label">Pending</div>
          <div class="stat-value">{{ stats()?.pending ?? 0 }}</div>
        </mat-card>
        <mat-card class="stat-card approved">
          <div class="stat-label">Approved</div>
          <div class="stat-value">{{ stats()?.approved ?? 0 }}</div>
        </mat-card>
        <mat-card class="stat-card rejected">
          <div class="stat-label">Rejected</div>
          <div class="stat-value">{{ stats()?.rejected ?? 0 }}</div>
        </mat-card>
        <mat-card class="stat-card archived">
          <div class="stat-label">Archived</div>
          <div class="stat-value">{{ stats()?.archived ?? 0 }}</div>
        </mat-card>
      </section>

      <section class="filters">
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(value)]="filterStatus" (selectionChange)="applyFilter()">
            <mat-option [value]="">All</mat-option>
            <mat-option value="PENDING">Pending</mat-option>
            <mat-option value="APPROVED">Approved</mat-option>
            <mat-option value="REJECTED">Rejected</mat-option>
            <mat-option value="ARCHIVED">Archived</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Predicate</mat-label>
          <mat-select [(value)]="filterPredicate" (selectionChange)="applyFilter()">
            <mat-option [value]="">All</mat-option>
            <mat-option value="SAME_AS">sameAs</mat-option>
            <mat-option value="EXACT_MATCH">exactMatch</mat-option>
            <mat-option value="CLOSE_MATCH">closeMatch</mat-option>
            <mat-option value="RELATED_MATCH">relatedMatch</mat-option>
            <mat-option value="BROADER">broader</mat-option>
            <mat-option value="NARROWER">narrower</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Matcher</mat-label>
          <mat-select [(value)]="filterMatcher" (selectionChange)="applyFilter()">
            <mat-option [value]="">All</mat-option>
            @for (m of matchers(); track m.id) {
              <mat-option [value]="m.id">{{ m.displayName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="search">
          <mat-label>Search URI</mat-label>
          <input matInput [(ngModel)]="filterSearch" (change)="applyFilter()" />
        </mat-form-field>
      </section>

      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (candidates().length === 0) {
        <div class="empty">No candidates match the current filters.</div>
      } @else {
        <table mat-table [dataSource]="candidates()" class="candidates">
          <ng-container matColumnDef="source">
            <th mat-header-cell *matHeaderCellDef>Source</th>
            <td mat-cell *matCellDef="let c" class="uri">{{ c.sourceUri }}</td>
          </ng-container>
          <ng-container matColumnDef="predicate">
            <th mat-header-cell *matHeaderCellDef>Predicate</th>
            <td mat-cell *matCellDef="let c"><span class="pred">{{ predicateShort(c.predicate) }}</span></td>
          </ng-container>
          <ng-container matColumnDef="target">
            <th mat-header-cell *matHeaderCellDef>Target</th>
            <td mat-cell *matCellDef="let c" class="uri">{{ c.targetUri }}</td>
          </ng-container>
          <ng-container matColumnDef="confidence">
            <th mat-header-cell *matHeaderCellDef>Confidence</th>
            <td mat-cell *matCellDef="let c">{{ (c.confidence * 100) | number:'1.0-0' }}%</td>
          </ng-container>
          <ng-container matColumnDef="matcher">
            <th mat-header-cell *matHeaderCellDef>Matcher</th>
            <td mat-cell *matCellDef="let c">{{ c.matcherName }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let c">
              <span class="status" [class]="'status-' + c.status.toLowerCase()">{{ c.status }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let c" class="actions-cell">
              <button mat-icon-button color="primary" matTooltip="View evidence" (click)="openDetail(c)">
                <mat-icon>info</mat-icon>
              </button>
              @if (c.status === 'PENDING') {
                <button mat-icon-button color="primary" matTooltip="Approve" (click)="approve(c)">
                  <mat-icon>check_circle</mat-icon>
                </button>
                <button mat-icon-button color="warn" matTooltip="Reject" (click)="reject(c)">
                  <mat-icon>cancel</mat-icon>
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        </table>
      }
    </div>
  `,
  styles: [`
    .dash { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
    .dash-head { display: flex; justify-content: space-between; align-items: center; }
    .dash-head h2 { margin: 0; }
    .actions { display: flex; gap: 8px; }
    .stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
    .stat-card { padding: 14px; }
    .stat-label { font-size: 12px; color: rgba(0,0,0,.6); text-transform: uppercase; }
    .stat-value { font-size: 28px; font-weight: 500; }
    .stat-card.pending  .stat-value { color: #f57c00; }
    .stat-card.approved .stat-value { color: #388e3c; }
    .stat-card.rejected .stat-value { color: #c62828; }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; }
    .filters .search { flex: 1 1 200px; }
    table.candidates { width: 100%; }
    .uri { font-family: ui-monospace, monospace; font-size: 11px; word-break: break-all; max-width: 280px; }
    .pred { background: #e3f2fd; color: #1565c0; padding: 2px 6px; border-radius: 10px; font-size: 11px; }
    .status { padding: 2px 6px; border-radius: 10px; font-size: 11px; font-weight: 500; }
    .status-pending  { background: #fff3e0; color: #e65100; }
    .status-approved { background: #e8f5e9; color: #2e7d32; }
    .status-rejected { background: #ffebee; color: #c62828; }
    .status-archived { background: #eceff1; color: #546e7a; }
    .actions-cell { white-space: nowrap; }
    .loading, .empty { padding: 32px; text-align: center; color: rgba(0,0,0,.55); }
  `]
})
export class ReconciliationDashboard implements OnInit {
  private readonly service = inject(ReconciliationService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly displayedColumns = ['source', 'predicate', 'target', 'confidence', 'matcher', 'status', 'actions'];

  readonly candidates = signal<MatchCandidate[]>([]);
  readonly matchers = signal<MatcherInfo[]>([]);
  readonly stats = signal<MatchStats | null>(null);
  readonly loading = signal(false);

  filterStatus: MatchStatus | '' = 'PENDING';
  filterPredicate: MatchPredicate | '' = '';
  filterMatcher = '';
  filterSearch = '';

  readonly projectId = computed<string | null>(() => {
    // Routed as child tab under /projects/:id/reconciliation; also supports
    // direct navigation with ?projectId=... for standalone / testing.
    return this.route.snapshot.paramMap.get('projectId')
        ?? this.route.snapshot.parent?.paramMap.get('projectId')
        ?? this.route.snapshot.parent?.paramMap.get('id')
        ?? this.route.snapshot.queryParamMap.get('projectId');
  });

  constructor() {
    // React to projectId changes.
    effect(() => {
      const pid = this.projectId();
      if (pid) this.refresh();
    });
  }

  ngOnInit(): void {
    this.service.matchers().subscribe({
      next: list => this.matchers.set(list),
      error: () => this.matchers.set([])
    });
  }

  refresh(): void {
    const pid = this.projectId();
    if (!pid) return;
    this.loading.set(true);
    this.loadCandidates(pid);
    this.service.stats(pid).subscribe({
      next: s => this.stats.set(s),
      error: () => this.stats.set(null)
    });
  }

  applyFilter(): void {
    this.loadCandidates(this.projectId() ?? '');
  }

  private loadCandidates(pid: string): void {
    if (!pid) return;
    const filter: CandidateListFilter = {
      status: (this.filterStatus || undefined) as MatchStatus | undefined,
      predicate: (this.filterPredicate || undefined) as MatchPredicate | undefined,
      matcher: this.filterMatcher || undefined,
      search: this.filterSearch || undefined
    };
    this.service.list(pid, filter).subscribe({
      next: list => {
        this.candidates.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.candidates.set([]);
        this.loading.set(false);
      }
    });
  }

  approve(c: MatchCandidate): void {
    this.service.approve(c.id).subscribe({
      next: () => {
        this.snackBar.open('Approved', 'OK', { duration: 2000 });
        this.refresh();
      },
      error: (err) => this.snackBar.open('Approve failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 })
    });
  }

  reject(c: MatchCandidate): void {
    this.service.reject(c.id).subscribe({
      next: () => {
        this.snackBar.open('Rejected', 'OK', { duration: 2000 });
        this.refresh();
      },
      error: (err) => this.snackBar.open('Reject failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 })
    });
  }

  openDetail(c: MatchCandidate): void {
    this.dialog.open(CandidateDetailDialog, { data: c, width: '560px' });
  }

  openManual(): void {
    const pid = this.projectId();
    if (!pid) return;
    const ref = this.dialog.open(ManualCandidateDialog, {
      data: { projectId: pid },
      width: '560px'
    });
    ref.afterClosed().subscribe((res) => {
      if (res) this.refresh();
    });
  }

  openSuggest(): void {
    const pid = this.projectId();
    if (!pid) return;
    const sourceUri = window.prompt('Enter the source URI to reconcile:');
    if (!sourceUri) return;
    const label = window.prompt('Optional label (for local duplicate match):') ?? undefined;
    this.service.suggest({ projectId: pid, sourceUri, label }).subscribe({
      next: (resp) => {
        this.snackBar.open(
          `Found ${resp.persisted} new candidate(s), ${resp.duplicatesSkipped} duplicate(s) skipped`,
          'OK', { duration: 3500 });
        this.refresh();
      },
      error: (err) => this.snackBar.open('Suggest failed: ' + (err?.error?.detail ?? err?.message), 'OK', { duration: 4000 })
    });
  }

  predicateShort(p: MatchPredicate): string {
    return p.toLowerCase().replace(/_/g, ' ');
  }
}
