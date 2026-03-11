import { Component, inject, OnInit, OnDestroy, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, Validators, FormBuilder, ReactiveFormsModule, FormGroup } from '@angular/forms';
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
import { MatSortModule, Sort } from '@angular/material/sort';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { Subject, takeUntil, finalize } from 'rxjs';

import { ConfirmationService } from '../../../core/services/confirmation.service';
import { DimensionService } from '../../../core/services';
import { Dimension, DimensionValue, DimensionType } from '../../../core/models';
import { LoggerService } from '../../../core/services/logger.service';

interface ValidationErrors {
  name?: string;
  uri?: string;
  baseUri?: string;
  code?: string;
  label?: string;
}

@Component({
  selector: 'app-dimension-manager',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
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
    MatSortModule,
    DragDropModule
  ],
  providers: [ConfirmationService],
  templateUrl: './dimension-manager.html',
  styleUrl: './dimension-manager.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DimensionManager implements OnInit, OnDestroy {
  private readonly dimensionService = inject(DimensionService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly fb = inject(FormBuilder);
  private readonly logger = inject(LoggerService);
  private destroy$ = new Subject<void>();

  loading = signal(true);
  error = signal<string | null>(null);
  refreshing = signal(false);
  searchQuery = signal('');
  typeFilter = signal<DimensionType | null>(null);
  dimensions = signal<Dimension[]>([]);
  currentSort = signal<Sort>({ active: 'name', direction: 'asc' });

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

  // Validation errors
  validationErrors = signal<ValidationErrors>({});

  // Reactive Forms
  dimensionForm: FormGroup;
  editDimensionForm: FormGroup;
  valueForm: FormGroup;
  editValueForm: FormGroup;

  // Import
  importing = signal(false);
  importCsvData = signal<string>('');
  parsedCsvPreview = signal<Array<{code: string; label: string; description?: string}>>([]);
  hasHeaderRow = signal(true);
  csvDelimiter = signal(',');
  importError = signal<string | null>(null);

  // Drag and drop
  dragEnabled = signal(true);

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
    const query = this.searchQuery().toLowerCase().trim();
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
    return this.sortDimensions(result, this.currentSort());
  });

  filteredValues = computed(() => {
    let result = this.dimensionValues();
    const query = this.valuesSearchQuery().toLowerCase().trim();
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

  constructor() {
    // Initialize reactive forms with validation
    this.dimensionForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      uri: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      type: ['KEY'],
      baseUri: ['', [Validators.pattern(/^https?:\/\/.+\/$/)]],
      description: ['', Validators.maxLength(500)]
    });

    this.editDimensionForm = this.fb.group({
      id: [''],
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      uri: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      type: ['KEY'],
      baseUri: ['', [Validators.pattern(/^https?:\/\/.+\/$/)]],
      description: ['', Validators.maxLength(500)]
    });

    this.valueForm = this.fb.group({
      code: ['', [Validators.required, Validators.maxLength(50)]],
      label: ['', [Validators.required, Validators.maxLength(200)]],
      uri: [''],
      description: ['', Validators.maxLength(500)]
    });

    this.editValueForm = this.fb.group({
      id: [''],
      code: ['', [Validators.required, Validators.maxLength(50)]],
      label: ['', [Validators.required, Validators.maxLength(200)]],
      uri: [''],
      description: ['', Validators.maxLength(500)]
    });
  }

  ngOnInit(): void {
    this.loadDimensions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadDimensions(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dimensionService.list()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (data) => {
          this.dimensions.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.handleError(err, 'Failed to load dimensions');
          this.loading.set(false);
        }
      });
  }

  refreshDimensions(): void {
    this.refreshing.set(true);
    this.dimensionService.list()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.refreshing.set(false))
      )
      .subscribe({
        next: (data) => {
          this.dimensions.set(data);
          this.snackBar.open('Dimensions refreshed', 'Close', { duration: 2000 });
        },
        error: (err) => this.handleError(err, 'Failed to refresh dimensions')
      });
  }

  private sortDimensions(dimensions: Dimension[], sort: Sort): Dimension[] {
    if (!sort.active || sort.direction === '') {
      return dimensions;
    }
    return [...dimensions].sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      switch (sort.active) {
        case 'name': return this.compare(a.name, b.name, isAsc);
        case 'type': return this.compare(a.type, b.type, isAsc);
        case 'valueCount': return this.compare(a.valueCount || 0, b.valueCount || 0, isAsc);
        case 'updatedAt': return this.compare(new Date(a.updatedAt || 0), new Date(b.updatedAt || 0), isAsc);
        default: return 0;
      }
    });
  }

  private compare(a: any, b: any, isAsc: boolean): number {
    return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
  }

  onSortChange(sort: Sort): void {
    this.currentSort.set(sort);
  }

  // Create Dimension
  openCreateDialog(): void {
    this.dimensionForm.reset({
      type: 'KEY'
    });
    this.validationErrors.set({});
    this.createDialogVisible.set(true);
  }

  createDimension(): void {
    if (this.dimensionForm.invalid) {
      this.markFormGroupTouched(this.dimensionForm);
      return;
    }

    const dim = this.dimensionForm.value;
    if (dim.baseUri && !dim.baseUri.endsWith('/')) {
      dim.baseUri += '/';
    }

    this.loading.set(true);
    this.dimensionService.create(dim as Dimension)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: () => {
          this.snackBar.open('Dimension created successfully', 'Close', { duration: 3000 });
          this.createDialogVisible.set(false);
          this.loadDimensions();
        },
        error: (err) => this.handleError(err, 'Failed to create dimension')
      });
  }

  // Edit Dimension
  openEditDialog(dim: Dimension, event: Event): void {
    event.stopPropagation();
    this.editDimensionForm.patchValue({ ...dim });
    this.validationErrors.set({});
    this.editDialogVisible.set(true);
  }

  saveDimension(): void {
    if (this.editDimensionForm.invalid) {
      this.markFormGroupTouched(this.editDimensionForm);
      return;
    }

    const dim = this.editDimensionForm.value;
    if (!dim.id) return;

    this.dimensionService.update(dim.id, dim)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Dimension updated successfully', 'Close', { duration: 3000 });
          this.editDialogVisible.set(false);
          this.loadDimensions();
        },
        error: (err) => this.handleError(err, 'Failed to update dimension')
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
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (confirmed) {
        this.deleteDimension(dim);
      }
    });
  }

  deleteDimension(dim: Dimension): void {
    if (!dim.id) return;
    this.dimensionService.delete(dim.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Dimension deleted successfully', 'Close', { duration: 3000 });
          this.loadDimensions();
        },
        error: (err) => this.handleError(err, 'Failed to delete dimension')
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
    this.dimensionService.getValues(dimensionId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.valuesLoading.set(false))
      )
      .subscribe({
        next: (data) => {
          this.dimensionValues.set(data);
          this.valuesLoading.set(false);
        },
        error: (err) => {
          this.handleError(err, 'Failed to load values');
          this.valuesLoading.set(false);
        }
      });
  }

  // Add Value
  openAddValueDialog(): void {
    const dim = this.selectedDimension();
    this.valueForm.reset({
      uri: dim?.baseUri ? `${dim.baseUri}/` : ''
    });
    this.validationErrors.set({});
    this.addValueDialogVisible.set(true);
  }

  addValue(): void {
    if (this.valueForm.invalid) {
      this.markFormGroupTouched(this.valueForm);
      return;
    }

    const dim = this.selectedDimension();
    if (!dim?.id) return;

    const value = this.valueForm.value;
    const newVal: DimensionValue = {
      dimensionId: dim.id,
      code: value.code,
      label: value.label,
      uri: value.uri || `${dim.baseUri}/${value.code}`,
      description: value.description
    };

    this.dimensionService.addValue(dim.id, newVal)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Value added successfully', 'Close', { duration: 3000 });
          this.addValueDialogVisible.set(false);
          this.loadValues(dim.id!);
          this.loadDimensions();
        },
        error: (err) => this.handleError(err, 'Failed to add value')
      });
  }

  // Edit Value
  openEditValueDialog(value: DimensionValue, event: Event): void {
    event.stopPropagation();
    this.selectedValue.set(value);
    this.editValueForm.patchValue({ ...value });
    this.editValueDialogVisible.set(true);
  }

  saveValue(): void {
    if (this.editValueForm.invalid) {
      this.markFormGroupTouched(this.editValueForm);
      return;
    }

    const value = this.editValueForm.value;
    if (!value.id) return;

    this.dimensionService.updateValue(value.id, value)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Value updated successfully', 'Close', { duration: 3000 });
          this.editValueDialogVisible.set(false);
          const dim = this.selectedDimension();
          if (dim?.id) {
            this.loadValues(dim.id);
          }
        },
        error: (err) => this.handleError(err, 'Failed to update value')
      });
  }

  // Delete Value
  confirmDeleteValue(value: DimensionValue, event: Event): void {
    event.stopPropagation();
    this.confirmationService.confirm({
      title: 'Confirm Delete',
      message: `Are you sure you want to delete "${value.code} - ${value.label}"?`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmColor: 'warn'
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (confirmed) {
        this.deleteValue(value);
      }
    });
  }

  deleteValue(value: DimensionValue): void {
    const dim = this.selectedDimension();
    if (!value.id || !dim?.id) return;

    this.dimensionService.deleteValue(value.id!)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Value deleted successfully', 'Close', { duration: 3000 });
          this.loadValues(dim.id!);
          this.loadDimensions();
        },
        error: (err) => this.handleError(err, 'Failed to delete value')
      });
  }

  // CSV Import
  openImportDialog(): void {
    this.importDialogVisible.set(true);
    this.importCsvData.set('');
    this.parsedCsvPreview.set([]);
    this.hasHeaderRow.set(true);
    this.csvDelimiter.set(',');
    this.importError.set(null);
  }

  parseCsvPreview(): void {
    const data = this.importCsvData().trim();
    if (!data) {
      this.parsedCsvPreview.set([]);
      return;
    }

    const lines = data.split('\n');
    const delimiter = this.csvDelimiter();
    const hasHeader = this.hasHeaderRow();
    const startIndex = hasHeader ? 1 : 0;

    const preview: Array<{code: string; label: string; description?: string}> = [];

    for (let i = startIndex; i < Math.min(lines.length, startIndex + 5); i++) {
      const line = lines[i].trim();
      if (!line) continue;

      const parts = line.split(delimiter).map(p => p.trim());
      preview.push({
        code: parts[0] || '',
        label: parts[1] || '',
        description: parts[2]
      });
    }

    this.parsedCsvPreview.set(preview);
  }

  importFromCsv(): void {
    const dim = this.selectedDimension();
    const data = this.importCsvData().trim();
    if (!dim?.id || !data) return;

    this.importing.set(true);
    this.importError.set(null);

    const lines = data.split('\n');
    const delimiter = this.csvDelimiter();
    const hasHeader = this.hasHeaderRow();
    const startIndex = hasHeader ? 1 : 0;

    const values: DimensionValue[] = [];

    for (let i = startIndex; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      const parts = line.split(delimiter).map(p => p.trim());
      const code = parts[0];
      const label = parts[1];
      const description = parts[2];

      if (!code || !label) continue;

      values.push({
        dimensionId: dim.id,
        code,
        label,
        uri: dim.baseUri ? `${dim.baseUri}/${code}` : code,
        description
      });
    }

    if (values.length === 0) {
      this.importError.set('No valid values found in CSV data');
      this.importing.set(false);
      return;
    }

    let processed = 0;
    const processBatch = () => {
      const batch = values.splice(0, 10);
      if (batch.length === 0) {
        this.importing.set(false);
        this.snackBar.open(`Successfully imported ${processed} values`, 'Close', { duration: 3000 });
        this.importDialogVisible.set(false);
        this.loadValues(dim.id!);
        this.loadDimensions();
        return;
      }

      const observables = batch.map(v =>
        this.dimensionService.addValue(dim.id!, v).pipe(takeUntil(this.destroy$))
      );

      Promise.all(observables.map(obs => obs.toPromise()))
        .then(() => {
          processed += batch.length;
          processBatch();
        })
        .catch(err => {
          this.importError.set(`Import failed after ${processed} values: ${err.message}`);
          this.importing.set(false);
          this.handleError(err, 'Import failed');
        });
    };

    processBatch();
  }

  // Drag and drop reordering
  onValueDrop(event: any): void {
    const dim = this.selectedDimension();
    if (!dim?.id) return;

    const values = this.dimensionValues();
    const previousIndex = values.findIndex(v => v.id === event.previousContainer.data[event.previousIndex].id);
    const currentIndex = values.findIndex(v => v.id === event.container.data[event.currentIndex].id);

    const newValues = [...values];
    const [moved] = newValues.splice(previousIndex, 1);
    newValues.splice(currentIndex, 0, moved);

    this.dimensionValues.set(newValues);

    // Save new order to backend (if API supports it)
    this.snackBar.open('Values reordered', 'Close', { duration: 2000 });
  }

  // Utility
  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      if (control) {
        control.markAsTouched();
        control.markAsDirty();
      }
    });
  }

  getTypeLabel(type: DimensionType): string {
    return this.typeOptions.find(t => t.value === type)?.label || type;
  }

  getTypeColor(type: DimensionType): string {
    const colors: Record<DimensionType, string> = {
      'KEY': 'primary',
      'TEMPORAL': 'accent',
      'GEO': 'primary',
      'MEASURE': 'warn',
      'ATTRIBUTE': 'basic',
      'CODED': 'accent'
    };
    return colors[type] || 'basic';
  }

  getErrorMessage(controlName: string, form: FormGroup): string {
    const control = form.get(controlName);
    if (!control || !control.touched || !control.errors) return '';

    const errors = control.errors;
    if (errors['required']) return 'This field is required';
    if (errors['minlength']) return `Minimum ${errors['minlength'].requiredLength} characters`;
    if (errors['maxlength']) return `Maximum ${errors['maxlength'].requiredLength} characters`;
    if (errors['pattern']) {
      if (controlName === 'uri' || controlName === 'baseUri') return 'Must be a valid HTTP(S) URL';
      return 'Invalid format';
    }
    return 'Invalid value';
  }

  private handleError(err: any, defaultMsg: string): void {
    const message = err?.error?.message || err?.message || defaultMsg;
    this.error.set(message);
    this.snackBar.open(message, 'Close', { duration: 5000 });
    this.logger.error('Dimension manager error:', err);
  }

  formatDate(date: Date | string | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  // Dialog helpers
  closeCreateDialog(): void { this.createDialogVisible.set(false); }
  closeEditDialog(): void { this.editDialogVisible.set(false); }
  closeDetailsDialog(): void { this.detailsDialogVisible.set(false); }
  closeValuesDialog(): void { this.valuesDialogVisible.set(false); }
  closeImportDialog(): void { this.importDialogVisible.set(false); }
  closeAddValueDialog(): void { this.addValueDialogVisible.set(false); }
  closeEditValueDialog(): void { this.editValueDialogVisible.set(false); }
}