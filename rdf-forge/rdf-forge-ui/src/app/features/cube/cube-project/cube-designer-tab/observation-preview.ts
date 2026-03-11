import {
  Component,
  input,
  OnInit,
  OnDestroy,
  signal,
  computed,
  inject,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { CubeService } from '../../../../core/services/cube.service';
import { ObservationColumn } from '../../../../core/models/cube.model';

@Component({
  selector: 'app-observation-preview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  template: `
    <div class="preview-container">

      <!-- Header row -->
      <div class="preview-header">
        <span class="preview-title">Observations</span>
        @if (!loading() && totalCount() > 0) {
          <span class="count-badge">{{ totalCount() | number }} total</span>
        }
      </div>

      <!-- Loading spinner -->
      @if (loading()) {
        <div class="loading-overlay">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      }

      <!-- Empty state -->
      @if (!loading() && observations().length === 0) {
        <div class="empty-state">
          <mat-icon>table_rows</mat-icon>
          <p>No observations yet. Run a transformation first.</p>
        </div>
      }

      <!-- Table -->
      @if (!loading() && observations().length > 0) {
        <div class="table-scroll-wrapper">
          <table mat-table [dataSource]="observations()" class="obs-table">

            @for (col of columns(); track col.name) {
              <ng-container [matColumnDef]="col.name">
                <th mat-header-cell *matHeaderCellDef
                    [class.key-dim-header]="col.role === 'dimension'"
                    [class.measure-header]="col.role === 'measure'">
                  {{ col.name }}
                </th>
                <td mat-cell *matCellDef="let row"
                    [class.key-dim-cell]="col.role === 'dimension'"
                    [class.measure-cell]="col.role === 'measure'">
                  {{ getCellValue(row, col.name) }}
                </td>
              </ng-container>
            }

            <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns();"></tr>
          </table>
        </div>

        <mat-paginator
          [length]="totalCount()"
          [pageSize]="pageSize()"
          [pageIndex]="pageIndex()"
          [pageSizeOptions]="pageSizeOptions"
          (page)="onPageChange($event)"
          aria-label="Observations pagination">
        </mat-paginator>
      }

    </div>
  `,
  styles: [`
    .preview-container {
      display: flex;
      flex-direction: column;
      min-height: 200px;
      position: relative;
    }

    .preview-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0 12px;
    }

    .preview-title {
      font-size: 1rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .count-badge {
      background: var(--mat-sys-surface-variant, #f5f5f5);
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 500;
    }

    .loading-overlay {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 48px 0;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 48px 16px;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    .empty-state p {
      margin: 0;
      font-size: 0.95rem;
    }

    .table-scroll-wrapper {
      overflow-x: auto;
    }

    .obs-table {
      width: 100%;
      min-width: max-content;
    }

    .key-dim-header,
    .key-dim-cell {
      font-weight: 600;
    }

    .measure-header,
    .measure-cell {
      text-align: right;
    }

    th.measure-header {
      text-align: right;
    }
  `]
})
export class ObservationPreview implements OnInit, OnDestroy {
  readonly cubeId = input.required<string>();

  private readonly cubeService = inject(CubeService);
  private readonly destroy$ = new Subject<void>();

  readonly observations = signal<Record<string, unknown>[]>([]);
  readonly columns      = signal<ObservationColumn[]>([]);
  readonly totalCount   = signal(0);
  readonly loading      = signal(false);
  readonly pageIndex    = signal(0);
  readonly pageSize     = signal(10);

  readonly displayedColumns = computed(() => this.columns().map(c => c.name));

  readonly pageSizeOptions = [10, 20, 50, 100];

  ngOnInit(): void {
    this.loadObservations();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadObservations();
  }

  getCellValue(row: Record<string, unknown>, colName: string): string {
    const val = row[colName];
    if (val === null || val === undefined) return '';
    return String(val);
  }

  private loadObservations(): void {
    this.loading.set(true);
    this.cubeService
      .getObservations(this.cubeId(), this.pageIndex(), this.pageSize())
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: page => {
          this.observations.set(page.items);
          this.columns.set(page.columns);
          this.totalCount.set(page.totalCount);
          this.loading.set(false);
        },
        error: err => {
          console.error('Failed to load observations', err);
          this.observations.set([]);
          this.loading.set(false);
        }
      });
  }
}
