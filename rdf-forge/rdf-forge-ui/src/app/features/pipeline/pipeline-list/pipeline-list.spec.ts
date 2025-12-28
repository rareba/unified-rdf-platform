import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PipelineList } from './pipeline-list';
import { PipelineService } from '../../../core/services';
import { Pipeline } from '../../../core/models';

describe('PipelineList', () => {
  let component: PipelineList;
  let fixture: ComponentFixture<PipelineList>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;

  const mockPipelines: Pipeline[] = [
    { id: '1', name: 'Pipeline A', status: 'active', stepsCount: 3, tags: ['tag1'], description: 'Description A', lastRun: new Date(), definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() },
    { id: '2', name: 'Pipeline B', status: 'draft', stepsCount: 2, tags: ['tag2'], description: 'Description B', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() },
    { id: '3', name: 'Pipeline C', status: 'archived', stepsCount: 0, tags: [], description: 'Description C', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  beforeEach(async () => {
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', ['list', 'run', 'duplicate', 'delete']);
    pipelineServiceSpy.list.and.returnValue(of(mockPipelines));

    await TestBed.configureTestingModule({
      imports: [PipelineList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([
          { path: 'jobs/:id', component: PipelineList },
          { path: 'jobs', component: PipelineList }
        ]),
        { provide: PipelineService, useValue: pipelineServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineList);
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

  it('should load pipelines on init', fakeAsync(() => {
    tick();
    expect(pipelineServiceSpy.list).toHaveBeenCalled();
    expect(component.pipelines().length).toBe(3);
    expect(component.loading()).toBeFalse();
  }));

  it('should filter pipelines by search query', fakeAsync(() => {
    tick();
    component.searchQuery.set('Pipeline A');
    expect(component.filteredPipelines().length).toBe(1);
    expect(component.filteredPipelines()[0].name).toBe('Pipeline A');
  }));

  it('should filter pipelines by status', fakeAsync(() => {
    tick();
    component.statusFilter.set('active');
    expect(component.filteredPipelines().length).toBe(1);
    expect(component.filteredPipelines()[0].status).toBe('active');
  }));

  it('should search in tags', fakeAsync(() => {
    tick();
    component.searchQuery.set('tag1');
    expect(component.filteredPipelines().length).toBe(1);
  }));

  it('should handle page change', () => {
    const event = { pageIndex: 2, pageSize: 5, length: 20 };
    component.onPageChange(event as any);
    expect(component.pageIndex()).toBe(2);
    expect(component.pageSize()).toBe(5);
  });

  it('should handle sort change', () => {
    component.onSortChange({ active: 'status', direction: 'desc' } as any);
    expect(component.sortField()).toBe('status');
    expect(component.sortDirection()).toBe('desc');
  });

  it('should get status class', () => {
    expect(component.getStatusClass('active')).toBe('status-active');
    expect(component.getStatusClass('draft')).toBe('status-draft');
    expect(component.getStatusClass('archived')).toBe('status-archived');
    expect(component.getStatusClass('unknown')).toBe('status-info');
  });

  it('should format date correctly', () => {
    expect(component.formatDate(undefined)).toBe('Never');
    const date = new Date(2024, 0, 15, 10, 30);
    const formatted = component.formatDate(date);
    expect(formatted).toContain('Jan');
    expect(formatted).toContain('15');
  });

  it('should handle load error gracefully', fakeAsync(() => {
    pipelineServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadPipelines();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should run pipeline', fakeAsync(() => {
    pipelineServiceSpy.run.and.returnValue(of({ jobId: 'job-1' }));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.runPipeline(mockPipelines[0], mockEvent as any);
    tick();
    expect(pipelineServiceSpy.run).toHaveBeenCalledWith('1');
  }));

  it('should duplicate pipeline', fakeAsync(() => {
    pipelineServiceSpy.duplicate.and.returnValue(of(mockPipelines[0]));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.duplicatePipeline(mockPipelines[0], mockEvent as any);
    tick();
    expect(pipelineServiceSpy.duplicate).toHaveBeenCalledWith('1');
  }));

  it('should create pipeline', () => {
    spyOn((component as any).router, 'navigate');
    component.createPipeline();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines/new']);
  });

  it('should open pipeline', () => {
    spyOn((component as any).router, 'navigate');
    component.openPipeline(mockPipelines[0]);
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines', '1']);
  });

  it('should handle run pipeline error', fakeAsync(() => {
    pipelineServiceSpy.run.and.returnValue(throwError(() => new Error('Run failed')));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.runPipeline(mockPipelines[0], mockEvent as any);
    tick();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should handle duplicate pipeline error', fakeAsync(() => {
    pipelineServiceSpy.duplicate.and.returnValue(throwError(() => new Error('Duplicate failed')));
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.duplicatePipeline(mockPipelines[0], mockEvent as any);
    tick();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should confirm delete pipeline', fakeAsync(() => {
    pipelineServiceSpy.delete.and.returnValue(of(void 0));
    spyOn(window, 'confirm').and.returnValue(true);
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.confirmDelete(mockPipelines[0], mockEvent as any);
    tick();
    expect(pipelineServiceSpy.delete).toHaveBeenCalledWith('1');
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should not delete pipeline if cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.confirmDelete(mockPipelines[0], mockEvent as any);
    expect(pipelineServiceSpy.delete).not.toHaveBeenCalled();
  });

  it('should handle delete pipeline error', fakeAsync(() => {
    pipelineServiceSpy.delete.and.returnValue(throwError(() => new Error('Delete failed')));
    component.deletePipeline(mockPipelines[0]);
    tick();
    expect(component.deleting()).toBeFalse();
  }));

  it('should compute sorted pipelines', fakeAsync(() => {
    tick();
    component.sortField.set('name');
    component.sortDirection.set('asc');
    const sorted = component.sortedPipelines();
    expect(sorted.length).toBe(3);
    expect(sorted[0].name).toBe('Pipeline A');
  }));

  it('should compute sorted pipelines descending', fakeAsync(() => {
    tick();
    component.sortField.set('name');
    component.sortDirection.set('desc');
    const sorted = component.sortedPipelines();
    expect(sorted[0].name).toBe('Pipeline C');
  }));

  it('should compute paginated pipelines', fakeAsync(() => {
    tick();
    component.pageSize.set(2);
    component.pageIndex.set(0);
    const paginated = component.paginatedPipelines();
    expect(paginated.length).toBe(2);
  }));

  it('should compute paginated pipelines page 2', fakeAsync(() => {
    tick();
    component.pageSize.set(2);
    component.pageIndex.set(1);
    const paginated = component.paginatedPipelines();
    expect(paginated.length).toBe(1);
  }));

  it('should handle empty sort direction', () => {
    component.onSortChange({ active: '', direction: '' } as any);
    expect(component.sortField()).toBe('name');
    expect(component.sortDirection()).toBe('asc');
  });

  it('should filter by description', fakeAsync(() => {
    tick();
    component.searchQuery.set('Description B');
    expect(component.filteredPipelines().length).toBe(1);
    expect(component.filteredPipelines()[0].id).toBe('2');
  }));

  it('should return all pipelines when no filter', fakeAsync(() => {
    tick();
    component.searchQuery.set('');
    component.statusFilter.set(null);
    expect(component.filteredPipelines().length).toBe(3);
  }));

  it('should have status options', () => {
    expect(component.statusOptions.length).toBe(4);
    expect(component.statusOptions.map(o => o.value)).toContain('active');
    expect(component.statusOptions.map(o => o.value)).toContain('draft');
  });

  it('should have displayed columns', () => {
    expect(component.displayedColumns).toContain('name');
    expect(component.displayedColumns).toContain('status');
    expect(component.displayedColumns).toContain('actions');
  });
});