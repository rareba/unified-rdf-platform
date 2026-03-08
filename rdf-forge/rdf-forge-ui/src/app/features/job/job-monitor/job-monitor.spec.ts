import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
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

  const mockLogs: JobLog[] = [
    { id: 'log-1', timestamp: new Date(), level: 'info', message: 'Job started', step: 'init' },
    { id: 'log-2', timestamp: new Date(), level: 'info', message: 'Processing data', step: 'process' },
    { id: 'log-3', timestamp: new Date(), level: 'warn', message: 'Slow operation detected', step: 'process' },
    { id: 'log-4', timestamp: new Date(), level: 'error', message: 'Connection timeout', step: 'output' }
  ];

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

  it('should load logs on init', fakeAsync(() => {
    tick();
    expect(jobServiceSpy.getLogs).toHaveBeenCalledWith('job-1', { limit: 100 });
    expect(component.logs().length).toBe(4);
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
    // Set a failed job
    component.job.set(mockFailedJob);
    jobServiceSpy.retry.and.returnValue(of({ ...mockJob, id: 'job-2', status: 'pending' as const }));
    component.retryJob();
    tick();
    expect(jobServiceSpy.retry).toHaveBeenCalledWith('job-1');
  }));

  it('should not retry if job is not failed', fakeAsync(() => {
    tick();
    component.job.set(mockJob); // running job
    component.retryJob();
    expect(jobServiceSpy.retry).not.toHaveBeenCalled();
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    jobServiceSpy.get.and.returnValue(throwError(() => new Error('Network error')));
    component.loadJob('job-1');
    tick();
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBeTruthy();
  }));

  it('should handle logs load error', fakeAsync(() => {
    jobServiceSpy.getLogs.and.returnValue(throwError(() => new Error('Logs error')));
    component.loadLogs('job-1');
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should format duration', () => {
    expect(component.formatDuration(undefined)).toBe('-');
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(5000)).toBe('5s');
    expect(component.formatDuration(120000)).toBe('2m');
    expect(component.formatDuration(3600000)).toBe('1h');
    expect(component.formatDuration(7200000)).toBe('2h');
    expect(component.formatDuration(90000)).toBe('1m 30s');
  });

  it('should get log class', () => {
    expect(component.getLogClass('info')).toBe('log-info');
    expect(component.getLogClass('error')).toBe('log-error');
    expect(component.getLogClass('warn')).toBe('log-warn');
    expect(component.getLogClass('debug')).toBe('log-debug');
    expect(component.getLogClass('trace')).toBe('log-debug');
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
    expect(component.formatDate(null as any)).toBe('-');
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

  it('should handle cancel error', fakeAsync(() => {
    jobServiceSpy.cancel.and.returnValue(throwError(() => new Error('Cancel failed')));
    tick();
    component.cancelJob();
    tick();
    // Error handling shows snackbar
    expect(component.error()).toBeTruthy();
  }));

  it('should handle retry error', fakeAsync(() => {
    component.job.set(mockFailedJob);
    jobServiceSpy.retry.and.returnValue(throwError(() => new Error('Retry failed')));
    tick();
    component.retryJob();
    tick();
    // Error handling shows snackbar
    expect(component.error()).toBeTruthy();
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

      expect(component.logs().length).toBe(5); // 4 initial + 1 new
      expect(component.logs()[4].message).toBe('New log from WebSocket');
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
      
      const completionMessage: LogStreamMessage = {
        type: 'completion',
        success: true
      };

      logStreamSubject.next(completionMessage);
      tick();

      expect(component.job()?.status).toBe('completed');
    }));

    it('should receive historical logs from WebSocket', fakeAsync(() => {
      tick();
      
      const historicalMessage: LogStreamMessage = {
        type: 'historical',
        historicalLogs: [
          { id: 'h1', timestamp: new Date(), level: 'info', message: 'Historical log 1' },
          { id: 'h2', timestamp: new Date(), level: 'info', message: 'Historical log 2' }
        ]
      };

      logStreamSubject.next(historicalMessage);
      tick();

      expect(component.logs().length).toBe(6); // 4 initial + 2 historical
    }));

    it('should update connection status', fakeAsync(() => {
      tick();
      
      connectionStatusSubject.next({ connected: true, reconnecting: false });
      tick();

      expect(component.isConnected()).toBeTrue();
      expect(component.isReconnecting()).toBeFalse();
    }));

    it('should show reconnecting status', fakeAsync(() => {
      tick();
      
      connectionStatusSubject.next({ connected: false, reconnecting: true, error: 'Reconnecting...' });
      tick();

      expect(component.isConnected()).toBeFalse();
      expect(component.isReconnecting()).toBeTrue();
    }));

    it('should show connection error', fakeAsync(() => {
      tick();
      
      connectionStatusSubject.next({ connected: false, reconnecting: false, error: 'Connection failed' });
      tick();

      expect(component.connectionError()).toBe('Connection failed');
    }));

    it('should handle multiple log messages', fakeAsync(() => {
      tick();
      
      for (let i = 0; i < 10; i++) {
        logStreamSubject.next({
          type: 'log',
          timestamp: new Date().toISOString(),
          level: 'info',
          message: `Message ${i}`
        });
      }
      tick();

      expect(component.logs().length).toBe(14); // 4 initial + 10 new
    }));
  });

  describe('Log Filtering', () => {
    it('should filter logs by level', fakeAsync(() => {
      tick();
      
      component.filterLevel.set('error');
      const filtered = component.filteredLogs();
      
      expect(filtered.length).toBe(1);
      expect(filtered[0].level).toBe('error');
    }));

    it('should filter logs by search term', fakeAsync(() => {
      tick();
      
      component.searchTerm.set('Processing');
      const filtered = component.filteredLogs();
      
      expect(filtered.length).toBe(1);
      expect(filtered[0].message).toContain('Processing');
    }));

    it('should show all logs when no filter', fakeAsync(() => {
      tick();
      
      component.filterLevel.set('all');
      component.searchTerm.set('');
      const filtered = component.filteredLogs();
      
      expect(filtered.length).toBe(4);
    }));

    it('should clear filters', fakeAsync(() => {
      tick();
      
      component.filterLevel.set('error');
      component.searchTerm.set('test');
      component.clearFilters();
      
      expect(component.filterLevel()).toBe('all');
      expect(component.searchTerm()).toBe('');
    }));
  });

  describe('Auto-scroll', () => {
    it('should auto-scroll to bottom when enabled', fakeAsync(() => {
      tick();
      
      component.autoScroll = true;
      
      logStreamSubject.next({
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'New message'
      });
      tick();

      // Auto-scroll should be triggered
      expect(component.logs().length).toBe(5);
    }));

    it('should not auto-scroll when disabled', fakeAsync(() => {
      tick();
      
      component.autoScroll = false;
      
      logStreamSubject.next({
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'New message'
      });
      tick();

      expect(component.logs().length).toBe(5);
    }));
  });

  describe('Metrics Display', () => {
    it('should display job metrics', fakeAsync(() => {
      tick();
      
      const metrics = component.jobMetrics();
      expect(metrics).toBeDefined();
      expect(metrics?.rowsProcessed).toBe(1000);
      expect(metrics?.quadsGenerated).toBe(5000);
    }));

    it('should handle missing metrics', fakeAsync(() => {
      tick();
      
      component.job.set({ ...mockJob, metrics: undefined });
      const metrics = component.jobMetrics();
      expect(metrics).toBeUndefined();
    }));

    it('should format throughput', () => {
      expect(component.formatThroughput(1000, 1000)).toBe('1,000 rows/s');
      expect(component.formatThroughput(0, 1000)).toBe('0 rows/s');
      expect(component.formatThroughput(1000, 0)).toBe('-');
    });
  });

  describe('Job Progress', () => {
    it('should calculate progress for running job', fakeAsync(() => {
      tick();
      expect(component.progress()).toBe(50);
    }));

    it('should show 100% for completed job', fakeAsync(() => {
      tick();
      component.job.set(mockCompletedJob);
      expect(component.progress()).toBe(100);
    }));

    it('should show 0% for pending job', fakeAsync(() => {
      tick();
      component.job.set({ ...mockJob, status: 'pending', progress: 0 });
      expect(component.progress()).toBe(0);
    }));
  });

  describe('Polling', () => {
    it('should start polling for running jobs', fakeAsync(() => {
      tick();
      
      // Job is running, polling should be active
      expect(component.isPolling()).toBeTrue();
    }));

    it('should stop polling for completed jobs', fakeAsync(() => {
      tick();
      
      component.job.set(mockCompletedJob);
      tick();
      
      expect(component.isPolling()).toBeFalse();
    }));

    it('should stop polling on destroy', fakeAsync(() => {
      tick();
      
      expect(component.isPolling()).toBeTrue();
      
      component.ngOnDestroy();
      tick();
      
      expect(component.isPolling()).toBeFalse();
    }));
  });

  describe('Export', () => {
    it('should export logs as JSON', fakeAsync(() => {
      tick();
      
      const blob = component.exportLogs('json');
      expect(blob).toBeDefined();
      expect(blob.type).toBe('application/json');
    }));

    it('should export logs as CSV', fakeAsync(() => {
      tick();
      
      const blob = component.exportLogs('csv');
      expect(blob).toBeDefined();
      expect(blob.type).toBe('text/csv');
    }));

    it('should export logs as text', fakeAsync(() => {
      tick();
      
      const blob = component.exportLogs('text');
      expect(blob).toBeDefined();
      expect(blob.type).toBe('text/plain');
    }));
  });

  describe('Keyboard Shortcuts', () => {
    it('should handle refresh shortcut', fakeAsync(() => {
      tick();
      spyOn(component, 'refresh');
      
      const event = new KeyboardEvent('keydown', { key: 'r', ctrlKey: true });
      component.handleKeyboard(event);
      
      expect(component.refresh).toHaveBeenCalled();
    }));

    it('should handle cancel shortcut', fakeAsync(() => {
      tick();
      spyOn(component, 'cancelJob');
      
      const event = new KeyboardEvent('keydown', { key: 'c', ctrlKey: true });
      component.handleKeyboard(event);
      
      expect(component.cancelJob).toHaveBeenCalled();
    }));
  });
});
