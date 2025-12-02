import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DimensionService } from '../../../core/services';
import { Dimension, DimensionType } from '../../../core/models';

@Component({
  selector: 'app-dimension-manager',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TagModule,
    DialogModule,
    SelectModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './dimension-manager.html',
  styleUrl: './dimension-manager.scss',
})
export class DimensionManager implements OnInit {
  private readonly dimensionService = inject(DimensionService);
  private readonly messageService = inject(MessageService);

  loading = signal(true);
  searchQuery = signal('');
  dimensions = signal<Dimension[]>([]);
  createDialogVisible = signal(false);

  newDimension = signal<Partial<Dimension>>({
    name: '',
    uri: '',
    type: 'KEY',
    description: ''
  });

  typeOptions: { label: string; value: DimensionType }[] = [
    { label: 'Key', value: 'KEY' },
    { label: 'Temporal', value: 'TEMPORAL' },
    { label: 'Geographic', value: 'GEO' },
    { label: 'Measure', value: 'MEASURE' },
    { label: 'Attribute', value: 'ATTRIBUTE' },
    { label: 'Coded', value: 'CODED' }
  ];

  ngOnInit(): void {
    this.loadDimensions();
  }

  loadDimensions(): void {
    this.loading.set(true);
    this.dimensionService.list().subscribe({
      next: (data) => {
        this.dimensions.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load dimensions' });
        this.loading.set(false);
      }
    });
  }

  createDimension(): void {
    const dim = this.newDimension();
    this.dimensionService.create(dim as Dimension).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Created', detail: 'Dimension created' });
        this.createDialogVisible.set(false);
        this.newDimension.set({ name: '', uri: '', type: 'KEY', description: '' });
        this.loadDimensions();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create dimension' });
      }
    });
  }

  deleteDimension(dim: Dimension, event: Event): void {
    event.stopPropagation();
    if (!dim.id) return;
    this.dimensionService.delete(dim.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: 'Dimension deleted' });
        this.loadDimensions();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete dimension' });
      }
    });
  }

  getTypeSeverity(type: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (type) {
      case 'KEY': return 'info';
      case 'TEMPORAL': return 'success';
      case 'GEO': return 'warn';
      case 'MEASURE': return 'danger';
      default: return 'secondary';
    }
  }
}
