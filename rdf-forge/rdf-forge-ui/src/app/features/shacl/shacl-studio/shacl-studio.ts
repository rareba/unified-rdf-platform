import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ShaclService } from '../../../core/services';
import { Shape } from '../../../core/models';

@Component({
  selector: 'app-shacl-studio',
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatPaginatorModule
  ],
  templateUrl: './shacl-studio.html',
  styleUrl: './shacl-studio.scss',
})
export class ShaclStudio implements OnInit {
  private readonly router = inject(Router);
  private readonly shaclService = inject(ShaclService);
  private readonly snackBar = inject(MatSnackBar);

  loading = signal(true);
  searchQuery = signal('');
  shapes = signal<Shape[]>([]);
  displayedColumns: string[] = ['name', 'targetClass', 'format', 'updated', 'actions'];

  pageSize = signal(10);
  pageIndex = signal(0);

  filteredShapes = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) return this.shapes();
    return this.shapes().filter(shape =>
      shape.name.toLowerCase().includes(query) ||
      shape.uri.toLowerCase().includes(query) ||
      (shape.targetClass && shape.targetClass.toLowerCase().includes(query))
    );
  });

  paginatedShapes = computed(() => {
    const filtered = this.filteredShapes();
    const start = this.pageIndex() * this.pageSize();
    const end = start + this.pageSize();
    return filtered.slice(start, end);
  });

  ngOnInit(): void {
    this.loadShapes();
  }

  loadShapes(): void {
    this.loading.set(true);
    this.shaclService.list().subscribe({
      next: (data) => {
        this.shapes.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load shapes', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  createShape(): void {
    this.router.navigate(['/shacl/new']);
  }

  editShape(shape: Shape): void {
    this.router.navigate(['/shacl', shape.id]);
  }

  deleteShape(shape: Shape, event: Event): void {
    event.stopPropagation();
    this.shaclService.delete(shape.id).subscribe({
      next: () => {
        this.snackBar.open('Shape deleted', 'Close', { duration: 3000 });
        this.loadShapes();
      },
      error: () => {
        this.snackBar.open('Failed to delete shape', 'Close', { duration: 3000 });
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.pageIndex.set(event.pageIndex);
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
}
