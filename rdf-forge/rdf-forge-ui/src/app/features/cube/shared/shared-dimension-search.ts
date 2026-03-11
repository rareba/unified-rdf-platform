import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { DimensionService } from '../../../core/services/dimension.service';
import { Dimension, DimensionType } from '../../../core/models/dimension.model';

export interface SharedDimensionSearchData {
  typeFilter?: DimensionType;
}

@Component({
  selector: 'app-shared-dimension-search',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Link to Shared Dimension</h2>

    <mat-dialog-content>
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search dimensions</mat-label>
        <input
          matInput
          placeholder="Type to search..."
          [ngModel]="searchTerm()"
          (ngModelChange)="onSearchChange($event)"
        />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      @if (loading()) {
        <div class="loading-container">
          <mat-spinner diameter="32"></mat-spinner>
        </div>
      }

      @if (!loading() && results().length === 0 && searchTerm()) {
        <p class="no-results">No shared dimensions found.</p>
      }

      <mat-list>
        @for (dim of results(); track dim.uri) {
          <mat-list-item class="dimension-item">
            <div class="dimension-info">
              <div class="dimension-header">
                <span class="dimension-name">{{ dim.name }}</span>
                <mat-chip-set>
                  <mat-chip [highlighted]="true" class="type-chip">
                    {{ dim.type }}
                  </mat-chip>
                </mat-chip-set>
              </div>
              @if (dim.description) {
                <p class="dimension-description">{{ dim.description }}</p>
              }
              @if (dim.valueCount != null) {
                <span class="dimension-values">{{ dim.valueCount }} values</span>
              }
            </div>
            <button mat-stroked-button color="primary" (click)="select(dim)">
              Link
            </button>
          </mat-list-item>
        }
      </mat-list>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .search-field {
      width: 100%;
    }

    .loading-container {
      display: flex;
      justify-content: center;
      padding: 24px 0;
    }

    .no-results {
      text-align: center;
      color: rgba(0, 0, 0, 0.54);
      padding: 16px 0;
    }

    .dimension-item {
      height: auto !important;
      padding: 12px 0;
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .dimension-info {
      flex: 1;
      min-width: 0;
    }

    .dimension-header {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .dimension-name {
      font-weight: 500;
    }

    .type-chip {
      font-size: 11px;
    }

    .dimension-description {
      margin: 4px 0 0;
      font-size: 13px;
      color: rgba(0, 0, 0, 0.6);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .dimension-values {
      font-size: 12px;
      color: rgba(0, 0, 0, 0.45);
    }
  `],
})
export class SharedDimensionSearch implements OnInit, OnDestroy {
  private readonly dialogRef = inject(MatDialogRef<SharedDimensionSearch>);
  private readonly data: SharedDimensionSearchData | null = inject(MAT_DIALOG_DATA, { optional: true });
  private readonly dimensionService = inject(DimensionService);

  readonly searchTerm = signal('');
  readonly results = signal<Dimension[]>([]);
  readonly loading = signal(false);

  private readonly search$ = new Subject<string>();
  private subscription?: Subscription;

  ngOnInit(): void {
    this.subscription = this.search$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(term => {
          this.loading.set(true);
          return this.dimensionService.list({
            search: term || undefined,
            type: this.data?.typeFilter,
          });
        }),
      )
      .subscribe({
        next: dimensions => {
          const shared = dimensions.filter(d => d.isShared);
          this.results.set(shared);
          this.loading.set(false);
        },
        error: () => {
          this.results.set([]);
          this.loading.set(false);
        },
      });

    // Initial load
    this.search$.next('');
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.search$.next(value);
  }

  select(dimension: Dimension): void {
    this.dialogRef.close(dimension.uri);
  }
}
