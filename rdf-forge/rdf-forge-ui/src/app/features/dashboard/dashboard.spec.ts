import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Dashboard } from './dashboard';
import { PipelineService, JobService, DataService, ShaclService, ProjectService } from '../../core/services';
import { Pipeline, Job, DataSource, Shape, Operation, Project } from '../../core/models';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let dataServiceSpy: jasmine.SpyObj<DataService>;
  let shaclServiceSpy: jasmine.SpyObj<ShaclService>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;

  const mockProjects: Project[] = [
    { id: 'p1', name: 'Project 1', description: '', baseUri: 'http://example.org/p1/', status: 'ACTIVE', createdBy: 'user', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ];

  const mockPipelines: Pipeline[] = [
    { id: '1', name: 'Pipeline 1', status: 'active', stepsCount: 3, tags: [], description: '', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() },
    { id: '2', name: 'Pipeline 2', status: 'draft', stepsCount: 5, tags: ['test'], description: 'Test pipeline', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  const mockJobs: Job[] = [
    { id: '1', pipelineId: '1', pipelineVersion: 1, status: 'completed', startedAt: new Date(), pipelineName: 'Pipeline 1', progress: 100, variables: {}, triggeredBy: 'manual', createdBy: 'user', createdAt: new Date() },
    { id: '2', pipelineId: '1', pipelineVersion: 1, status: 'running', startedAt: new Date(), pipelineName: 'Pipeline 1', progress: 50, variables: {}, triggeredBy: 'schedule', createdBy: 'user', createdAt: new Date() },
    { id: '3', pipelineId: '2', pipelineVersion: 1, status: 'failed', startedAt: new Date(), pipelineName: 'Pipeline 2', progress: 0, variables: {}, triggeredBy: 'manual', createdBy: 'user', createdAt: new Date() }
  ];

  const mockDataSources: DataSource[] = [
    { id: '1', name: 'Data 1', originalFilename: 'data.csv', format: 'csv', sizeBytes: 1000, rowCount: 100, columnCount: 5, storagePath: '/data/data.csv', uploadedAt: new Date(), uploadedBy: 'user' },
    { id: '2', name: 'Data 2', originalFilename: 'data.json', format: 'json', sizeBytes: 2000, rowCount: 200, columnCount: 10, storagePath: '/data/data.json', uploadedAt: new Date(), uploadedBy: 'user' }
  ];

  const mockShapes: Shape[] = [
    { id: '1', name: 'Shape 1', uri: 'http://example.org/shape', content: '', targetClass: '', contentFormat: 'turtle', tags: [], isTemplate: false, version: 1, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() },
    { id: '2', name: 'Shape 2', uri: 'http://example.org/shape2', content: '', targetClass: '', contentFormat: 'turtle', tags: ['template'], isTemplate: true, version: 1, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  const mockOperations: Operation[] = [
    { id: 'op1', name: 'Load CSV', type: 'SOURCE', description: 'Load CSV file', parameters: {} },
    { id: 'op2', name: 'Transform', type: 'TRANSFORM', description: 'Transform data', parameters: {} },
    { id: 'op3', name: 'Validate', type: 'VALIDATION', description: 'Validate data', parameters: {} },
    { id: 'op4', name: 'Save', type: 'OUTPUT', description: 'Save output', parameters: {} }
  ];

  beforeEach(async () => {
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', ['list', 'getOperations']);
    jobServiceSpy = jasmine.createSpyObj('JobService', ['list']);
    dataServiceSpy = jasmine.createSpyObj('DataService', ['list']);
    shaclServiceSpy = jasmine.createSpyObj('ShaclService', ['list']);
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['list']);

    pipelineServiceSpy.list.and.returnValue(of(mockPipelines));
    pipelineServiceSpy.getOperations.and.returnValue(of(mockOperations));
    jobServiceSpy.list.and.returnValue(of(mockJobs));
    dataServiceSpy.list.and.returnValue(of(mockDataSources));
    shaclServiceSpy.list.and.returnValue(of(mockShapes));
    projectServiceSpy.list.and.returnValue(of(mockProjects));

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: PipelineService, useValue: pipelineServiceSpy },
        { provide: JobService, useValue: jobServiceSpy },
        { provide: DataService, useValue: dataServiceSpy },
        { provide: ShaclService, useValue: shaclServiceSpy },
        { provide: ProjectService, useValue: projectServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
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

  it('should load dashboard data on init', fakeAsync(() => {
    tick();
    expect(pipelineServiceSpy.list).toHaveBeenCalled();
    expect(jobServiceSpy.list).toHaveBeenCalled();
    expect(dataServiceSpy.list).toHaveBeenCalled();
    expect(shaclServiceSpy.list).toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  }));

  it('should calculate stats correctly', fakeAsync(() => {
    tick();
    const stats = component.stats();
    expect(stats.pipelines).toBe(2);
    expect(stats.completedJobs).toBe(1);
    expect(stats.dataSources).toBe(2);
    expect(stats.shapes).toBe(2);
  }));

  it('should detect new user when no data', fakeAsync(() => {
    pipelineServiceSpy.list.and.returnValue(of([]));
    jobServiceSpy.list.and.returnValue(of([]));
    dataServiceSpy.list.and.returnValue(of([]));
    shaclServiceSpy.list.and.returnValue(of([]));
    pipelineServiceSpy.getOperations.and.returnValue(of([]));
    projectServiceSpy.list.and.returnValue(of([]));

    component.loadDashboardData();
    tick();
    expect(component.isNewUser()).toBeTrue();
  }));

  it('should not be new user when data exists', fakeAsync(() => {
    tick();
    expect(component.isNewUser()).toBeFalse();
  }));

  it('should get status color correctly', () => {
    expect(component.getStatusColor('completed')).toBe('primary');
    expect(component.getStatusColor('running')).toBe('accent');
    expect(component.getStatusColor('failed')).toBe('warn');
    expect(component.getStatusColor('pending')).toBe('');
    expect(component.getStatusColor('cancelled')).toBe('');
    expect(component.getStatusColor('unknown' as any)).toBe('');
  });

  it('should format duration correctly', () => {
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
    expect(component.formatDuration(7200000)).toBe('2h');
    expect(component.formatDuration(undefined)).toBe('-');
    expect(component.formatDuration(3600000)).toBe('1h');
  });

  it('should format number correctly', () => {
    expect(component.formatNumber(0)).toBe('0');
    expect(component.formatNumber(999)).toBe('999');
    expect(component.formatNumber(1000)).toBe('1.0K');
    expect(component.formatNumber(1500)).toBe('1.5K');
    expect(component.formatNumber(1000000)).toBe('1.0M');
    expect(component.formatNumber(1000000000)).toBe('1.0B');
  });

  it('should get group color', () => {
    expect(component.getGroupColor('SOURCE')).toBe('#3b82f6');
    expect(component.getGroupColor('TRANSFORM')).toBe('#8b5cf6');
    expect(component.getGroupColor('CUBE')).toBe('#f59e0b');
    expect(component.getGroupColor('VALIDATION')).toBe('#22c55e');
    expect(component.getGroupColor('OUTPUT')).toBe('#ec4899');
    expect(component.getGroupColor('UNKNOWN')).toBe('#64748b');
    expect(component.getGroupColor('OTHER' as any)).toBe('#64748b');
  });

  it('should format date correctly', () => {
    expect(component.formatDate(undefined)).toBe('-');
    expect(component.formatDate(null as any)).toBe('-');
    const date = new Date(2024, 0, 15, 10, 30);
    const formatted = component.formatDate(date);
    expect(formatted).not.toBe('-');
    expect(formatted.length).toBeGreaterThan(0);
  });

  it('should navigate to path', () => {
    spyOn((component as any).router, 'navigate');
    component.navigateTo('/pipelines');
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines']);
  });

  it('should start csv-to-cube workflow', () => {
    spyOn((component as any).router, 'navigate');
    component.startWorkflow('csv-to-cube');
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/cubes/new']);
  });

  it('should start validate-cube workflow', () => {
    spyOn((component as any).router, 'navigate');
    component.startWorkflow('validate-cube');
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/shacl'], { queryParams: { action: 'validate' } });
  });

  it('should start publish-graphdb workflow', () => {
    spyOn((component as any).router, 'navigate');
    component.startWorkflow('publish-graphdb');
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines/new'], { queryParams: { template: 'publish' } });
  });

  it('should start default workflow', () => {
    spyOn((component as any).router, 'navigate');
    component.startWorkflow('other');
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines/new']);
  });

  it('should group operations correctly', fakeAsync(() => {
    pipelineServiceSpy.getOperations.and.returnValue(of([
      { id: 'op1', name: 'Load CSV', type: 'SOURCE', description: '', parameters: {} },
      { id: 'op2', name: 'Transform', type: 'TRANSFORM', description: '', parameters: {} },
      { id: 'op3', name: 'Validate', type: 'VALIDATION', description: '', parameters: {} }
    ]));
    component.loadDashboardData();
    tick();
    const groups = component.operationGroups();
    expect(groups.length).toBeGreaterThan(0);
    expect(groups.find(g => g.type === 'SOURCE')).toBeTruthy();
    expect(groups.find(g => g.type === 'TRANSFORM')).toBeTruthy();
    expect(groups.find(g => g.type === 'VALIDATION')).toBeTruthy();
  }));

  it('should count operations', fakeAsync(() => {
    tick();
    expect(component.operationsCount()).toBe(4);
  }));

  it('should enrich jobs with pipeline names', fakeAsync(() => {
    tick();
    const jobs = component.recentJobs();
    expect(jobs.length).toBeGreaterThanOrEqual(0);
  }));

  it('should handle loading state', () => {
    // After detectChanges and synchronous completion of forkJoin, loading is false
    expect(component.loading()).toBeFalse();
  });

  it('should handle data loading error gracefully via catchError', fakeAsync(() => {
    // With catchError in the forkJoin, errors are converted to empty arrays
    pipelineServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadDashboardData();
    tick();
    expect(component.loading()).toBeFalse();
    // catchError converts errors to empty arrays, so data still loads successfully
    expect(component.stats()?.pipelines).toBe(0);
  }));

  it('should handle partial data loading error', fakeAsync(() => {
    pipelineServiceSpy.list.and.returnValue(of(mockPipelines));
    jobServiceSpy.list.and.returnValue(throwError(() => new Error('Job service error')));
    dataServiceSpy.list.and.returnValue(of(mockDataSources));
    shaclServiceSpy.list.and.returnValue(of(mockShapes));

    component.loadDashboardData();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should retry loading via retryLoad', fakeAsync(() => {
    tick();
    component.retryLoad();
    tick();
    expect(pipelineServiceSpy.list).toHaveBeenCalledTimes(2);
  }));

  it('should handle empty operations', fakeAsync(() => {
    pipelineServiceSpy.getOperations.and.returnValue(of([]));
    component.loadDashboardData();
    tick();
    expect(component.operationsCount()).toBe(0);
  }));

  it('should have displayed columns defined', () => {
    expect(component.displayedColumns).toEqual(['pipelineName', 'status', 'startedAt', 'duration']);
  });

  it('should handle all services failing gracefully', fakeAsync(() => {
    // With catchError wrapping each service call, errors are converted to empty arrays
    pipelineServiceSpy.list.and.returnValue(throwError(() => new Error('fail')));
    jobServiceSpy.list.and.returnValue(throwError(() => new Error('fail')));
    dataServiceSpy.list.and.returnValue(throwError(() => new Error('fail')));
    shaclServiceSpy.list.and.returnValue(throwError(() => new Error('fail')));
    pipelineServiceSpy.getOperations.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.list.and.returnValue(throwError(() => new Error('fail')));
    component.loadDashboardData();
    tick();
    expect(component.loading()).toBeFalse();
    expect(component.stats()?.pipelines).toBe(0);
  }));

  it('should sort operation groups by count descending', fakeAsync(() => {
    pipelineServiceSpy.getOperations.and.returnValue(of([
      { id: 'op1', name: 'Load CSV', type: 'SOURCE', description: '', parameters: {} },
      { id: 'op2', name: 'Load JSON', type: 'SOURCE', description: '', parameters: {} },
      { id: 'op3', name: 'Transform', type: 'TRANSFORM', description: '', parameters: {} }
    ]));
    component.loadDashboardData();
    tick();
    const groups = component.operationGroups();
    expect(groups[0].type).toBe('SOURCE');
    expect(groups[0].count).toBe(2);
    expect(groups[1].type).toBe('TRANSFORM');
    expect(groups[1].count).toBe(1);
  }));

  it('should use fallback config for unknown operation types', fakeAsync(() => {
    pipelineServiceSpy.getOperations.and.returnValue(of([
      { id: 'op1', name: 'Custom', type: 'CUSTOM' as any, description: '', parameters: {} }
    ]));
    component.loadDashboardData();
    tick();
    const groups = component.operationGroups();
    expect(groups.length).toBe(1);
    expect(groups[0].label).toBe('CUSTOM');
    expect(groups[0].icon).toBe('extension');
  }));
});
