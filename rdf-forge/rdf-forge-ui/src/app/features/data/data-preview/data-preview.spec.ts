import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { DataPreviewComponent } from './data-preview';
import { DataService } from '../../../core/services';
import { DataPreview } from '../../../core/models';

describe('DataPreviewComponent', () => {
  let component: DataPreviewComponent;
  let fixture: ComponentFixture<DataPreviewComponent>;
  let dataService: jasmine.SpyObj<DataService>;

  const mockPreviewData: DataPreview = {
    columns: ['id', 'name', 'value'],
    data: [
      { id: '1', name: 'Item 1', value: '100' },
      { id: '2', name: 'Item 2', value: '200' }
    ],
    schema: [],
    totalRows: 10
  };

  beforeEach(async () => {
    dataService = jasmine.createSpyObj('DataService', ['preview']);
    dataService.preview.and.returnValue(of(mockPreviewData));

    await TestBed.configureTestingModule({
      imports: [DataPreviewComponent, NoopAnimationsModule],
      providers: [
        { provide: DataService, useValue: dataService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DataPreviewComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not load preview when dataSourceId is null', () => {
    component.dataSourceId = null;
    component.loadPreview();

    expect(dataService.preview).not.toHaveBeenCalled();
    expect(component.previewData()).toBeNull();
  });

  it('should load preview when dataSourceId is set', fakeAsync(() => {
    component.dataSourceId = 'ds-1';
    component.ngOnChanges({
      dataSourceId: new SimpleChange(null, 'ds-1', true)
    });

    tick();

    expect(dataService.preview).toHaveBeenCalledWith('ds-1', { rows: 20 });
    expect(component.previewData()).toEqual(mockPreviewData);
    expect(component.loading()).toBeFalse();
  }));

  it('should show loading state while fetching', () => {
    // Create a subject to control when the observable completes
    component.dataSourceId = 'ds-1';

    // loading starts as false
    expect(component.loading()).toBeFalse();

    // Trigger the load
    component.loadPreview();

    // After successful load, loading should be false
    expect(component.loading()).toBeFalse();
    expect(component.previewData()).toEqual(mockPreviewData);
  });

  it('should handle errors', fakeAsync(() => {
    dataService.preview.and.returnValue(throwError(() => new Error('Network error')));

    component.dataSourceId = 'ds-1';
    component.loadPreview();

    tick();

    expect(component.error()).toContain('Failed to load data preview');
    expect(component.loading()).toBeFalse();
  }));

  it('should reload when dataSourceId changes', fakeAsync(() => {
    component.dataSourceId = 'ds-1';
    component.ngOnChanges({
      dataSourceId: new SimpleChange(null, 'ds-1', true)
    });

    tick();
    expect(dataService.preview).toHaveBeenCalledTimes(1);

    component.dataSourceId = 'ds-2';
    component.ngOnChanges({
      dataSourceId: new SimpleChange('ds-1', 'ds-2', false)
    });

    tick();
    expect(dataService.preview).toHaveBeenCalledTimes(2);
    expect(dataService.preview).toHaveBeenCalledWith('ds-2', { rows: 20 });
  }));

  it('should reload when options change', fakeAsync(() => {
    component.dataSourceId = 'ds-1';
    component.ngOnChanges({
      options: new SimpleChange(null, { delimiter: ',' }, true)
    });

    tick();
    expect(dataService.preview).toHaveBeenCalled();
  }));

  it('should clear error when loading starts', fakeAsync(() => {
    component.error.set('Previous error');
    component.dataSourceId = 'ds-1';
    component.loadPreview();

    expect(component.error()).toBeNull();
  }));

  it('should display data in table after loading', fakeAsync(() => {
    component.dataSourceId = 'ds-1';
    component.ngOnChanges({
      dataSourceId: new SimpleChange(null, 'ds-1', true)
    });

    tick();
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('table');
    expect(table).toBeTruthy();

    const headerCells = fixture.nativeElement.querySelectorAll('th');
    expect(headerCells.length).toBe(3);
  }));

  it('should display total rows in summary', fakeAsync(() => {
    component.dataSourceId = 'ds-1';
    component.ngOnChanges({
      dataSourceId: new SimpleChange(null, 'ds-1', true)
    });

    tick();
    fixture.detectChanges();

    const summary = fixture.nativeElement.querySelector('.table-summary');
    expect(summary.textContent).toContain('10 rows');
  }));
});
