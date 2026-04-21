import {
  Component,
  inject,
  OnInit,
  OnDestroy,
  signal,
  computed,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CubeService } from '../../../core/services/cube.service';
import { Cube, CubeStatus } from '../../../core/models/cube.model';

@Component({
  selector: 'app-cube-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './cube-list.html',
  styleUrl: './cube-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CubeList implements OnInit, OnDestroy {
  private readonly cubeService = inject(CubeService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();
  private readonly searchInput$ = new Subject<string>();

  readonly cubes = signal<Cube[]>([]);
  readonly loading = signal(false);
  readonly searchTerm = signal('');

  readonly filteredCubes = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    if (!term) return this.cubes();
    return this.cubes().filter(
      c =>
        c.name.toLowerCase().includes(term) ||
        (c.description ?? '').toLowerCase().includes(term)
    );
  });

  ngOnInit(): void {
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(term => {
        this.searchTerm.set(term);
      });

    this.loadCubes();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchChange(value: string): void {
    this.searchInput$.next(value);
  }

  loadCubes(): void {
    this.loading.set(true);
    this.cubeService
      .list()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: cubes => {
          this.cubes.set(cubes);
          this.loading.set(false);
        },
        error: err => {
          console.error('Failed to load cubes', err);
          this.loading.set(false);
          this.snackBar.open('Failed to load cubes', 'Dismiss', { duration: 4000 });
        }
      });
  }

  openCube(cube: Cube): void {
    this.router.navigate(['/cubes', cube.id]);
  }

  createCube(): void {
    this.router.navigate(['/cubes/new']);
  }

  duplicateCube(cube: Cube): void {
    const copyRequest = {
      uri: `${cube.uri}-copy-${Date.now()}`,
      name: `${cube.name} (copy)`,
      description: cube.description,
      sourceDataId: cube.sourceDataId,
      pipelineId: cube.pipelineId,
      shapeId: cube.shapeId,
      triplestoreId: cube.triplestoreId,
      graphUri: cube.graphUri,
      metadata: cube.metadata
    };

    this.loading.set(true);
    this.cubeService
      .create(copyRequest)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: newCube => {
          this.cubes.update(list => [...list, newCube]);
          this.loading.set(false);
          this.snackBar.open(`"${newCube.name}" created`, 'Open', { duration: 4000 }).onAction().pipe(takeUntil(this.destroy$)).subscribe(() => {
            this.openCube(newCube);
          });
        },
        error: err => {
          console.error('Failed to duplicate cube', err);
          this.loading.set(false);
          this.snackBar.open('Failed to duplicate cube', 'Dismiss', { duration: 4000 });
        }
      });
  }

  deleteCube(cube: Cube): void {
    const confirmed = window.confirm(`Delete "${cube.name}"? This action cannot be undone.`);
    if (!confirmed) return;

    this.loading.set(true);
    this.cubeService
      .delete(cube.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.cubes.update(list => list.filter(c => c.id !== cube.id));
          this.loading.set(false);
          this.snackBar.open(`"${cube.name}" deleted`, 'Dismiss', { duration: 3000 });
        },
        error: err => {
          console.error('Failed to delete cube', err);
          this.loading.set(false);
          this.snackBar.open('Failed to delete cube', 'Dismiss', { duration: 4000 });
        }
      });
  }

  getStatusColor(status: CubeStatus | undefined): string {
    switch (status) {
      case 'draft':        return 'default';
      case 'mapped':       return 'accent';
      case 'transformed':  return 'primary';
      case 'published':    return 'primary';
      default:             return 'default';
    }
  }

  getStatusClass(status: CubeStatus | undefined): string {
    switch (status) {
      case 'draft':        return 'status-draft';
      case 'mapped':       return 'status-mapped';
      case 'transformed':  return 'status-transformed';
      case 'published':    return 'status-published';
      default:             return 'status-draft';
    }
  }

  getStatusLabel(status: CubeStatus | undefined): string {
    if (!status) return 'Draft';
    return status.charAt(0).toUpperCase() + status.slice(1);
  }

  trackByCubeId(_index: number, cube: Cube): string {
    return cube.id;
  }
}
