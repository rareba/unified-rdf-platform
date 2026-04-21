import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DimensionManager } from './dimension-manager';
import { DimensionService } from '../../../core/services/dimension.service';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { LoggerService } from '../../../core/services/logger.service';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { Dimension, DimensionType, DimensionValue } from '../../../core/models/dimension.model';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

class MockDimensionService {
  list = jasmine.createSpy('list').and.returnValue(of([]));
  create = jasmine.createSpy('create').and.returnValue(of({ id: 'dim-1', name: 'Test Dimension' }));
  update = jasmine.createSpy('update').and.returnValue(of({ id: 'dim-1', name: 'Updated' }));
  delete = jasmine.createSpy('delete').and.returnValue(of(void 0));
  get = jasmine.createSpy('get').and.returnValue(of({ id: 'dim-1', name: 'Test' }));
  getValues = jasmine.createSpy('getValues').and.returnValue(of([]));
  addValue = jasmine.createSpy('addValue').and.returnValue(of({ id: 'val-1', code: 'TEST', label: 'Test' }));
  updateValue = jasmine.createSpy('updateValue').and.returnValue(of({ id: 'val-1', code: 'UPD', label: 'Updated' }));
  deleteValue = jasmine.createSpy('deleteValue').and.returnValue(of(void 0));
}

class MockConfirmationService {
  confirm = jasmine.createSpy('confirm').and.returnValue(of(true));
}

class MockLoggerService {
  debug = jasmine.createSpy('debug');
  info = jasmine.createSpy('info');
  warn = jasmine.createSpy('warn');
  error = jasmine.createSpy('error');
}

// MockSnackBar is created per-test as a spy object (see beforeEach)

class MockMatDialog {
  open = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(true) });
}

describe('DimensionManager', () => {
  let component: DimensionManager;
  let fixture: ComponentFixture<DimensionManager>;
  let dimensionService: MockDimensionService;
  let confirmationService: MockConfirmationService;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const mockDimensions: Dimension[] = [
    {
      id: 'dim-1',
      name: 'Year',
      uri: 'http://example.org/year',
      type: 'TEMPORAL' as DimensionType,
      baseUri: 'http://example.org/year/',
      valueCount: 5,
      createdAt: '2024-01-15T10:00:00Z',
      updatedAt: '2024-01-16T10:00:00Z'
    },
    {
      id: 'dim-2',
      name: 'Canton',
      uri: 'http://example.org/canton',
      type: 'GEO' as DimensionType,
      baseUri: 'http://example.org/canton/',
      valueCount: 3,
      createdAt: '2024-01-15T10:00:00Z',
      updatedAt: '2024-01-16T10:00:00Z'
    }
  ];

  beforeEach(async () => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [DimensionManager, BrowserAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: DimensionService, useClass: MockDimensionService },
        { provide: LoggerService, useClass: MockLoggerService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialog, useClass: MockMatDialog },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({}), queryParamMap: convertToParamMap({}) },
            paramMap: of(convertToParamMap({})),
            queryParamMap: of(convertToParamMap({}))
          }
        }
      ]
    })
    .overrideComponent(DimensionManager, {
      remove: {
        providers: [ConfirmationService],
        imports: [MatSnackBarModule]
      },
      add: {
        providers: [
          { provide: ConfirmationService, useClass: MockConfirmationService }
        ]
      }
    })
    .compileComponents();

    fixture = TestBed.createComponent(DimensionManager);
    component = fixture.componentInstance;
    dimensionService = TestBed.inject(DimensionService) as unknown as MockDimensionService;
    confirmationService = fixture.debugElement.injector.get(ConfirmationService) as unknown as MockConfirmationService;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load dimensions on init', () => {
    fixture.detectChanges();
    expect(dimensionService.list).toHaveBeenCalled();
  });

  it('should initialize with empty dimensions', () => {
    fixture.detectChanges();
    expect(component.dimensions()).toEqual([]);
  });

  it('should have dimensionForm defined', () => {
    expect(component.dimensionForm).toBeDefined();
  });

  it('should have editDimensionForm defined', () => {
    expect(component.editDimensionForm).toBeDefined();
  });

  it('should have valueForm defined', () => {
    expect(component.valueForm).toBeDefined();
  });

  describe('loadDimensions', () => {
    it('should populate dimensions on success', fakeAsync(() => {
      dimensionService.list.and.returnValue(of(mockDimensions));
      component.loadDimensions();
      tick();
      expect(component.dimensions()).toEqual(mockDimensions);
      expect(component.loading()).toBeFalse();
    }));

    it('should set error on failure', fakeAsync(() => {
      dimensionService.list.and.returnValue(throwError(() => new Error('Network error')));
      component.loadDimensions();
      tick();
      expect(component.error()).toBeTruthy();
      expect(component.loading()).toBeFalse();
    }));
  });

  describe('refreshDimensions', () => {
    it('should refresh and show snackbar on success', fakeAsync(() => {
      dimensionService.list.and.returnValue(of(mockDimensions));
      component.refreshDimensions();
      tick();
      expect(component.dimensions()).toEqual(mockDimensions);
      expect(component.refreshing()).toBeFalse();
      expect(snackBar.open).toHaveBeenCalledWith('Dimensions refreshed', 'Close', { duration: 2000 });
    }));
  });

  describe('filteredDimensions', () => {
    it('should filter by search query', () => {
      component.dimensions.set(mockDimensions);
      component.searchQuery.set('year');
      const filtered = component.filteredDimensions();
      expect(filtered.length).toBe(1);
      expect(filtered[0].name).toBe('Year');
    });

    it('should filter by type', () => {
      component.dimensions.set(mockDimensions);
      component.typeFilter.set('GEO');
      const filtered = component.filteredDimensions();
      expect(filtered.length).toBe(1);
      expect(filtered[0].name).toBe('Canton');
    });

    it('should return all when no filters', () => {
      component.dimensions.set(mockDimensions);
      const filtered = component.filteredDimensions();
      expect(filtered.length).toBe(2);
    });
  });

  describe('openCreateDialog', () => {
    it('should reset form and show dialog', () => {
      component.openCreateDialog();
      expect(component.createDialogVisible()).toBeTrue();
      expect(component.dimensionForm.value.type).toBe('KEY');
    });
  });

  describe('createDimension', () => {
    it('should not call service if form is invalid', () => {
      component.createDimension();
      expect(dimensionService.create).not.toHaveBeenCalled();
    });

    it('should call service if form is valid', fakeAsync(() => {
      component.dimensionForm.patchValue({
        name: 'Test Dim',
        uri: 'http://example.org/test',
        type: 'KEY'
      });
      component.createDimension();
      tick();
      expect(dimensionService.create).toHaveBeenCalled();
    }));

    it('should accept baseUri with trailing slash', fakeAsync(() => {
      component.dimensionForm.patchValue({
        name: 'Test Dim',
        uri: 'http://example.org/test',
        type: 'KEY',
        baseUri: 'http://example.org/base/'
      });
      component.createDimension();
      tick();
      expect(dimensionService.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ baseUri: 'http://example.org/base/' })
      );
    }));
  });

  describe('openEditDialog', () => {
    it('should populate edit form and show dialog', () => {
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.openEditDialog(mockDimensions[0], event);
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(component.editDialogVisible()).toBeTrue();
      expect(component.editDimensionForm.value.name).toBe('Year');
    });
  });

  describe('saveDimension', () => {
    it('should not call service if form is invalid', () => {
      component.saveDimension();
      expect(dimensionService.update).not.toHaveBeenCalled();
    });

    it('should call update when form is valid', fakeAsync(() => {
      component.editDimensionForm.patchValue({
        id: 'dim-1',
        name: 'Updated Year',
        uri: 'http://example.org/year',
        type: 'TEMPORAL'
      });
      component.saveDimension();
      tick();
      expect(dimensionService.update).toHaveBeenCalledWith('dim-1', jasmine.objectContaining({ name: 'Updated Year' }));
    }));
  });

  describe('deleteDimension', () => {
    it('should delete dimension', fakeAsync(() => {
      component.deleteDimension(mockDimensions[0]);
      tick();
      expect(dimensionService.delete).toHaveBeenCalledWith('dim-1');
    }));

    it('should not call delete if dimension has no id', fakeAsync(() => {
      component.deleteDimension({ name: 'No ID', uri: 'http://test', type: 'KEY' });
      tick();
      expect(dimensionService.delete).not.toHaveBeenCalled();
    }));
  });

  describe('confirmDelete', () => {
    it('should call confirmation service and delete on confirm', fakeAsync(() => {
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.confirmDelete(mockDimensions[0], event);
      tick();
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(confirmationService.confirm).toHaveBeenCalled();
      expect(dimensionService.delete).toHaveBeenCalledWith('dim-1');
    }));
  });

  describe('viewDetails', () => {
    it('should set selected dimension and show dialog', () => {
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.viewDetails(mockDimensions[0], event);
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(component.selectedDimension()).toEqual(mockDimensions[0]);
      expect(component.detailsDialogVisible()).toBeTrue();
    });
  });

  describe('openValuesDialog', () => {
    it('should set selected dimension and load values', fakeAsync(() => {
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.openValuesDialog(mockDimensions[0], event);
      tick();
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(component.selectedDimension()).toEqual(mockDimensions[0]);
      expect(component.valuesDialogVisible()).toBeTrue();
      expect(dimensionService.getValues).toHaveBeenCalledWith('dim-1');
    }));
  });

  describe('loadValues', () => {
    it('should load values from service', fakeAsync(() => {
      const mockValues: DimensionValue[] = [
        { id: 'v1', dimensionId: 'dim-1', code: '2024', label: '2024', uri: 'http://example.org/year/2024' }
      ];
      dimensionService.getValues.and.returnValue(of(mockValues));
      component.loadValues('dim-1');
      tick();
      expect(component.dimensionValues()).toEqual(mockValues);
      expect(component.valuesLoading()).toBeFalse();
    }));
  });

  describe('openAddValueDialog', () => {
    it('should reset value form and show dialog', () => {
      component.selectedDimension.set(mockDimensions[0]);
      component.openAddValueDialog();
      expect(component.addValueDialogVisible()).toBeTrue();
    });
  });

  describe('addValue', () => {
    it('should not call service if form is invalid', () => {
      component.selectedDimension.set(mockDimensions[0]);
      component.addValue();
      expect(dimensionService.addValue).not.toHaveBeenCalled();
    });

    it('should call addValue on service if form is valid', fakeAsync(() => {
      component.selectedDimension.set(mockDimensions[0]);
      component.valueForm.patchValue({
        code: 'TEST',
        label: 'Test Value'
      });
      component.addValue();
      tick();
      expect(dimensionService.addValue).toHaveBeenCalledWith('dim-1', jasmine.objectContaining({
        code: 'TEST',
        label: 'Test Value',
        dimensionId: 'dim-1'
      }));
    }));
  });

  describe('openEditValueDialog', () => {
    it('should set selected value and show dialog', () => {
      const mockValue: DimensionValue = { id: 'v1', dimensionId: 'dim-1', code: 'CH', label: 'Switzerland', uri: 'http://test' };
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.openEditValueDialog(mockValue, event);
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(component.selectedValue()).toEqual(mockValue);
      expect(component.editValueDialogVisible()).toBeTrue();
    });
  });

  describe('saveValue', () => {
    it('should not call service if form is invalid', () => {
      component.saveValue();
      expect(dimensionService.updateValue).not.toHaveBeenCalled();
    });

    it('should call updateValue on service if form is valid', fakeAsync(() => {
      component.editValueForm.patchValue({
        id: 'v1',
        code: 'UPD',
        label: 'Updated'
      });
      component.saveValue();
      tick();
      expect(dimensionService.updateValue).toHaveBeenCalledWith('v1', jasmine.objectContaining({ code: 'UPD' }));
    }));
  });

  describe('deleteValue', () => {
    it('should delete value via service', fakeAsync(() => {
      component.selectedDimension.set(mockDimensions[0]);
      const mockValue: DimensionValue = { id: 'v1', dimensionId: 'dim-1', code: 'CH', label: 'Switzerland', uri: 'http://test' };
      component.deleteValue(mockValue);
      tick();
      expect(dimensionService.deleteValue).toHaveBeenCalledWith('v1');
    }));

    it('should not call service if value has no id', fakeAsync(() => {
      component.selectedDimension.set(mockDimensions[0]);
      const mockValue: DimensionValue = { dimensionId: 'dim-1', code: 'CH', label: 'Switzerland', uri: 'http://test' };
      component.deleteValue(mockValue);
      tick();
      expect(dimensionService.deleteValue).not.toHaveBeenCalled();
    }));
  });

  describe('openImportDialog', () => {
    it('should reset import state and show dialog', () => {
      component.openImportDialog();
      expect(component.importDialogVisible()).toBeTrue();
      expect(component.importCsvData()).toBe('');
      expect(component.parsedCsvPreview()).toEqual([]);
      expect(component.hasHeaderRow()).toBeTrue();
      expect(component.csvDelimiter()).toBe(',');
      expect(component.importError()).toBeNull();
    });
  });

  describe('parseCsvPreview', () => {
    it('should parse CSV data into preview', () => {
      component.importCsvData.set('code,label,desc\nCH,Switzerland,Country\nDE,Germany,Country');
      component.hasHeaderRow.set(true);
      component.csvDelimiter.set(',');
      component.parseCsvPreview();
      const preview = component.parsedCsvPreview();
      expect(preview.length).toBe(2);
      expect(preview[0].code).toBe('CH');
      expect(preview[0].label).toBe('Switzerland');
    });

    it('should handle empty data', () => {
      component.importCsvData.set('');
      component.parseCsvPreview();
      expect(component.parsedCsvPreview()).toEqual([]);
    });

    it('should limit preview to 5 rows', () => {
      component.importCsvData.set('code,label\n1,a\n2,b\n3,c\n4,d\n5,e\n6,f\n7,g');
      component.hasHeaderRow.set(true);
      component.csvDelimiter.set(',');
      component.parseCsvPreview();
      expect(component.parsedCsvPreview().length).toBe(5);
    });
  });

  describe('getTypeLabel', () => {
    it('should return formatted type labels', () => {
      expect(component.getTypeLabel('TEMPORAL')).toBe('Temporal');
      expect(component.getTypeLabel('GEO')).toBe('Geographic');
      expect(component.getTypeLabel('MEASURE')).toBe('Measure');
      expect(component.getTypeLabel('KEY')).toBe('Key');
      expect(component.getTypeLabel('CODED')).toBe('Coded');
      expect(component.getTypeLabel('ATTRIBUTE')).toBe('Attribute');
    });
  });

  describe('getTypeColor', () => {
    it('should return correct colors for types', () => {
      expect(component.getTypeColor('KEY')).toBe('primary');
      expect(component.getTypeColor('TEMPORAL')).toBe('accent');
      expect(component.getTypeColor('GEO')).toBe('primary');
      expect(component.getTypeColor('MEASURE')).toBe('warn');
      expect(component.getTypeColor('CODED')).toBe('accent');
    });
  });

  describe('getErrorMessage', () => {
    it('should return empty string for untouched control', () => {
      expect(component.getErrorMessage('name', component.dimensionForm)).toBe('');
    });

    it('should return required error for touched empty field', () => {
      const control = component.dimensionForm.get('name');
      control?.markAsTouched();
      control?.setValue('');
      expect(component.getErrorMessage('name', component.dimensionForm)).toBe('This field is required');
    });
  });

  describe('formatDate', () => {
    it('should return dash for undefined', () => {
      expect(component.formatDate(undefined)).toBe('-');
    });

    it('should return formatted date string', () => {
      const result = component.formatDate('2024-01-15T10:00:00Z');
      expect(result.length).toBeGreaterThan(0);
      expect(result).not.toBe('-');
    });
  });

  describe('onSortChange', () => {
    it('should update currentSort signal', () => {
      component.onSortChange({ active: 'type', direction: 'desc' });
      expect(component.currentSort()).toEqual({ active: 'type', direction: 'desc' });
    });
  });

  describe('computed stats', () => {
    it('should compute totalDimensions', () => {
      component.dimensions.set(mockDimensions);
      expect(component.totalDimensions()).toBe(2);
    });

    it('should compute totalValues', () => {
      component.dimensions.set(mockDimensions);
      expect(component.totalValues()).toBe(8); // 5 + 3
    });

    it('should compute byType', () => {
      component.dimensions.set(mockDimensions);
      const counts = component.byType();
      expect(counts['TEMPORAL']).toBe(1);
      expect(counts['GEO']).toBe(1);
    });
  });

  describe('dialog helpers', () => {
    it('should close create dialog', () => {
      component.createDialogVisible.set(true);
      component.closeCreateDialog();
      expect(component.createDialogVisible()).toBeFalse();
    });

    it('should close edit dialog', () => {
      component.editDialogVisible.set(true);
      component.closeEditDialog();
      expect(component.editDialogVisible()).toBeFalse();
    });

    it('should close details dialog', () => {
      component.detailsDialogVisible.set(true);
      component.closeDetailsDialog();
      expect(component.detailsDialogVisible()).toBeFalse();
    });

    it('should close values dialog', () => {
      component.valuesDialogVisible.set(true);
      component.closeValuesDialog();
      expect(component.valuesDialogVisible()).toBeFalse();
    });

    it('should close import dialog', () => {
      component.importDialogVisible.set(true);
      component.closeImportDialog();
      expect(component.importDialogVisible()).toBeFalse();
    });

    it('should close add value dialog', () => {
      component.addValueDialogVisible.set(true);
      component.closeAddValueDialog();
      expect(component.addValueDialogVisible()).toBeFalse();
    });

    it('should close edit value dialog', () => {
      component.editValueDialogVisible.set(true);
      component.closeEditValueDialog();
      expect(component.editValueDialogVisible()).toBeFalse();
    });
  });

  describe('cleanup', () => {
    it('should unsubscribe on destroy', () => {
      fixture.detectChanges();
      spyOn(component['destroy$'], 'next');
      spyOn(component['destroy$'], 'complete');
      component.ngOnDestroy();
      expect(component['destroy$'].next).toHaveBeenCalled();
      expect(component['destroy$'].complete).toHaveBeenCalled();
    });
  });
});
