import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { JobMonitor } from './job-monitor';
import { JobService } from '../../../core/services';
import { Job, JobLog } from '../../../core/models';

describe('JobMonitor', () => {
  let component: JobMonitor;
  let fixture: ComponentFixture<JobMonitor>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;

  const mockJob: Job = {
    id: 'job-1',
    pipelineId: 'p1',
    pipelineName: 'Test Pipeline',
    pipelineVersion: 1,
    status: 'running',
    startedAt: new Date(),
    progress: 50,
    variables: {},
    triggeredBy: 'manual',
    metrics: { rowsProcessed: 1000, quadsGenerated: 5000 },
    createdBy: 'user',
    createdAt: new Date()
  };

  const mockLogs: JobLog[] = [
    { id: 'log-1', timestamp: new Date(), level: 'info', message: 'Job started' },
    { id: 'log-2', timestamp: new Date(), level: 'info', message: 'Processing data' }
  ];

  beforeEach(async () => {
    jobServiceSpy = jasmine.createSpyObj('JobService', ['get', 'getLogs', 'cancel', 'retry']);
    jobServiceSpy.get.and.returnValue(of(mockJob));
    jobServiceSpy.getLogs.and.returnValue(of(mockLogs));

    await TestBed.configureTestingModule({
      imports: [JobMonitor],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([
          { path: 'jobs/:id', component: JobMonitor },
          { path: 'jobs', component: JobMonitor }
        ]),
        { provide: JobService, useValue: jobServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => 'job-1' } }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(JobMonitor);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });

  it('should load job on init', fakeAsync(() => {
    tick();
    expect(jobServiceSpy.get).toHaveBeenCalledWith('job-1');
    expect(component.job()).toBe(mockJob);
    expect(component.loading()).toBeFalse();
  }));

  it('should load logs on init', fakeAsync(() => {
    tick();
    expect(jobServiceSpy.getLogs).toHaveBeenCalledWith('job-1', { limit: 100 });
    expect(component.logs().length).toBe(2);
  }));

  it('should cancel job', fakeAsync(() => {
    jobServiceSpy.cancel.and.returnValue(of(void 0));
    tick();
    component.cancelJob();
    tick();
    expect(jobServiceSpy.cancel).toHaveBeenCalledWith('job-1');
  }));

  it('should retry job', fakeAsync(() => {
    tick();
    // Set a failed job
    const failedJob = { ...mockJob, id: 'job-1', status: 'failed' as const };
    component.job.set(failedJob);
    jobServiceSpy.retry.and.returnValue(of({ ...mockJob, id: 'job-2', status: 'pending' as const }));
    component.retryJob();
    tick();
    expect(jobServiceSpy.retry).toHaveBeenCalledWith('job-1');
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    jobServiceSpy.get.and.returnValue(throwError(() => new Error('Network error')));
    component.loadJob('job-1');
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should format duration', () => {
    expect(component.formatDuration(undefined)).toBe('-');
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
  });

  it('should get log class', () => {
    expect(component.getLogClass('info')).toBe('log-info');
    expect(component.getLogClass('error')).toBe('log-error');
    expect(component.getLogClass('warn')).toBe('log-warn');
  });

  it('should get status class', () => {
    expect(component.getStatusClass('running')).toBe('status-info');
    expect(component.getStatusClass('completed')).toBe('status-success');
    expect(component.getStatusClass('failed')).toBe('status-error');
    expect(component.getStatusClass('cancelled')).toBe('status-warn');
  });

  it('should format date', () => {
    expect(component.formatDate(undefined)).toBe('-');
    const date = new Date(2024, 0, 15, 10, 30);
    const formatted = component.formatDate(date);
    // Date uses toLocaleString which varies by locale, just check it's not '-'
    expect(formatted).not.toBe('-');
    expect(formatted.length).toBeGreaterThan(0);
  });

  it('should format duration for hours', () => {
    expect(component.formatDuration(3600000)).toBe('1h');
    expect(component.formatDuration(7200000)).toBe('2h');
  });

  it('should get status class for pending', () => {
    expect(component.getStatusClass('pending')).toBe('status-default');
  });

  it('should get log class for debug', () => {
    expect(component.getLogClass('debug')).toBe('log-debug');
  });

  it('should get log class for default', () => {
    expect(component.getLogClass('unknown')).toBe('log-info');
  });

  it('should navigate back', () => {
    spyOn((component as any).router, 'navigate');
    component.goBack();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/jobs']);
  });

  it('should handle cancel error', fakeAsync(() => {
    jobServiceSpy.cancel.and.returnValue(throwError(() => new Error('Cancel failed')));
    tick();
    component.cancelJob();
    tick();
    // Error handling shows snackbar
  }));

  it('should handle retry error', fakeAsync(() => {
    jobServiceSpy.retry.and.returnValue(throwError(() => new Error('Retry failed')));
    tick();
    component.retryJob();
    tick();
    // Error handling shows snackbar
  }));

  it('should not cancel if no job', fakeAsync(() => {
    tick();
    component.job.set(null);
    component.cancelJob();
    expect(jobServiceSpy.cancel).not.toHaveBeenCalled();
  }));

  it('should not retry if no job', fakeAsync(() => {
    tick();
    component.job.set(null);
    component.retryJob();
    expect(jobServiceSpy.retry).not.toHaveBeenCalled();
  }));
});
