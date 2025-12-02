import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ShaclService } from '../../../core/services';
import { Shape } from '../../../core/models';

@Component({
  selector: 'app-shacl-studio',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TagModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './shacl-studio.html',
  styleUrl: './shacl-studio.scss',
})
export class ShaclStudio implements OnInit {
  private readonly router = inject(Router);
  private readonly shaclService = inject(ShaclService);
  private readonly messageService = inject(MessageService);

  loading = signal(true);
  searchQuery = signal('');
  shapes = signal<Shape[]>([]);

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
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load shapes' });
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
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: 'Shape deleted' });
        this.loadShapes();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete shape' });
      }
    });
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
}
