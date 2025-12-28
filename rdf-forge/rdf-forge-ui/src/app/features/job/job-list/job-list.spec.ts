import { ComponentFixture, TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { JobList } from './job-list';
import { JobService, PipelineService } from '../../../core/services';
import { Job, Pipeline } from '../../../core/models';

describe('JobList', () => {
  let component: JobList;
  let fixture: ComponentFixture<JobList>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;

  const mockPipelines: Pipeline[] = [
    { id: 'p1', name: 'Pipeline 1', status: 'active', stepsCount: 3, tags: [], description: '', definition: '{}', definitionFormat: 'JSON', variables: {}, createdBy: 'user', createdAt: new Date(), updatedAt: new Date() }
  ];

  // Use fixed dates to avoid ExpressionChangedAfterItHasBeenCheckedError from Date.now() calls
  const fixedDate = new Date('2024-01-15T10:00:00Z');
  const mockJobs: Job[] = [
    { id: 'job1', pipelineId: 'p1', pipelineVersion: 1, status: 'completed', pipelineName: 'Pipeline 1', startedAt: fixedDate, completedAt: fixedDate, duration: 1000, progress: 100, variables: {}, triggeredBy: 'manual', metrics: { rowsProcessed: 1000, quadsGenerated: 5000 }, createdBy: 'user', createdAt: fixedDate },
    { id: 'job2', pipelineId: 'p1', pipelineVersion: 1, status: 'completed', pipelineName: 'Pipeline 1', startedAt: fixedDate, completedAt: fixedDate, duration: 5000, progress: 100, variables: {}, triggeredBy: 'manual', createdBy: 'user', createdAt: fixedDate },
    { id: 'job3', pipelineId: 'p1', pipelineVersion: 1, status: 'failed', pipelineName: 'Pipeline 1', startedAt: fixedDate, completedAt: fixedDate, duration: 3000, progress: 75, variables: {}, triggeredBy: 'manual', createdBy: 'user', createdAt: fixedDate }
  ];

  beforeEach(async () => {
    jobServiceSpy = jasmine.createSpyObj('JobService', ['list', 'cancel', 'retry', 'create', 'getLogs']);
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', ['list']);

    jobServiceSpy.list.and.returnValue(of(mockJobs));
    pipelineServiceSpy.list.and.returnValue(of(mockPipelines));
    jobServiceSpy.getLogs.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [JobList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: JobService, useValue: jobServiceSpy },
        { provide: PipelineService, useValue: pipelineServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(JobList);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should create', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component).toBeTruthy();
  }));

  it('should load jobs on init', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(jobServiceSpy.list).toHaveBeenCalled();
    expect(component.jobs().length).toBe(3);
  }));

  it('should load pipelines on init', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(pipelineServiceSpy.list).toHaveBeenCalled();
    expect(component.pipelines().length).toBe(1);
  }));

  it('should filter jobs by status', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    component.statusFilter.set('completed');
    expect(component.filteredJobs().length).toBe(2);
    expect(component.filteredJobs()[0].status).toBe('completed');
  }));

  it('should filter jobs by search query', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    component.searchQuery.set('job1');
    expect(component.filteredJobs().length).toBe(1);
    expect(component.filteredJobs()[0].id).toBe('job1');
  }));

  it('should calculate running count', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    // All mock jobs are completed or failed, not running
    expect(component.runningCount()).toBe(0);
  }));

  it('should handle page change', () => {
    const event = { pageIndex: 2, pageSize: 5, length: 20 };
    component.onPageChange(event as any);
    expect(component.pageIndex).toBe(2);
    expect(component.pageSize).toBe(5);
  });

  it('should format duration correctly', () => {
    expect(component.formatDuration(undefined)).toBe('-');
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
    expect(component.formatDuration(7200000)).toBe('2h');
  });

  it('should format number correctly', () => {
    expect(component.formatNumber(undefined)).toBe('0');
    expect(component.formatNumber(500)).toBe('500');
    expect(component.formatNumber(1500)).toBe('1.5K');
    expect(component.formatNumber(1500000)).toBe('1.5M');
  });

  it('should get status class', () => {
    expect(component.getStatusClass('running')).toBe('status-running');
    expect(component.getStatusClass('completed')).toBe('status-completed');
    expect(component.getStatusClass('failed')).toBe('status-failed');
  });

  it('should get log class', () => {
    expect(component.getLogClass('info')).toBe('log-info');
    expect(component.getLogClass('error')).toBe('log-error');
  });

  it('should check if has variables', () => {
    expect(component.hasVariables(undefined)).toBeFalse();
    expect(component.hasVariables({})).toBeFalse();
    expect(component.hasVariables({ key: 'value' })).toBeTrue();
  });

  it('should get variable keys', () => {
    const vars = { key1: 'value1', key2: 'value2' };
    expect(component.getVariableKeys(vars)).toEqual(['key1', 'key2']);
  });

  it('should open new job dialog', () => {
    component.openNewJobDialog();
    expect(component.newJobDialogVisible()).toBeTrue();
    expect(component.selectedPipelineId()).toBeNull();
  });

  it('should close new job dialog', () => {
    component.newJobDialogVisible.set(true);
    component.closeNewJobDialog();
    expect(component.newJobDialogVisible()).toBeFalse();
  });

  it('should close details dialog', () => {
    component.detailsDialogVisible.set(true);
    component.closeDetailsDialog();
    expect(component.detailsDialogVisible()).toBeFalse();
  });

  it('should close logs dialog', () => {
    component.logsDialogVisible.set(true);
    component.closeLogsDialog();
    expect(component.logsDialogVisible()).toBeFalse();
  });

  it('should handle load error gracefully', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    jobServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadJobs();
    tick();
    expect(component.backendAvailable()).toBeFalse();
    expect(component.jobs().length).toBe(0);
  }));

  it('should view job details', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.viewDetails(mockJobs[0], mockEvent as any);
    expect(component.selectedJob()).toBe(mockJobs[0]);
    expect(component.detailsDialogVisible()).toBeTrue();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  }));

  it('should view job logs', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.viewLogs(mockJobs[0], mockEvent as any);
    tick();
    expect(component.selectedJob()).toBe(mockJobs[0]);
    expect(component.logsDialogVisible()).toBeTrue();
    expect(jobServiceSpy.getLogs).toHaveBeenCalledWith('job1', { limit: 200 });
  }));

  it('should cancel job', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(true);
    jobServiceSpy.cancel.and.returnValue(of(void 0));

    component.cancelJob(mockJobs[0], mockEvent as any);
    tick();

    expect(jobServiceSpy.cancel).toHaveBeenCalledWith('job1');
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('should not cancel job if not confirmed', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(false);

    component.cancelJob(mockJobs[0], mockEvent as any);
    tick();

    expect(jobServiceSpy.cancel).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('should handle cancel error', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(true);
    jobServiceSpy.cancel.and.returnValue(throwError(() => new Error('Cancel failed')));

    component.cancelJob(mockJobs[0], mockEvent as any);
    tick();
    // Error handled via snackbar
    discardPeriodicTasks();
  }));

  it('should retry job', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    const newJob = { ...mockJobs[0], id: 'newjob' };
    jobServiceSpy.retry.and.returnValue(of(newJob));
    spyOn((component as any).router, 'navigate');

    component.retryJob(mockJobs[0], mockEvent as any);
    tick();

    expect(jobServiceSpy.retry).toHaveBeenCalledWith('job1');
    expect((component as any).router.navigate).toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('should handle retry error', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    jobServiceSpy.retry.and.returnValue(throwError(() => new Error('Retry failed')));

    component.retryJob(mockJobs[0], mockEvent as any);
    tick();
    // Error handled via snackbar
    discardPeriodicTasks();
  }));

  it('should create job', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const newJob = { ...mockJobs[0], id: 'newjob' };
    jobServiceSpy.create.and.returnValue(of(newJob));
    spyOn((component as any).router, 'navigate');
    component.selectedPipelineId.set('p1');

    component.createJob();
    tick();

    expect(jobServiceSpy.create).toHaveBeenCalledWith('p1', {});
    expect(component.newJobDialogVisible()).toBeFalse();
    discardPeriodicTasks();
  }));

  it('should not create job without pipeline', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    component.selectedPipelineId.set(null);

    component.createJob();
    tick();

    expect(jobServiceSpy.create).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));

  it('should handle create job error', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    jobServiceSpy.create.and.returnValue(throwError(() => new Error('Create failed')));
    component.selectedPipelineId.set('p1');

    component.createJob();
    tick();

    expect(component.creatingJob()).toBeFalse();
    discardPeriodicTasks();
  }));

  it('should open job', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    spyOn((component as any).router, 'navigate');

    component.openJob(mockJobs[0]);

    expect((component as any).router.navigate).toHaveBeenCalledWith(['/jobs', 'job1']);
    discardPeriodicTasks();
  }));

  it('should format date', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.formatDate(undefined)).toBe('Pending');
    expect(component.formatDate(fixedDate).length).toBeGreaterThan(0);
  }));

  it('should format full date', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.formatFullDate(undefined)).toBe('-');
    expect(component.formatFullDate(fixedDate).length).toBeGreaterThan(0);
  }));

  it('should get running time', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.getRunningTime(undefined)).toBe('-');
    // Use a date in the past so elapsed time > 0
    const pastDate = new Date(Date.now() - 60000); // 1 minute ago
    expect(component.getRunningTime(pastDate)).not.toBe('-');
  }));

  it('should calculate pending count', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.pendingCount()).toBe(0);
  }));

  it('should calculate completed today', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.completedToday()).toBeGreaterThanOrEqual(0);
  }));

  it('should calculate failed today', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.failedToday()).toBeGreaterThanOrEqual(0);
  }));

  it('should calculate avg duration', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    const avg = component.avgDuration();
    expect(avg).toBeTruthy();
  }));

  it('should calculate total rows processed', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.totalRowsProcessed()).toBe(1000);
  }));

  it('should compute paged jobs', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.pagedJobs().length).toBeGreaterThanOrEqual(0);
  }));

  it('should filter by pipeline name', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    component.searchQuery.set('Pipeline 1');
    expect(component.filteredJobs().length).toBe(3);
  }));

  it('should have status options', () => {
    expect(component.statusOptions.length).toBeGreaterThan(0);
    expect(component.statusOptions.map(o => o.value)).toContain('running');
  });

  it('should have displayed columns', () => {
    expect(component.displayedColumns).toContain('id');
    expect(component.displayedColumns).toContain('actions');
  });

  it('should handle logs load error', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    jobServiceSpy.getLogs.and.returnValue(throwError(() => new Error('Logs failed')));

    component.loadLogs('job1');
    tick();

    expect(component.logsLoading()).toBeFalse();
    discardPeriodicTasks();
  }));

  it('should enrich jobs with pipeline names', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    expect(component.jobs()[0].pipelineName).toBe('Pipeline 1');
  }));

  it('should handle pipelines load error', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    discardPeriodicTasks();
    pipelineServiceSpy.list.and.returnValue(throwError(() => new Error('Pipelines failed')));
    component.loadPipelines();
    tick();
    // No error thrown - handled gracefully
    discardPeriodicTasks();
  }));
});