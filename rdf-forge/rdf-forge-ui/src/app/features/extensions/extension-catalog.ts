import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import {
  ExtensionDescriptor,
  ExtensionKind,
  EXTENSION_KINDS,
  EXTENSION_KIND_LABELS
} from '../../core/models';
import { ExtensionService } from '../../core/services/extension.service';
import { ExtensionDetail } from './extension-detail';

interface TabState {
  kind: ExtensionKind;
  label: string;
  items: ExtensionDescriptor[];
}

/**
 * Top-level catalog page at {@code /extensions}.
 *
 * <p>Loads every registered extension from the aggregated meta-endpoint and
 * groups them into tabs by kind. Each tab shows a table with name, id,
 * version, providedBy, and capability chips. Clicking a row opens
 * {@link ExtensionDetail} with full parameter / config info.
 */
@Component({
  selector: 'app-extension-catalog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTabsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatDialogModule
  ],
  template: `
    <section class="catalog">
      <header class="hdr">
        <div class="title">
          <mat-icon>extension</mat-icon>
          <div>
            <h1>Extension Catalog</h1>
            <p class="muted">
              Every plugin registered in the running services — operations,
              formats, storage, destinations, triplestores, validators, and more.
            </p>
          </div>
        </div>
        <div class="actions">
          <mat-form-field appearance="outline" class="search">
            <mat-label>Filter</mat-label>
            <input matInput [ngModel]="filter()" (ngModelChange)="filter.set($event)" />
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-stroked-button (click)="reload()" [disabled]="loading()">
            <mat-icon>refresh</mat-icon>
            Reload
          </button>
        </div>
      </header>

      @if (loading()) {
        <div class="center">
          <mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner>
        </div>
      } @else if (error()) {
        <mat-card class="err">
          <mat-icon color="warn">error</mat-icon>
          <div>
            <strong>Failed to load catalog</strong>
            <p class="muted">{{ error() }}</p>
          </div>
          <button mat-stroked-button (click)="reload()">Retry</button>
        </mat-card>
      } @else {
        <mat-tab-group animationDuration="0ms">
          @for (t of tabs(); track t.kind) {
            <mat-tab [label]="t.label + ' (' + countFor(t.kind) + ')'">
              @if (filteredFor(t.kind).length === 0) {
                <p class="muted empty">Nothing registered here yet.</p>
              } @else {
                <table mat-table [dataSource]="filteredFor(t.kind)" class="ext-table">
                  <ng-container matColumnDef="name">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let e">
                      <strong>{{ e.name }}</strong>
                      <div class="muted"><code>{{ e.id }}</code></div>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="version">
                    <th mat-header-cell *matHeaderCellDef>Version</th>
                    <td mat-cell *matCellDef="let e">{{ e.version }}</td>
                  </ng-container>
                  <ng-container matColumnDef="providedBy">
                    <th mat-header-cell *matHeaderCellDef>Provided by</th>
                    <td mat-cell *matCellDef="let e"><code>{{ e.providedBy }}</code></td>
                  </ng-container>
                  <ng-container matColumnDef="capabilities">
                    <th mat-header-cell *matHeaderCellDef>Capabilities</th>
                    <td mat-cell *matCellDef="let e">
                      @for (c of e.capabilities?.slice(0, 4); track c) {
                        <mat-chip class="cap-chip">{{ c }}</mat-chip>
                      }
                      @if ((e.capabilities?.length ?? 0) > 4) {
                        <span class="muted">+{{ e.capabilities.length - 4 }}</span>
                      }
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="available">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let e">
                      @if (!e.available) {
                        <mat-chip class="unavailable">coming soon</mat-chip>
                      }
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: displayedColumns"
                      (click)="openDetail(row)" class="clickable"></tr>
                </table>
              }
            </mat-tab>
          }
        </mat-tab-group>
      }
    </section>
  `,
  styles: [`
    .catalog { padding: 1rem 1.5rem; }
    .hdr { display: flex; justify-content: space-between; align-items: flex-start;
           gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .title { display: flex; gap: .75rem; align-items: center; }
    .title h1 { margin: 0; font-size: 1.5rem; }
    .muted { color: #666; margin: 0; }
    .actions { display: flex; gap: .5rem; align-items: center; }
    .search { width: 280px; }
    .center { display: flex; justify-content: center; padding: 2rem; }
    .err { display: flex; gap: 1rem; align-items: center; padding: 1rem; }
    .ext-table { width: 100%; margin-top: .5rem; }
    .clickable { cursor: pointer; }
    .clickable:hover { background: #fafafa; }
    .cap-chip { font-size: .7rem; margin-right: .25rem; }
    .empty { padding: 1rem; }
    mat-chip.unavailable { background-color: #fff9c4; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExtensionCatalog implements OnInit {
  private readonly extensionService = inject(ExtensionService);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly all = signal<ExtensionDescriptor[]>([]);
  readonly filter = signal<string>('');

  readonly tabs = computed<TabState[]>(() =>
    EXTENSION_KINDS.map(k => ({
      kind: k,
      label: EXTENSION_KIND_LABELS[k],
      items: this.all().filter(e => e.kind === k)
    }))
  );

  readonly displayedColumns = ['name', 'version', 'providedBy', 'capabilities', 'available'];

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.extensionService.listAll().subscribe({
      next: list => {
        this.all.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.message ?? 'Unknown error');
        this.loading.set(false);
      }
    });
  }

  countFor(kind: ExtensionKind): number {
    return this.all().filter(e => e.kind === kind).length;
  }

  filteredFor(kind: ExtensionKind): ExtensionDescriptor[] {
    const needle = this.filter().trim().toLowerCase();
    const pool = this.all().filter(e => e.kind === kind);
    if (!needle) return pool;
    return pool.filter(e =>
      e.name.toLowerCase().includes(needle) ||
      e.id.toLowerCase().includes(needle) ||
      (e.description ?? '').toLowerCase().includes(needle) ||
      (e.capabilities ?? []).some(c => c.toLowerCase().includes(needle))
    );
  }

  openDetail(e: ExtensionDescriptor): void {
    this.dialog.open(ExtensionDetail, { data: e, autoFocus: false });
  }
}
