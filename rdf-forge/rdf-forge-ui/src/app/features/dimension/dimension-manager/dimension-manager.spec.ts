import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DimensionManager } from './dimension-manager';
import { DimensionService } from '../../../core/services/dimension.service';
import { NotificationService } from '../../../core/services/notification.service';
import { DialogService } from '../../../core/services/dialog.service';
import { of, throwError } from 'rxjs';
import { Dimension, DimensionType } from '../../../core/models/dimension.model';
import { NO_ERRORS_SCHEMA } from '@angular/core';

class MockDimensionService {
  list = jasmine.createSpy('list').and.returnValue(of({ content: [], totalElements: 0 }));
  create = jasmine.createSpy('create').and.returnValue(of({ id: 'dim-1', name: 'Test Dimension' }));
  update = jasmine.createSpy('update').and.returnValue(of({ id: 'dim-1', name: 'Updated' }));
  delete = jasmine.createSpy('delete').and.returnValue(of(void 0));
  search = jasmine.createSpy('search').and.returnValue(of([]));
  get = jasmine.createSpy('get').and.returnValue(of({ id: 'dim-1', name: 'Test' }));
  deleteValue = jasmine.createSpy('deleteValue').and.returnValue(of(void 0));
  importFromCsv = jasmine.createSpy('importFromCsv').and.returnValue(of({ success: true, imported: 5 }));
}

class MockNotificationService {
  success = jasmine.createSpy('success');
  error = jasmine.createSpy('error');
  info = jasmine.createSpy('info');
}

class MockDialogService {
  confirm = jasmine.createSpy('confirm').and.returnValue(of(true));
  open = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of({ confirmed: true }) });
}

describe('DimensionManager', () => {
  let component: DimensionManager;
  let fixture: ComponentFixture<DimensionManager>;
  let dimensionService: MockDimensionService;
  let notificationService: MockNotificationService;
  let dialogService: MockDialogService;

  const mockDimensions: Dimension[] = [
    {
      id: 'dim-1',
      name: 'Year',
      uri: 'http://example.org/year',
      type: 'TEMPORAL' as DimensionType,
      createdAt: '2024-01-15T10:00:00Z'
    },
    {
      id: 'dim-2',
      name: 'Canton',
      uri: 'http://example.org/canton',
      type: 'GEO' as DimensionType,
      createdAt: '2024-01-15T10:00:00Z'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DimensionManager],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: DimensionService, useClass: MockDimensionService },
        { provide: NotificationService, useClass: MockNotificationService },
        { provide: DialogService, useClass: MockDialogService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DimensionManager);
    component = fixture.componentInstance;
    dimensionService = TestBed.inject(DimensionService) as unknown as MockDimensionService;
    notificationService = TestBed.inject(NotificationService) as unknown as MockNotificationService;
    dialogService = TestBed.inject(DialogService) as unknown as MockDialogService;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with empty tree', () => {
    fixture.detectChanges();
    expect(component.dimensions()).toEqual([]);
  });

  it('should set new dimension form values', () => {
    fixture.detectChanges();
    // The form should exist after view init
    expect(component.newDimensionForm).toBeDefined();
  });

  describe('createDimension', () => {
    it('should show error if form invalid', () => {
      fixture.detectChanges();
      component.createDimension();
      expect(notificationService.error).toHaveBeenCalled();
    });
  });

  describe('deleteDimension', () => {
    it('should delete dimension after confirmation', fakeAsync(() => {
      fixture.detectChanges();
      component.deleteDimension(mockDimensions[0], new Event('click') as any);
      tick();
      expect(dimensionService.delete).toHaveBeenCalledWith('dim-1');
    }));
  });

  describe('addValue', () => {
    it('should add value to dimension', () => {
      fixture.detectChanges();
      component.addValue(mockDimensions[0]);
      expect(component.editingDimension()).toEqual(mockDimensions[0]);
    });
  });

  describe('deleteValue', () => {
    it('should delete value after confirmation', fakeAsync(() => {
      const mockDim = { ...mockDimensions[0], values: [{ id: 'val-1', code: 'CH', label: 'Switzerland' }] };
      fixture.detectChanges();
      component.deleteValue(mockDim, mockDim.values[0], new Event('click') as any);
      tick();
      expect(dimensionService.deleteValue).toHaveBeenCalledWith('dim-1', 'val-1');
    }));
  });

  describe('exportDimension', () => {
    it('should export dimension as Turtle', () => {
      fixture.detectChanges();
      const event = new Event('click');
      spyOn(event, 'stopPropagation');
      component.exportDimension(mockDimensions[0], event as any);
      expect(event.stopPropagation).toHaveBeenCalled();
    });
  });

  describe('copyUri', () => {
    it('should copy URI to clipboard', async () => {
      fixture.detectChanges();
      spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
      await component.copyUri('http://test.uri', new Event('click') as any);
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('http://test.uri');
    });
  });

  describe('toggleExpand', () => {
    it('should toggle dimension expansion', () => {
      fixture.detectChanges();
      expect(component.isExpanded('dim-1')).toBeFalse();
      component.toggleExpand('dim-1');
      expect(component.isExpanded('dim-1')).toBeTrue();
      component.toggleExpand('dim-1');
      expect(component.isExpanded('dim-1')).toBeFalse();
    });
  });

  describe('selectDimension', () => {
    it('should select and deselect dimension', () => {
      fixture.detectChanges();
      component.selectDimension(mockDimensions[0]);
      expect(component.selectedDimension()).toEqual(mockDimensions[0]);
      component.selectDimension(mockDimensions[0]);
      expect(component.selectedDimension()).toBeNull();
    });
  });

  describe('startEditingDimension', () => {
    it('should populate edit form', () => {
      fixture.detectChanges();
      component.startEditingDimension(mockDimensions[0], new Event('click') as any);
      expect(component.editingDimension()).toEqual(mockDimensions[0]);
    });
  });

  describe('saveDimensionChanges', () => {
    it('should update dimension', () => {
      fixture.detectChanges();
      component.editingDimension.set(mockDimensions[0]);
      component.editDimensionForm.patchValue({ name: 'Updated' });
      component.saveDimensionChanges();
      expect(dimensionService.update).toHaveBeenCalled();
    });
  });

  describe('cancelEditingDimension', () => {
    it('should clear editing state', () => {
      fixture.detectChanges();
      component.editingDimension.set(mockDimensions[0]);
      component.cancelEditingDimension();
      expect(component.editingDimension()).toBeNull();
    });
  });

  describe('startAddingValue', () => {
    it('should initialize value form', () => {
      fixture.detectChanges();
      component.startAddingValue(mockDimensions[0]);
      expect(component.addingValueTo()).toEqual(mockDimensions[0]);
    });
  });

  describe('cancelAddingValue', () => {
    it('should clear adding value state', () => {
      fixture.detectChanges();
      component.addingValueTo.set(mockDimensions[0]);
      component.cancelAddingValue();
      expect(component.addingValueTo()).toBeNull();
    });
  });

  describe('saveValue', () => {
    it('should add new value', () => {
      fixture.detectChanges();
      component.addingValueTo.set(mockDimensions[0]);
      component.valueForm.patchValue({ code: 'TEST', label: 'Test Value' });
      component.saveValue();
      expect(notificationService.success).toHaveBeenCalled();
    });
  });

  describe('startEditingValue', () => {
    it('should set editing value state', () => {
      fixture.detectChanges();
      const mockValue = { id: 'val-1', code: 'CH', label: 'Switzerland' };
      component.startEditingValue(mockDimensions[0], mockValue);
      expect(component.editingValueIn()).toEqual(mockDimensions[0]);
      expect(component.editingValueId()).toBe('val-1');
    });
  });

  describe('cancelEditingValue', () => {
    it('should clear editing value state', () => {
      fixture.detectChanges();
      component.editingValueIn.set(mockDimensions[0]);
      component.editingValueId.set('val-1');
      component.cancelEditingValue();
      expect(component.editingValueIn()).toBeNull();
      expect(component.editingValueId()).toBeNull();
    });
  });

  describe('updateValue', () => {
    it('should update value', () => {
      fixture.detectChanges();
      component.editingValueIn.set(mockDimensions[0]);
      component.editingValueId.set('val-1');
      component.valueForm.patchValue({ code: 'UPD', label: 'Updated' });
      component.updateValue();
      expect(notificationService.success).toHaveBeenCalled();
    });
  });

  describe('importValue', () => {
    it('should import values from CSV', () => {
      fixture.detectChanges();
      component.importingTo.set(mockDimensions[0]);
      const mockFile = new File(['code,label\nTEST,Test'], 'values.csv', { type: 'text/csv' });
      component.importValue(mockFile);
      expect(dimensionService.importFromCsv).toHaveBeenCalled();
    });
  });

  describe('closeImport', () => {
    it('should clear import state', () => {
      fixture.detectChanges();
      component.importingTo.set(mockDimensions[0]);
      component.closeImport();
      expect(component.importingTo()).toBeNull();
    });
  });

  describe('formatType', () => {
    it('should return formatted type names', () => {
      fixture.detectChanges();
      expect(component.formatType('TEMPORAL')).toBe('Temporal');
      expect(component.formatType('GEO')).toBe('Geographic');
      expect(component.formatType('MEASURE')).toBe('Measure');
      expect(component.formatType('KEY')).toBe('Key');
      expect(component.formatType('CODED')).toBe('Coded');
      expect(component.formatType('' as DimensionType)).toBe('' as DimensionType);
    });
  });

  describe('formatDate', () => {
    it('should format date or return dash', () => {
      fixture.detectChanges();
      expect(component.formatDate(undefined)).toBe('-');
      const dateStr = '2024-01-15T10:00:00Z';
      const result = component.formatDate(dateStr);
      expect(result.length).toBeGreaterThan(0);
      expect(result).not.toBe('-');
    });
  });

  describe('onFileSelected', () => {
    it('should handle CSV file selection for import', () => {
      fixture.detectChanges();
      component.importingTo.set(mockDimensions[0]);
      const mockFile = new File(['code,label\nTEST,Test'], 'values.csv', { type: 'text/csv' });
      component.onFileSelected({ files: [mockFile] } as any);
      expect(dimensionService.importFromCsv).toHaveBeenCalled();
    });

    it('should reject non-CSV files', () => {
      fixture.detectChanges();
      component.importingTo.set(mockDimensions[0]);
      const mockFile = new File(['content'], 'values.txt', { type: 'text/plain' });
      component.onFileSelected({ files: [mockFile] } as any);
      expect(notificationService.error).toHaveBeenCalled();
    });

    it('should handle empty file selection', () => {
      fixture.detectChanges();
      component.onFileSelected({ files: [] } as any);
      expect(dimensionService.importFromCsv).not.toHaveBeenCalled();
    });
  });

  describe('onEscape', () => {
    it('should close dialogs on escape', () => {
      fixture.detectChanges();
      component.importingTo.set(mockDimensions[0]);
      component.onEscape();
      expect(component.importingTo()).toBeNull();
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
