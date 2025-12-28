import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { DimensionManager } from './dimension-manager';
import { DimensionService } from '../../../core/services';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { Dimension, DimensionValue } from '../../../core/models';

describe('DimensionManager', () => {
  let component: DimensionManager;
  let fixture: ComponentFixture<DimensionManager>;
  let dimensionServiceSpy: jasmine.SpyObj<DimensionService>;
  let confirmationServiceSpy: jasmine.SpyObj<ConfirmationService>;

  const mockDimensions: Dimension[] = [
    { id: '1', name: 'Year', uri: 'http://example.org/dimension/year', type: 'TEMPORAL', description: 'Year dimension' },
    { id: '2', name: 'Region', uri: 'http://example.org/dimension/region', type: 'KEY', description: 'Geographic region' },
    { id: '3', name: 'Measure', uri: 'http://example.org/dimension/measure', type: 'MEASURE', description: 'Measure dimension' }
  ];

  const mockValues: DimensionValue[] = [
    { id: 'v1', dimensionId: '1', code: '2023', label: '2023', uri: 'http://example.org/year/2023' },
    { id: 'v2', dimensionId: '1', code: '2024', label: '2024', uri: 'http://example.org/year/2024' }
  ];

  beforeEach(async () => {
    dimensionServiceSpy = jasmine.createSpyObj('DimensionService', [
      'list', 'get', 'create', 'update', 'delete', 'getValues', 'addValue', 'deleteValue'
    ]);
    confirmationServiceSpy = jasmine.createSpyObj('ConfirmationService', ['confirm']);

    dimensionServiceSpy.list.and.returnValue(of(mockDimensions));
    dimensionServiceSpy.getValues.and.returnValue(of(mockValues));

    await TestBed.configureTestingModule({
      imports: [DimensionManager],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: DimensionService, useValue: dimensionServiceSpy },
        { provide: ConfirmationService, useValue: confirmationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DimensionManager);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });

  it('should load dimensions on init', fakeAsync(() => {
    tick();
    expect(dimensionServiceSpy.list).toHaveBeenCalled();
    expect(component.dimensions().length).toBe(3);
    expect(component.loading()).toBeFalse();
  }));

  it('should filter dimensions by search query', fakeAsync(() => {
    tick();
    component.searchQuery.set('Year');
    expect(component.filteredDimensions().length).toBe(1);
    expect(component.filteredDimensions()[0].name).toBe('Year');
  }));

  it('should filter dimensions by type', fakeAsync(() => {
    tick();
    component.typeFilter.set('TEMPORAL');
    expect(component.filteredDimensions().length).toBe(1);
    expect(component.filteredDimensions()[0].type).toBe('TEMPORAL');
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    dimensionServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadDimensions();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should open create dialog', () => {
    component.openCreateDialog();
    expect(component.createDialogVisible()).toBeTrue();
  });

  it('should create dimension', fakeAsync(() => {
    const newDim: Partial<Dimension> = { name: 'New Dim', uri: 'http://example.org/new', type: 'KEY' };
    dimensionServiceSpy.create.and.returnValue(of({ ...newDim, id: '4' } as Dimension));

    component.newDimension.set(newDim);
    component.createDimension();
    tick();

    expect(dimensionServiceSpy.create).toHaveBeenCalled();
    expect(component.createDialogVisible()).toBeFalse();
  }));

  it('should delete dimension', fakeAsync(() => {
    dimensionServiceSpy.delete.and.returnValue(of(void 0));
    component.deleteDimension(mockDimensions[0]);
    tick();
    expect(dimensionServiceSpy.delete).toHaveBeenCalledWith('1');
  }));

  it('should get dimension type icon', () => {
    expect(component.getTypeIcon('TEMPORAL')).toBe('event');
    expect(component.getTypeIcon('KEY')).toBe('key');
    expect(component.getTypeIcon('MEASURE')).toBe('bar_chart');
  });

  it('should get type color', () => {
    expect(component.getTypeColor('TEMPORAL')).toBeTruthy();
    expect(component.getTypeColor('KEY')).toBeTruthy();
    expect(component.getTypeColor('MEASURE')).toBeTruthy();
  });

  it('should have dialog visibility signals', () => {
    expect(component.createDialogVisible()).toBeFalse();
    expect(component.editDialogVisible()).toBeFalse();
    expect(component.detailsDialogVisible()).toBeFalse();
    expect(component.valuesDialogVisible()).toBeFalse();
  });

  it('should filter dimensions', fakeAsync(() => {
    tick();
    expect(component.filteredDimensions().length).toBe(3);
    component.searchQuery.set('Region');
    expect(component.filteredDimensions().length).toBe(1);
  }));

  it('should not create dimension without required fields', () => {
    component.newDimension.set({ name: '', uri: '', type: 'KEY' });
    component.createDimension();
    expect(dimensionServiceSpy.create).not.toHaveBeenCalled();
  });

  it('should handle create dimension error', fakeAsync(() => {
    dimensionServiceSpy.create.and.returnValue(throwError(() => new Error('Create failed')));
    component.newDimension.set({ name: 'Test', uri: 'http://test.org/', type: 'KEY' });
    component.createDimension();
    tick();
    // Error handled via snackbar
  }));

  it('should open edit dialog', () => {
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.openEditDialog(mockDimensions[0], mockEvent as any);
    expect(component.editDialogVisible()).toBeTrue();
    expect(component.editDimension().id).toBe('1');
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  });

  it('should save dimension', fakeAsync(() => {
    dimensionServiceSpy.update.and.returnValue(of(mockDimensions[0]));
    component.editDimension.set({ ...mockDimensions[0] });
    component.saveDimension();
    tick();
    expect(dimensionServiceSpy.update).toHaveBeenCalledWith('1', jasmine.any(Object));
    expect(component.editDialogVisible()).toBeFalse();
  }));

  it('should not save dimension without id', () => {
    component.editDimension.set({ name: 'Test', uri: 'http://test.org/' });
    component.saveDimension();
    expect(dimensionServiceSpy.update).not.toHaveBeenCalled();
  });

  it('should handle save dimension error', fakeAsync(() => {
    dimensionServiceSpy.update.and.returnValue(throwError(() => new Error('Update failed')));
    component.editDimension.set({ ...mockDimensions[0] });
    component.saveDimension();
    tick();
    // Error handled via snackbar
  }));

  it('should handle delete dimension error', fakeAsync(() => {
    dimensionServiceSpy.delete.and.returnValue(throwError(() => new Error('Delete failed')));
    component.deleteDimension(mockDimensions[0]);
    tick();
    // Error handled via snackbar
  }));

  it('should not delete dimension without id', () => {
    component.deleteDimension({ name: 'Test' } as Dimension);
    expect(dimensionServiceSpy.delete).not.toHaveBeenCalled();
  });

  it('should view details', () => {
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.viewDetails(mockDimensions[0], mockEvent as any);
    expect(component.detailsDialogVisible()).toBeTrue();
    expect(component.selectedDimension()).toBe(mockDimensions[0]);
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  });

  it('should open values dialog', fakeAsync(() => {
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.openValuesDialog(mockDimensions[0], mockEvent as any);
    tick();
    expect(component.valuesDialogVisible()).toBeTrue();
    expect(dimensionServiceSpy.getValues).toHaveBeenCalledWith('1');
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should handle load values error', fakeAsync(() => {
    dimensionServiceSpy.getValues.and.returnValue(throwError(() => new Error('Load failed')));
    component.loadValues('1');
    tick();
    expect(component.valuesLoading()).toBeFalse();
  }));

  it('should open add value dialog', () => {
    component.selectedDimension.set(mockDimensions[0]);
    component.openAddValueDialog();
    expect(component.addValueDialogVisible()).toBeTrue();
  });

  it('should add value', fakeAsync(() => {
    dimensionServiceSpy.addValue.and.returnValue(of(mockValues[0]));
    component.selectedDimension.set(mockDimensions[0]);
    component.newValue.set({ code: 'test', label: 'Test', uri: 'http://test.org/', description: '', parentId: null });
    component.addValue();
    tick();
    expect(dimensionServiceSpy.addValue).toHaveBeenCalled();
    expect(component.addValueDialogVisible()).toBeFalse();
  }));

  it('should not add value without required fields', () => {
    component.selectedDimension.set(mockDimensions[0]);
    component.newValue.set({ code: '', label: '', uri: '', description: '', parentId: null });
    component.addValue();
    expect(dimensionServiceSpy.addValue).not.toHaveBeenCalled();
  });

  it('should not add value without selected dimension', () => {
    component.selectedDimension.set(null);
    component.newValue.set({ code: 'test', label: 'Test', uri: '', description: '', parentId: null });
    component.addValue();
    expect(dimensionServiceSpy.addValue).not.toHaveBeenCalled();
  });

  it('should handle add value error', fakeAsync(() => {
    dimensionServiceSpy.addValue.and.returnValue(throwError(() => new Error('Add failed')));
    component.selectedDimension.set(mockDimensions[0]);
    component.newValue.set({ code: 'test', label: 'Test', uri: '', description: '', parentId: null });
    component.addValue();
    tick();
    // Error handled via snackbar
  }));

  it('should open edit value dialog', () => {
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.openEditValueDialog(mockValues[0], mockEvent as any);
    expect(component.editValueDialogVisible()).toBeTrue();
    expect(component.selectedValue()).toBe(mockValues[0]);
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  });

  it('should save value', fakeAsync(() => {
    dimensionServiceSpy.updateValue = jasmine.createSpy().and.returnValue(of(mockValues[0]));
    component.selectedDimension.set(mockDimensions[0]);
    component.editValue.set({ ...mockValues[0] });
    component.saveValue();
    tick();
    expect(dimensionServiceSpy.updateValue).toHaveBeenCalled();
    expect(component.editValueDialogVisible()).toBeFalse();
  }));

  it('should not save value without id', () => {
    dimensionServiceSpy.updateValue = jasmine.createSpy();
    component.editValue.set({ code: 'test', label: 'Test' });
    component.saveValue();
    expect(dimensionServiceSpy.updateValue).not.toHaveBeenCalled();
  });

  it('should delete value', fakeAsync(() => {
    dimensionServiceSpy.deleteValue.and.returnValue(of(void 0));
    component.selectedDimension.set(mockDimensions[0]);
    component.deleteValue(mockValues[0]);
    tick();
    expect(dimensionServiceSpy.deleteValue).toHaveBeenCalledWith('v1');
  }));

  it('should not delete value without id', () => {
    component.deleteValue({ code: 'test', label: 'Test' } as DimensionValue);
    expect(dimensionServiceSpy.deleteValue).not.toHaveBeenCalled();
  });

  it('should handle delete value error', fakeAsync(() => {
    dimensionServiceSpy.deleteValue.and.returnValue(throwError(() => new Error('Delete failed')));
    component.selectedDimension.set(mockDimensions[0]);
    component.deleteValue(mockValues[0]);
    tick();
    // Error handled via snackbar
  }));

  it('should open import dialog', () => {
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.openImportDialog(mockDimensions[0], mockEvent as any);
    expect(component.importDialogVisible()).toBeTrue();
    expect(component.selectedDimension()).toBe(mockDimensions[0]);
  });

  it('should handle file select', fakeAsync(() => {
    dimensionServiceSpy.importCsv = jasmine.createSpy().and.returnValue(of({ imported: 10 }));
    component.selectedDimension.set(mockDimensions[0]);
    const mockFile = new File(['test'], 'test.csv', { type: 'text/csv' });
    component.onFileSelect({ files: [mockFile] });
    tick();
    expect(dimensionServiceSpy.importCsv).toHaveBeenCalled();
    expect(component.importDialogVisible()).toBeFalse();
  }));

  it('should not process file without selection', () => {
    dimensionServiceSpy.importCsv = jasmine.createSpy();
    component.selectedDimension.set(null);
    component.onFileSelect({ files: [] });
    expect(dimensionServiceSpy.importCsv).not.toHaveBeenCalled();
  });

  it('should handle import error', fakeAsync(() => {
    dimensionServiceSpy.importCsv = jasmine.createSpy().and.returnValue(throwError(() => new Error('Import failed')));
    component.selectedDimension.set(mockDimensions[0]);
    const mockFile = new File(['test'], 'test.csv', { type: 'text/csv' });
    component.onFileSelect({ files: [mockFile] });
    tick();
    expect(component.importing()).toBeFalse();
  }));

  it('should export turtle', fakeAsync(() => {
    dimensionServiceSpy.exportTurtle = jasmine.createSpy().and.returnValue(of(new Blob(['test'], { type: 'text/turtle' })));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.exportTurtle(mockDimensions[0], mockEvent as any);
    tick();
    expect(dimensionServiceSpy.exportTurtle).toHaveBeenCalledWith('1');
  }));

  it('should not export without id', () => {
    dimensionServiceSpy.exportTurtle = jasmine.createSpy();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.exportTurtle({ name: 'Test' } as Dimension, mockEvent as any);
    expect(dimensionServiceSpy.exportTurtle).not.toHaveBeenCalled();
  });

  it('should handle export error', fakeAsync(() => {
    dimensionServiceSpy.exportTurtle = jasmine.createSpy().and.returnValue(throwError(() => new Error('Export failed')));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.exportTurtle(mockDimensions[0], mockEvent as any);
    tick();
    // Error handled via snackbar
  }));

  it('should format date', () => {
    expect(component.formatDate(undefined)).toBe('-');
    expect(component.formatDate('2024-01-15T10:00:00Z').length).toBeGreaterThan(0);
  });

  it('should copy to clipboard', () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.copyToClipboard('test', 'Test');
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('test');
  });

  it('should update new dimension name', () => {
    component.updateNewDimensionName('New Name');
    expect(component.newDimension().name).toBe('New Name');
  });

  it('should update new dimension uri', () => {
    component.updateNewDimensionUri('http://new.uri/');
    expect(component.newDimension().uri).toBe('http://new.uri/');
  });

  it('should update new dimension type', () => {
    component.updateNewDimensionType('TEMPORAL');
    expect(component.newDimension().type).toBe('TEMPORAL');
  });

  it('should update new dimension base uri', () => {
    component.updateNewDimensionBaseUri('http://base.uri/');
    expect(component.newDimension().baseUri).toBe('http://base.uri/');
  });

  it('should update new dimension description', () => {
    component.updateNewDimensionDescription('New Description');
    expect(component.newDimension().description).toBe('New Description');
  });

  it('should update edit dimension fields', () => {
    component.updateEditDimensionName('Edit Name');
    component.updateEditDimensionUri('http://edit.uri/');
    component.updateEditDimensionType('GEO');
    component.updateEditDimensionBaseUri('http://edit-base.uri/');
    component.updateEditDimensionDescription('Edit Description');

    expect(component.editDimension().name).toBe('Edit Name');
    expect(component.editDimension().uri).toBe('http://edit.uri/');
    expect(component.editDimension().type).toBe('GEO');
    expect(component.editDimension().baseUri).toBe('http://edit-base.uri/');
    expect(component.editDimension().description).toBe('Edit Description');
  });

  it('should update new value fields', () => {
    component.updateNewValueCode('code1');
    component.updateNewValueLabel('Label 1');
    component.updateNewValueUri('http://value.uri/');
    component.updateNewValueDescription('Value Description');

    expect(component.newValue().code).toBe('code1');
    expect(component.newValue().label).toBe('Label 1');
    expect(component.newValue().uri).toBe('http://value.uri/');
    expect(component.newValue().description).toBe('Value Description');
  });

  it('should update edit value fields', () => {
    component.updateEditValueCode('edit-code');
    component.updateEditValueLabel('Edit Label');
    component.updateEditValueUri('http://edit-value.uri/');
    component.updateEditValueDescription('Edit Description');

    expect(component.editValue().code).toBe('edit-code');
    expect(component.editValue().label).toBe('Edit Label');
    expect(component.editValue().uri).toBe('http://edit-value.uri/');
    expect(component.editValue().description).toBe('Edit Description');
  });

  it('should compute total dimensions', fakeAsync(() => {
    tick();
    expect(component.totalDimensions()).toBe(3);
  }));

  it('should compute total values', fakeAsync(() => {
    tick();
    expect(component.totalValues()).toBeGreaterThanOrEqual(0);
  }));

  it('should compute by type', fakeAsync(() => {
    tick();
    const byType = component.byType();
    expect(byType['TEMPORAL']).toBe(1);
    expect(byType['KEY']).toBe(1);
    expect(byType['MEASURE']).toBe(1);
  }));

  it('should filter values by search query', fakeAsync(() => {
    tick();
    component.dimensionValues.set(mockValues);
    component.valuesSearchQuery.set('2023');
    expect(component.filteredValues().length).toBe(1);
    expect(component.filteredValues()[0].code).toBe('2023');
  }));

  it('should have type options', () => {
    expect(component.typeOptions.length).toBeGreaterThan(0);
    expect(component.typeOptions.map(o => o.value)).toContain('KEY');
  });

  it('should have type filter options', () => {
    expect(component.typeFilterOptions.length).toBeGreaterThan(0);
    expect(component.typeFilterOptions[0].value).toBeNull();
  });

  it('should get GEO type icon and color', () => {
    expect(component.getTypeIcon('GEO')).toBe('place');
    expect(component.getTypeColor('GEO')).toBe('warn');
  });

  it('should get CODED type icon and color', () => {
    expect(component.getTypeIcon('CODED')).toBe('list');
    expect(component.getTypeColor('CODED')).toBe('primary');
  });

  it('should get default type icon and color', () => {
    expect(component.getTypeIcon('UNKNOWN')).toBe('label');
    expect(component.getTypeColor('UNKNOWN')).toBe('');
  });

  it('should filter by URI', fakeAsync(() => {
    tick();
    component.searchQuery.set('example.org/dimension/year');
    expect(component.filteredDimensions().length).toBe(1);
  }));
});
