import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { JobMonitor } from './job-monitor';
import { JobService, LogStreamMessage, ConnectionStatus } from '../../../core/services/job.service';
import { Job, JobLog } from '../../../core/models';

describe('JobMonitor', () => {
  let component: JobMonitor;
  let fixture: ComponentFixture<JobMonitor>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let logStreamSubject: Subject<LogStreamMessage>;
  let connectionStatusSubject: Subject<ConnectionStatus>;

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

  const mockCompletedJob: Job = {
    ...mockJob,
    status: 'completed',
    completedAt: new Date(),
    progress: 100
  };

  const mockFailedJob: Job = {
    ...mockJob,
    status: 'failed',
    errorMessage: 'Processing failed',
    progress: 75
  };

  beforeEach(async () => {
    logStreamSubject = new Subject<LogStreamMessage>();
    connectionStatusSubject = new Subject<ConnectionStatus>();

    jobServiceSpy = jasmine.createSpyObj('JobService', ['get', 'getLogs', 'cancel', 'retry', 'connectToJobLogs', 'disconnect']);

    // Mock the observables
    Object.defineProperty(jobServiceSpy, 'logStream$', {
      get: () => logStreamSubject.asObservable()
    });
    Object.defineProperty(jobServiceSpy, 'connectionStatus$', {
      get: () => connectionStatusSubject.asObservable()
    });

    jobServiceSpy.get.and.returnValue(of(mockJob));
    jobServiceSpy.getLogs.and.returnValue(of([]));

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
    logStreamSubject.complete();
    connectionStatusSubject.complete();
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

  it('should connect to WebSocket on init', fakeAsync(() => {
    tick();
    expect(jobServiceSpy.connectToJobLogs).toHaveBeenCalledWith('job-1');
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
    jobServiceSpy.retry.and.returnValue(of({ ...mockJob, id: 'job-2', status: 'pending' as const }));
    component.retryJob();
    tick();
    expect(jobServiceSpy.retry).toHaveBeenCalledWith('job-1');
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

  it('should format duration', () => {
    expect(component.formatDuration(undefined)).toBe('-');
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
    expect(component.formatDuration(3600000)).toBe('1h');
  });

  it('should get log class', () => {
    expect(component.getLogClass('info')).toBe('log-info');
    expect(component.getLogClass('error')).toBe('log-error');
    expect(component.getLogClass('warn')).toBe('log-warn');
    expect(component.getLogClass('debug')).toBe('log-debug');
    expect(component.getLogClass('unknown' as any)).toBe('log-info');
  });

  it('should get status class', () => {
    expect(component.getStatusClass('running')).toBe('status-info');
    expect(component.getStatusClass('completed')).toBe('status-success');
    expect(component.getStatusClass('failed')).toBe('status-error');
    expect(component.getStatusClass('cancelled')).toBe('status-warn');
    expect(component.getStatusClass('pending')).toBe('status-default');
    expect(component.getStatusClass('unknown' as any)).toBe('status-default');
  });

  it('should format date', () => {
    expect(component.formatDate(undefined)).toBe('-');
    const date = new Date(2024, 0, 15, 10, 30);
    const formatted = component.formatDate(date);
    expect(formatted).not.toBe('-');
    expect(formatted.length).toBeGreaterThan(0);
  });

  it('should navigate back', () => {
    spyOn((component as any).router, 'navigate');
    component.goBack();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/jobs']);
  });

  it('should get connection status class', () => {
    component.connectionStatus.set({ connected: true, reconnecting: false });
    expect(component.getConnectionStatusClass()).toBe('status-success');

    component.connectionStatus.set({ connected: false, reconnecting: true });
    expect(component.getConnectionStatusClass()).toBe('status-warn');

    component.connectionStatus.set({ connected: false, reconnecting: false });
    expect(component.getConnectionStatusClass()).toBe('status-error');
  });

  it('should get connection status icon', () => {
    component.connectionStatus.set({ connected: true, reconnecting: false });
    expect(component.getConnectionStatusIcon()).toBe('cloud_done');

    component.connectionStatus.set({ connected: false, reconnecting: true });
    expect(component.getConnectionStatusIcon()).toBe('sync');

    component.connectionStatus.set({ connected: false, reconnecting: false });
    expect(component.getConnectionStatusIcon()).toBe('cloud_off');
  });

  it('should get connection status text', () => {
    component.connectionStatus.set({ connected: true, reconnecting: false });
    expect(component.getConnectionStatusText()).toBe('Connected');

    component.connectionStatus.set({ connected: false, reconnecting: true });
    expect(component.getConnectionStatusText()).toBe('Reconnecting...');

    component.connectionStatus.set({ connected: false, reconnecting: false });
    expect(component.getConnectionStatusText()).toBe('Disconnected');
  });

  describe('WebSocket Log Streaming', () => {
    it('should receive log messages from WebSocket', fakeAsync(() => {
      tick();

      const logMessage: LogStreamMessage = {
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        step: 'process',
        message: 'New log from WebSocket'
      };

      logStreamSubject.next(logMessage);
      tick();

      expect(component.logs().length).toBe(1);
      expect(component.logs()[0].message).toBe('New log from WebSocket');
    }));

    it('should receive status updates from WebSocket', fakeAsync(() => {
      tick();

      const statusMessage: LogStreamMessage = {
        type: 'status',
        status: 'running',
        progress: 75
      };

      logStreamSubject.next(statusMessage);
      tick();

      expect(component.job()?.progress).toBe(75);
    }));

    it('should receive completion message from WebSocket', fakeAsync(() => {
      tick();

      // After completion, the component re-fetches the job via loadJob(),
      // so the spy must return a completed job on the next call
      jobServiceSpy.get.and.returnValue(of(mockCompletedJob));

      const completionMessage: LogStreamMessage = {
        type: 'completion',
        success: true
      };

      logStreamSubject.next(completionMessage);
      tick();

      expect(component.job()?.status).toBe('completed');
    }));

    it('should update connection status', fakeAsync(() => {
      tick();

      connectionStatusSubject.next({ connected: true, reconnecting: false });
      tick();

      expect(component.connectionStatus().connected).toBeTrue();
      expect(component.connectionStatus().reconnecting).toBeFalse();
    }));

    it('should handle multiple log messages', fakeAsync(() => {
      tick();

      for (let i = 0; i < 10; i++) {
        const msg: any = {
          type: 'log',
          id: `test-log-${i}`,
          timestamp: new Date().toISOString(),
          level: 'info',
          message: `Message ${i}`
        };
        logStreamSubject.next(msg);
      }
      tick();

      expect(component.logs().length).toBe(10);
    }));
  });

  describe('Auto-scroll', () => {
    it('should auto-scroll to bottom when enabled', fakeAsync(() => {
      tick();

      component.autoScroll.set(true);

      logStreamSubject.next({
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'New message'
      });
      tick();

      expect(component.logs().length).toBe(1);
    }));

    it('should track new log count when auto-scroll disabled', fakeAsync(() => {
      tick();

      component.autoScroll.set(false);

      logStreamSubject.next({
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'New message'
      });
      tick();

      expect(component.logs().length).toBe(1);
      expect(component.newLogCount()).toBe(1);
    }));
  });

  describe('Filters', () => {
    it('should filter logs by level', fakeAsync(() => {
      tick();

      // Add some logs via WebSocket — include explicit ids to avoid deduplication
      logStreamSubject.next({ type: 'log', id: 'log-info-1', timestamp: new Date().toISOString(), level: 'info', message: 'Info msg' } as any);
      logStreamSubject.next({ type: 'log', id: 'log-error-1', timestamp: new Date().toISOString(), level: 'error', message: 'Error msg' } as any);
      tick();
      fixture.detectChanges();

      component.setLevelFilter('ERROR');
      tick();
      fixture.detectChanges();

      expect(component.filteredLogs().length).toBe(1);
      expect(component.filteredLogs()[0].message).toBe('Error msg');
    }));

    it('should clear search', () => {
      component.searchQuery.set('something');
      component.clearSearch();
      expect(component.searchQuery()).toBe('');
    });
  });
});
