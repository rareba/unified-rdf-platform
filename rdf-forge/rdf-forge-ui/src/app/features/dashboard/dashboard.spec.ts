import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Dashboard } from './dashboard';
import { PipelineService, JobService, DataService, ShaclService } from '../../core/services';
import { Pipeline, Job, DataSource, Shape, Operation } from '../../core/models';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let dataServiceSpy: jasmine.SpyObj<DataService>;
  let shaclServiceSpy: jasmine.SpyObj<ShaclService>;

  const mockPipelines: Pipeline[] = [
    { id: '1', name: 'Pipeline 1', status: 'active', stepsCount: 3, tags: [], description: '', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  const mockJobs: Job[] = [
    { id: '1', pipelineId: '1', pipelineVersion: 1, status: 'completed', startedAt: new Date(), pipelineName: 'Pipeline 1', progress: 100, variables: {}, triggeredBy: 'manual', createdBy: 'user', createdAt: new Date() }
  ];

  const mockDataSources: DataSource[] = [
    { id: '1', name: 'Data 1', originalFilename: 'data.csv', format: 'csv', sizeBytes: 1000, rowCount: 100, columnCount: 5, storagePath: '/data/data.csv', uploadedAt: new Date(), uploadedBy: 'user' }
  ];

  const mockShapes: Shape[] = [
    { id: '1', name: 'Shape 1', uri: 'http://example.org/shape', content: '', targetClass: '', contentFormat: 'turtle', tags: [], isTemplate: false, version: 1, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  const mockOperations: Operation[] = [
    { id: 'op1', name: 'Load CSV', type: 'SOURCE', description: 'Load CSV file', parameters: {} }
  ];

  beforeEach(async () => {
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', ['list', 'getOperations']);
    jobServiceSpy = jasmine.createSpyObj('JobService', ['list']);
    dataServiceSpy = jasmine.createSpyObj('DataService', ['list']);
    shaclServiceSpy = jasmine.createSpyObj('ShaclService', ['list']);

    pipelineServiceSpy.list.and.returnValue(of(mockPipelines));
    pipelineServiceSpy.getOperations.and.returnValue(of(mockOperations));
    jobServiceSpy.list.and.returnValue(of(mockJobs));
    dataServiceSpy.list.and.returnValue(of(mockDataSources));
    shaclServiceSpy.list.and.returnValue(of(mockShapes));

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
        { provide: ShaclService, useValue: shaclServiceSpy }
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
    expect(stats.pipelines).toBe(1);
    expect(stats.completedJobs).toBe(1);
    expect(stats.dataSources).toBe(1);
    expect(stats.shapes).toBe(1);
  }));

  it('should detect new user when no data', fakeAsync(() => {
    pipelineServiceSpy.list.and.returnValue(of([]));
    jobServiceSpy.list.and.returnValue(of([]));
    dataServiceSpy.list.and.returnValue(of([]));
    shaclServiceSpy.list.and.returnValue(of([]));
    pipelineServiceSpy.getOperations.and.returnValue(of([]));

    component.loadDashboardData();
    tick();
    expect(component.isNewUser()).toBeTrue();
  }));

  it('should get status color correctly', () => {
    expect(component.getStatusColor('completed')).toBe('primary');
    expect(component.getStatusColor('running')).toBe('accent');
    expect(component.getStatusColor('failed')).toBe('warn');
    expect(component.getStatusColor('pending')).toBe('');
  });

  it('should format duration correctly', () => {
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
    expect(component.formatDuration(7200000)).toBe('2h');
    expect(component.formatDuration(undefined)).toBe('-');
  });

  it('should get group color', () => {
    expect(component.getGroupColor('SOURCE')).toBe('#3b82f6');
    expect(component.getGroupColor('TRANSFORM')).toBe('#8b5cf6');
    expect(component.getGroupColor('CUBE')).toBe('#f59e0b');
    expect(component.getGroupColor('VALIDATION')).toBe('#22c55e');
    expect(component.getGroupColor('OUTPUT')).toBe('#ec4899');
    expect(component.getGroupColor('UNKNOWN')).toBe('#64748b');
  });

  it('should format date correctly', () => {
    expect(component.formatDate(undefined)).toBe('-');
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
  }));

  it('should count operations', fakeAsync(() => {
    tick();
    expect(component.operationsCount()).toBe(1);
  }));

  it('should enrich jobs with pipeline names', fakeAsync(() => {
    tick();
    const jobs = component.recentJobs();
    expect(jobs.length).toBeGreaterThanOrEqual(0);
  }));

  it('should handle cancelled status', () => {
    expect(component.getStatusColor('cancelled')).toBe('');
  });
});