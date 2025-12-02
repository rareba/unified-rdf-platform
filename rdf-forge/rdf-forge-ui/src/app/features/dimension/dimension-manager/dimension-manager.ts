import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { DimensionService } from '../../../core/services';
import { Dimension, DimensionValue, DimensionType } from '../../../core/models';

interface ValueForm {
  code: string;
  label: string;
  uri: string;
  description: string;
  parentId: string | null;
}

@Component({
  selector: 'app-dimension-manager',
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatDialogModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatDividerModule,
    MatPaginatorModule,
    MatSortModule
  ],
  providers: [ConfirmationService],
  templateUrl: './dimension-manager.html',
  styleUrl: './dimension-manager.scss',
})
export class DimensionManager implements OnInit {
  private readonly dimensionService = inject(DimensionService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirmationService = inject(ConfirmationService);

  loading = signal(true);
  searchQuery = signal('');
  typeFilter = signal<DimensionType | null>(null);
  dimensions = signal<Dimension[]>([]);

  // Dialogs
  createDialogVisible = signal(false);
  editDialogVisible = signal(false);
  detailsDialogVisible = signal(false);
  valuesDialogVisible = signal(false);
  importDialogVisible = signal(false);
  addValueDialogVisible = signal(false);
  editValueDialogVisible = signal(false);

  // Selected items
  selectedDimension = signal<Dimension | null>(null);
  dimensionValues = signal<DimensionValue[]>([]);
  valuesLoading = signal(false);
  valuesSearchQuery = signal('');
  selectedValue = signal<DimensionValue | null>(null);

  // Forms
  newDimension = signal<Partial<Dimension>>({
    name: '',
    uri: '',
    type: 'KEY',
    description: '',
    baseUri: ''
  });

  editDimension = signal<Partial<Dimension>>({});

  newValue = signal<ValueForm>({
    code: '',
    label: '',
    uri: '',
    description: '',
    parentId: null
  });

  editValue = signal<Partial<DimensionValue>>({});

  // Import
  importing = signal(false);

  typeOptions: { label: string; value: DimensionType }[] = [
    { label: 'Key', value: 'KEY' },
    { label: 'Temporal', value: 'TEMPORAL' },
    { label: 'Geographic', value: 'GEO' },
    { label: 'Measure', value: 'MEASURE' },
    { label: 'Attribute', value: 'ATTRIBUTE' },
    { label: 'Coded', value: 'CODED' }
  ];

  typeFilterOptions = [
    { label: 'All Types', value: null },
    ...this.typeOptions
  ];

  // Computed
  filteredDimensions = computed(() => {
    let result = this.dimensions();
    const query = this.searchQuery().toLowerCase();
    if (query) {
      result = result.filter(d =>
        d.name.toLowerCase().includes(query) ||
        d.uri.toLowerCase().includes(query) ||
        (d.description?.toLowerCase().includes(query) ?? false)
      );
    }
    const type = this.typeFilter();
    if (type) {
      result = result.filter(d => d.type === type);
    }
    return result;
  });

  filteredValues = computed(() => {
    let result = this.dimensionValues();
    const query = this.valuesSearchQuery().toLowerCase();
    if (query) {
      result = result.filter(v =>
        v.code.toLowerCase().includes(query) ||
        v.label.toLowerCase().includes(query) ||
        v.uri.toLowerCase().includes(query)
      );
    }
    return result;
  });

  // Stats
  totalDimensions = computed(() => this.dimensions().length);
  totalValues = computed(() => this.dimensions().reduce((sum, d) => sum + (d.valueCount || 0), 0));
  byType = computed(() => {
    const counts: Record<string, number> = {};
    for (const d of this.dimensions()) {
      counts[d.type] = (counts[d.type] || 0) + 1;
    }
    return counts;
  });

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
        this.snackBar.open('Failed to load dimensions', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  // Create Dimension
  openCreateDialog(): void {
    this.newDimension.set({
      name: '',
      uri: '',
      type: 'KEY',
      description: '',
      baseUri: ''
    });
    this.createDialogVisible.set(true);
  }

  createDimension(): void {
    const dim = this.newDimension();
    if (!dim.name || !dim.uri) {
      this.snackBar.open('Name and URI are required', 'Close', { duration: 3000 });
      return;
    }

    this.dimensionService.create(dim as Dimension).subscribe({
      next: () => {
        this.snackBar.open('Dimension created successfully', 'Close', { duration: 3000 });
        this.createDialogVisible.set(false);
        this.loadDimensions();
      },
      error: () => {
        this.snackBar.open('Failed to create dimension', 'Close', { duration: 3000 });
      }
    });
  }

  // Edit Dimension
  openEditDialog(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.editDimension.set({ ...dim });
    this.editDialogVisible.set(true);
  }

  saveDimension(): void {
    const dim = this.editDimension();
    if (!dim.id) return;

    this.dimensionService.update(dim.id, dim).subscribe({
      next: () => {
        this.snackBar.open('Dimension updated successfully', 'Close', { duration: 3000 });
        this.editDialogVisible.set(false);
        this.loadDimensions();
      },
      error: () => {
        this.snackBar.open('Failed to update dimension', 'Close', { duration: 3000 });
      }
    });
  }

  // Delete Dimension
  confirmDelete(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      title: 'Confirm Delete',
      message: `Are you sure you want to delete "${dim.name}"? This will also delete all ${dim.valueCount || 0} values.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (confirmed) {
        this.deleteDimension(dim);
      }
    });
  }

  deleteDimension(dim: Dimension): void {
    if (!dim.id) return;
    this.dimensionService.delete(dim.id).subscribe({
      next: () => {
        this.snackBar.open('Dimension deleted successfully', 'Close', { duration: 3000 });
        this.loadDimensions();
      },
      error: () => {
        this.snackBar.open('Failed to delete dimension', 'Close', { duration: 3000 });
      }
    });
  }

  // View Details
  viewDetails(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.selectedDimension.set(dim);
    this.detailsDialogVisible.set(true);
  }

  // Values Management
  openValuesDialog(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.selectedDimension.set(dim);
    this.valuesSearchQuery.set('');
    this.valuesDialogVisible.set(true);
    this.loadValues(dim.id!);
  }

  loadValues(dimensionId: string): void {
    this.valuesLoading.set(true);
    this.dimensionService.getValues(dimensionId).subscribe({
      next: (data) => {
        this.dimensionValues.set(data);
        this.valuesLoading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load values', 'Close', { duration: 3000 });
        this.valuesLoading.set(false);
      }
    });
  }

  // Add Value
  openAddValueDialog(): void {
    const dim = this.selectedDimension();
    this.newValue.set({
      code: '',
      label: '',
      uri: dim?.baseUri ? `${dim.baseUri}/` : '',
      description: '',
      parentId: null
    });
    this.addValueDialogVisible.set(true);
  }

  addValue(): void {
    const dim = this.selectedDimension();
    if (!dim?.id) return;

    const value = this.newValue();
    if (!value.code || !value.label) {
      this.snackBar.open('Code and Label are required', 'Close', { duration: 3000 });
      return;
    }

    const newVal: DimensionValue = {
      dimensionId: dim.id,
      code: value.code,
      label: value.label,
      uri: value.uri || `${dim.baseUri}/${value.code}`,
      description: value.description,
      parentId: value.parentId || undefined
    };

    this.dimensionService.addValue(dim.id, newVal).subscribe({
      next: () => {
        this.snackBar.open('Value added successfully', 'Close', { duration: 3000 });
        this.addValueDialogVisible.set(false);
        this.loadValues(dim.id!);
        this.loadDimensions();
      },
      error: () => {
        this.snackBar.open('Failed to add value', 'Close', { duration: 3000 });
      }
    });
  }

  // Edit Value
  openEditValueDialog(value: DimensionValue, event: Event): void {
    event.stopPropagation();
    this.selectedValue.set(value);
    this.editValue.set({ ...value });
    this.editValueDialogVisible.set(true);
  }

  saveValue(): void {
    const value = this.editValue();
    if (!value.id) return;

    this.dimensionService.updateValue(value.id, value).subscribe({
      next: () => {
        this.snackBar.open('Value updated successfully', 'Close', { duration: 3000 });
        this.editValueDialogVisible.set(false);
        const dim = this.selectedDimension();
        if (dim?.id) this.loadValues(dim.id);
      },
      error: () => {
        this.snackBar.open('Failed to update value', 'Close', { duration: 3000 });
      }
    });
  }

  // Delete Value
  confirmDeleteValue(value: DimensionValue, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      title: 'Confirm Delete',
      message: `Are you sure you want to delete "${value.label}"?`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (confirmed) {
        this.deleteValue(value);
      }
    });
  }

  deleteValue(value: DimensionValue): void {
    if (!value.id) return;
    this.dimensionService.deleteValue(value.id).subscribe({
      next: () => {
        this.snackBar.open('Value deleted successfully', 'Close', { duration: 3000 });
        const dim = this.selectedDimension();
        if (dim?.id) {
          this.loadValues(dim.id);
          this.loadDimensions();
        }
      },
      error: () => {
        this.snackBar.open('Failed to delete value', 'Close', { duration: 3000 });
      }
    });
  }

  // Import CSV
  openImportDialog(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.selectedDimension.set(dim);
    this.importDialogVisible.set(true);
  }

  onFileSelect(event: { files: File[] }): void {
    const dim = this.selectedDimension();
    if (!dim?.id || !event.files.length) return;

    this.importing.set(true);
    const file = event.files[0];

    this.dimensionService.importCsv(dim.id, file).subscribe({
      next: (result) => {
        this.snackBar.open(`Successfully imported ${result.imported} values`, 'Close', { duration: 3000 });
        this.importing.set(false);
        this.importDialogVisible.set(false);
        this.loadDimensions();
      },
      error: () => {
        this.snackBar.open('Failed to import CSV', 'Close', { duration: 3000 });
        this.importing.set(false);
      }
    });
  }

  // Export
  exportTurtle(dim: Dimension, event: Event): void {
    event.stopPropagation();
    if (!dim.id) return;

    this.dimensionService.exportTurtle(dim.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${dim.name.replace(/\s+/g, '_')}.ttl`;
        a.click();
        URL.revokeObjectURL(url);
        this.snackBar.open('Dimension exported as Turtle', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Failed to export dimension', 'Close', { duration: 3000 });
      }
    });
  }

  // Helpers
  getTypeColor(type: string): string {
    switch (type) {
      case 'KEY': return 'primary';
      case 'TEMPORAL': return 'accent';
      case 'GEO': return 'warn';
      case 'MEASURE': return 'warn';
      case 'CODED': return 'primary';
      default: return '';
    }
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'KEY': return 'key';
      case 'TEMPORAL': return 'event';
      case 'GEO': return 'place';
      case 'MEASURE': return 'bar_chart';
      case 'CODED': return 'list';
      default: return 'label';
    }
  }

  formatDate(date: string | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  copyToClipboard(text: string, label: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open(`${label} copied to clipboard`, 'Close', { duration: 2000 });
    });
  }

  // Form update helpers
  updateNewDimensionName(value: string): void {
    this.newDimension.update(v => ({ ...v, name: value }));
  }
  updateNewDimensionUri(value: string): void {
    this.newDimension.update(v => ({ ...v, uri: value }));
  }
  updateNewDimensionType(value: DimensionType): void {
    this.newDimension.update(v => ({ ...v, type: value }));
  }
  updateNewDimensionBaseUri(value: string): void {
    this.newDimension.update(v => ({ ...v, baseUri: value }));
  }
  updateNewDimensionDescription(value: string): void {
    this.newDimension.update(v => ({ ...v, description: value }));
  }

  updateEditDimensionName(value: string): void {
    this.editDimension.update(v => ({ ...v, name: value }));
  }
  updateEditDimensionUri(value: string): void {
    this.editDimension.update(v => ({ ...v, uri: value }));
  }
  updateEditDimensionType(value: DimensionType): void {
    this.editDimension.update(v => ({ ...v, type: value }));
  }
  updateEditDimensionBaseUri(value: string): void {
    this.editDimension.update(v => ({ ...v, baseUri: value }));
  }
  updateEditDimensionDescription(value: string): void {
    this.editDimension.update(v => ({ ...v, description: value }));
  }

  updateNewValueCode(value: string): void {
    this.newValue.update(v => ({ ...v, code: value }));
  }
  updateNewValueLabel(value: string): void {
    this.newValue.update(v => ({ ...v, label: value }));
  }
  updateNewValueUri(value: string): void {
    this.newValue.update(v => ({ ...v, uri: value }));
  }
  updateNewValueDescription(value: string): void {
    this.newValue.update(v => ({ ...v, description: value }));
  }

  updateEditValueCode(value: string): void {
    this.editValue.update(v => ({ ...v, code: value }));
  }
  updateEditValueLabel(value: string): void {
    this.editValue.update(v => ({ ...v, label: value }));
  }
  updateEditValueUri(value: string): void {
    this.editValue.update(v => ({ ...v, uri: value }));
  }
  updateEditValueDescription(value: string): void {
    this.editValue.update(v => ({ ...v, description: value }));
  }
}
