import {
  Component,
  inject,
  OnInit,
  OnDestroy,
  signal,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CubeService } from '../../../core/services/cube.service';
import { Cube } from '../../../core/models/cube.model';
import { CsvMappingTab } from './csv-mapping-tab/csv-mapping-tab';
import { TransformTab } from './transform-tab/transform-tab';
import { CubeDesignerTab } from './cube-designer-tab/cube-designer-tab';
import { PublishTab } from './publish-tab/publish-tab';

export type CubeTab = 'mapping' | 'transform' | 'designer' | 'publish';

@Component({
  selector: 'app-cube-project',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    CsvMappingTab,
    TransformTab,
    CubeDesignerTab,
    PublishTab
  ],
  template: `
    <div class="cube-project-container">
      <!-- Top bar -->
      <div class="cube-project-topbar">
        <button mat-icon-button (click)="goBack()" aria-label="Back to cubes">
          <mat-icon>arrow_back</mat-icon>
        </button>

        @if (loading()) {
          <mat-spinner diameter="24" class="topbar-spinner"></mat-spinner>
        } @else if (cube()) {
          <span class="cube-name">{{ cube()!.name }}</span>
          <span class="status-badge status-{{ cube()!.status ?? 'draft' }}">
            {{ getStatusLabel(cube()!.status) }}
          </span>
        } @else {
          <span class="cube-name">New Cube</span>
        }

        <span class="spacer"></span>

        @if (cube()?.pipelineId) {
          <button mat-stroked-button (click)="viewPipeline()">
            <mat-icon>account_tree</mat-icon>
            View Pipeline
          </button>
        }
      </div>

      <!-- Tab shell -->
      @if (!loading()) {
        <mat-tab-group
          [selectedIndex]="tabIndex()"
          (selectedIndexChange)="onTabChange($event)"
          animationDuration="200ms"
          class="cube-tabs">

          <mat-tab label="CSV Mapping">
            <ng-template matTabContent>
              @if (cube()) {
                <app-csv-mapping-tab
                  [cube]="cube()!"
                  (cubeUpdated)="onCubeUpdated($event)">
                </app-csv-mapping-tab>
              }
            </ng-template>
          </mat-tab>

          <mat-tab label="Transform">
            <ng-template matTabContent>
              @if (cube()) {
                <app-transform-tab
                  [cube]="cube()!"
                  (cubeUpdated)="onCubeUpdated($event)">
                </app-transform-tab>
              }
            </ng-template>
          </mat-tab>

          <mat-tab label="Cube Designer">
            <ng-template matTabContent>
              @if (cube()) {
                <app-cube-designer-tab
                  [cube]="cube()!"
                  (cubeUpdated)="onCubeUpdated($event)">
                </app-cube-designer-tab>
              }
            </ng-template>
          </mat-tab>

          <mat-tab label="Publish">
            <ng-template matTabContent>
              @if (cube()) {
                <app-publish-tab
                  [cube]="cube()!"
                  (cubeUpdated)="onCubeUpdated($event)">
                </app-publish-tab>
              }
            </ng-template>
          </mat-tab>

        </mat-tab-group>
      }
    </div>
  `,
  styles: [`
    .cube-project-container {
      display: flex;
      flex-direction: column;
      height: 100%;
    }

    .cube-project-topbar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 16px;
      border-bottom: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
      min-height: 56px;
    }

    .topbar-spinner {
      flex-shrink: 0;
    }

    .cube-name {
      font-size: 1.1rem;
      font-weight: 500;
    }

    .status-badge {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: capitalize;
    }

    .status-draft        { background: #e0e0e0; color: #424242; }
    .status-mapped       { background: #e3f2fd; color: #1565c0; }
    .status-transformed  { background: #e8f5e9; color: #2e7d32; }
    .status-published    { background: #f3e5f5; color: #6a1b9a; }

    .spacer { flex: 1; }

    .cube-tabs {
      flex: 1;
    }

    .tab-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      padding: 64px 16px;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .tab-placeholder mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    .tab-placeholder p {
      margin: 0;
      font-size: 1rem;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CubeProject implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cubeService = inject(CubeService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroy$ = new Subject<void>();

  readonly cube = signal<Cube | null>(null);
  readonly loading = signal(false);
  readonly activeTab = signal<CubeTab>('mapping');

  private readonly TAB_ORDER: CubeTab[] = ['mapping', 'transform', 'designer', 'publish'];

  tabIndex(): number {
    return this.TAB_ORDER.indexOf(this.activeTab());
  }

  onTabChange(index: number): void {
    const tab = this.TAB_ORDER[index];
    if (tab) {
      this.activeTab.set(tab);
    }
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id || id === 'new') {
      this.openNewCubeDialog();
    } else {
      this.loadCube(id);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadCube(id: string): void {
    this.loading.set(true);
    this.cubeService
      .get(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: cube => {
          this.cube.set(cube);
          this.loading.set(false);
        },
        error: err => {
          console.error('Failed to load cube', err);
          this.loading.set(false);
          this.snackBar.open('Failed to load cube', 'Dismiss', { duration: 4000 });
          this.goBack();
        }
      });
  }

  private openNewCubeDialog(): void {
    import('../shared/cube-metadata-dialog').then(m => {
      const ref = this.dialog.open(m.CubeMetadataDialog, {
        width: '480px',
        data: { mode: 'create' }
      });

      ref.afterClosed().pipe(takeUntil(this.destroy$)).subscribe(result => {
        if (!result) {
          this.goBack();
          return;
        }

        this.loading.set(true);
        this.cubeService
          .create(result)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: cube => {
              this.cube.set(cube);
              this.loading.set(false);
              this.router.navigate(['/cubes', cube.id], { replaceUrl: true });
            },
            error: err => {
              console.error('Failed to create cube', err);
              this.loading.set(false);
              this.snackBar.open('Failed to create cube', 'Dismiss', { duration: 4000 });
              this.goBack();
            }
          });
      });
    });
  }

  onCubeUpdated(updated: Cube): void {
    this.cube.set(updated);
  }

  refreshCube(): void {
    const id = this.cube()?.id ?? this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.loadCube(id);
    }
  }

  viewPipeline(): void {
    const pipelineId = this.cube()?.pipelineId;
    if (pipelineId) {
      this.router.navigate(['/pipelines', pipelineId]);
    }
  }

  goBack(): void {
    this.router.navigate(['/cubes']);
  }

  getStatusLabel(status: string | undefined): string {
    if (!status) return 'Draft';
    return status.charAt(0).toUpperCase() + status.slice(1);
  }
}
