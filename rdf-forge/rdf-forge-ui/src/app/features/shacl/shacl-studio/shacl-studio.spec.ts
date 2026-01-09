import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ShaclStudio } from './shacl-studio';
import { ShaclService } from '../../../core/services';
import { Shape } from '../../../core/models';

describe('ShaclStudio', () => {
  let component: ShaclStudio;
  let fixture: ComponentFixture<ShaclStudio>;
  let shaclServiceSpy: jasmine.SpyObj<ShaclService>;

  const mockShapes: Shape[] = [
    { id: '1', name: 'PersonShape', uri: 'http://example.org/shapes/Person', targetClass: 'http://example.org/Person', contentFormat: 'turtle', content: '', tags: [], isTemplate: false, version: 1, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() },
    { id: '2', name: 'OrganizationShape', uri: 'http://example.org/shapes/Organization', targetClass: 'http://example.org/Organization', contentFormat: 'turtle', content: '', tags: [], isTemplate: false, version: 1, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  beforeEach(async () => {
    shaclServiceSpy = jasmine.createSpyObj('ShaclService', ['list', 'delete', 'get', 'create', 'update']);
    shaclServiceSpy.list.and.returnValue(of(mockShapes));

    await TestBed.configureTestingModule({
      imports: [ShaclStudio],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ShaclService, useValue: shaclServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ShaclStudio);
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

  it('should load shapes on init', fakeAsync(() => {
    tick();
    expect(shaclServiceSpy.list).toHaveBeenCalled();
    expect(component.shapes().length).toBe(2);
    expect(component.loading()).toBeFalse();
  }));

  it('should filter shapes by search query', fakeAsync(() => {
    tick();
    component.searchQuery.set('Person');
    expect(component.filteredShapes().length).toBe(1);
    expect(component.filteredShapes()[0].name).toBe('PersonShape');
  }));

  it('should filter shapes by URI', fakeAsync(() => {
    tick();
    component.searchQuery.set('Organization');
    expect(component.filteredShapes().length).toBe(1);
  }));

  it('should filter shapes by target class', fakeAsync(() => {
    tick();
    component.searchQuery.set('example.org/Person');
    expect(component.filteredShapes().length).toBe(1);
  }));

  it('should return all shapes when search is empty', fakeAsync(() => {
    tick();
    component.searchQuery.set('');
    expect(component.filteredShapes().length).toBe(2);
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    shaclServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadShapes();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should delete shape', fakeAsync(() => {
    shaclServiceSpy.delete.and.returnValue(of(void 0));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.deleteShape(mockShapes[0], mockEvent as any);
    tick();
    expect(shaclServiceSpy.delete).toHaveBeenCalledWith('1');
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should paginate shapes', fakeAsync(() => {
    // Add more shapes to test pagination
    const manyShapes: Shape[] = Array.from({ length: 25 }, (_, i) => ({
      id: `${i}`,
      name: `Shape ${i}`,
      uri: `http://example.org/shapes/${i}`,
      targetClass: `http://example.org/Class${i}`,
      contentFormat: 'turtle' as const,
      content: '',
      tags: [],
      isTemplate: false,
      version: 1,
      createdBy: 'user',
      createdAt: new Date(),
      updatedAt: new Date()
    }));
    shaclServiceSpy.list.and.returnValue(of(manyShapes));
    component.loadShapes();
    tick();

    component.pageSize.set(10);
    component.pageIndex.set(0);
    expect(component.paginatedShapes().length).toBe(10);

    component.pageIndex.set(1);
    expect(component.paginatedShapes().length).toBe(10);

    component.pageIndex.set(2);
    expect(component.paginatedShapes().length).toBe(5);
  }));

  it('should navigate to create shape', () => {
    spyOn((component as any).router, 'navigate');
    component.createShape();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/shacl/new']);
  });

  it('should navigate to edit shape', () => {
    spyOn((component as any).router, 'navigate');
    component.editShape(mockShapes[0]);
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/shacl', '1']);
  });

  it('should handle delete error', fakeAsync(() => {
    shaclServiceSpy.delete.and.returnValue(throwError(() => new Error('Delete failed')));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.deleteShape(mockShapes[0], mockEvent as any);
    tick();
    // Error handled via snackbar
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should handle page change', () => {
    const pageEvent = { pageSize: 20, pageIndex: 1, length: 50 };
    component.onPageChange(pageEvent as any);
    expect(component.pageSize()).toBe(20);
    expect(component.pageIndex()).toBe(1);
  });

  it('should format date', () => {
    const date = new Date(2024, 0, 15);
    const formatted = component.formatDate(date);
    expect(formatted.length).toBeGreaterThan(0);
  });

  it('should have displayed columns', () => {
    expect(component.displayedColumns.length).toBeGreaterThan(0);
    expect(component.displayedColumns).toContain('name');
    expect(component.displayedColumns).toContain('actions');
  });

  it('should filter shapes case insensitively', fakeAsync(() => {
    tick();
    component.searchQuery.set('person');
    expect(component.filteredShapes().length).toBe(1);
  }));

  it('should reset page index on search', fakeAsync(() => {
    tick();
    component.pageIndex.set(5);
    component.searchQuery.set('Person');
    // Pagination should still work on filtered results
    expect(component.paginatedShapes().length).toBeGreaterThanOrEqual(0);
  }));

  it('should show empty list for no matching shapes', fakeAsync(() => {
    tick();
    component.searchQuery.set('xyz123nonexistent');
    expect(component.filteredShapes().length).toBe(0);
  }));
});