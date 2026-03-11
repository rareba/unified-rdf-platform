import {
  Component,
  input,
  output,
  signal,
  computed,
  inject,
  ChangeDetectionStrategy,
  OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CubeService } from '../../../../core/services/cube.service';
import { Cube, ColumnMapping } from '../../../../core/models/cube.model';
import { DimensionCard } from './dimension-card';
import { DimensionEditPanel } from './dimension-edit-panel';
import { ObservationPreview } from './observation-preview';

@Component({
  selector: 'app-cube-designer-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DimensionCard,
    DimensionEditPanel,
    ObservationPreview
  ],
  template: `
    <div class="designer-container">

      <!-- Metadata bar -->
      <div class="metadata-bar">
        <div class="metadata-info">
          <span class="cube-name">{{ cube().name }}</span>
          @if (cube().metadata?.['publisher']) {
            <span class="publisher-info">
              <mat-icon class="info-icon">business</mat-icon>
              {{ cube().metadata!['publisher'] }}
            </span>
          }
          @if (cube().graphUri) {
            <span class="graph-uri" [matTooltip]="cube().graphUri!">
              <mat-icon class="info-icon">lan</mat-icon>
              {{ cube().graphUri }}
            </span>
          }
        </div>

        <button
          mat-stroked-button
          disabled
          matTooltip="Metadata editor coming in a future release">
          <mat-icon>edit</mat-icon>
          Edit Metadata
        </button>
      </div>

      <mat-divider></mat-divider>

      <!-- Dimensions row -->
      <div class="dimensions-section">
        <div class="section-header">
          <span class="section-title">Dimensions &amp; Measures</span>
          <span class="dim-count">{{ visibleMappings().length }} columns</span>
        </div>

        @if (visibleMappings().length === 0) {
          <div class="no-mappings">
            <mat-icon>table_chart</mat-icon>
            <span>No column mappings defined yet. Configure them in the CSV Mapping tab.</span>
          </div>
        } @else {
          <div class="dimension-cards-row">
            @for (mapping of visibleMappings(); track mapping.name) {
              <app-dimension-card
                [mapping]="mapping"
                (edit)="startEditing($event)">
              </app-dimension-card>
            }
          </div>
        }
      </div>

      <mat-divider></mat-divider>

      <!-- Observation preview -->
      <div class="observations-section">
        <app-observation-preview [cubeId]="cube().id"></app-observation-preview>
      </div>

    </div>

    <!-- Edit panel overlay -->
    <app-dimension-edit-panel
      [mapping]="editingMapping()"
      (save)="onMappingSave($event)"
      (cancel)="stopEditing()">
    </app-dimension-edit-panel>
  `,
  styles: [`
    .designer-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-y: auto;
    }

    /* Metadata bar */
    .metadata-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 12px 16px;
      flex-wrap: wrap;
    }

    .metadata-info {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
      min-width: 0;
    }

    .cube-name {
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .publisher-info,
    .graph-uri {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.82rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      max-width: 240px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .info-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
      flex-shrink: 0;
    }

    /* Dimensions section */
    .dimensions-section {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .section-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .section-title {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .dim-count {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      background: var(--mat-sys-surface-variant, #f5f5f5);
      padding: 1px 8px;
      border-radius: 10px;
    }

    .no-mappings {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 20px 0;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      font-size: 0.9rem;
    }

    .no-mappings mat-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .dimension-cards-row {
      display: flex;
      flex-direction: row;
      gap: 12px;
      overflow-x: auto;
      padding-bottom: 8px;
    }

    /* Observations section */
    .observations-section {
      padding: 16px;
      flex: 1;
    }
  `]
})
export class CubeDesignerTab implements OnDestroy {
  readonly cube = input.required<Cube>();
  readonly cubeUpdated = output<Cube>();

  private readonly cubeService = inject(CubeService);
  private readonly snackBar    = inject(MatSnackBar);
  private readonly destroy$    = new Subject<void>();

  /** The mapping currently open in the edit panel; null = panel hidden. */
  readonly editingMapping = signal<ColumnMapping | null>(null);

  /** Mappings that are not 'ignore' role — shown as cards. */
  readonly visibleMappings = computed<ColumnMapping[]>(() => {
    const mappings = this.cube().metadata?.columnMappings ?? [];
    return mappings.filter(m => m.role !== 'ignore');
  });

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  startEditing(mapping: ColumnMapping): void {
    this.editingMapping.set(mapping);
  }

  stopEditing(): void {
    this.editingMapping.set(null);
  }

  onMappingSave(updated: ColumnMapping): void {
    const cube = this.cube();
    const existingMappings = cube.metadata?.columnMappings ?? [];
    const newMappings = existingMappings.map(m =>
      m.name === updated.name ? updated : m
    );

    const updatedMetadata = {
      ...cube.metadata,
      columnMappings: newMappings
    };

    this.cubeService
      .update(cube.id, { metadata: updatedMetadata })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: savedCube => {
          this.cubeUpdated.emit(savedCube);
          this.stopEditing();
          this.snackBar.open('Dimension updated', 'Dismiss', { duration: 3000 });
        },
        error: err => {
          console.error('Failed to update dimension', err);
          this.snackBar.open('Failed to save changes', 'Dismiss', { duration: 4000 });
        }
      });
  }
}
