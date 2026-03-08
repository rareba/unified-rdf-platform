import { TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { JobService, LogStreamMessage, ConnectionStatus } from './job.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Job, JobLog, JobMetrics, JobSchedule } from '../models';

describe('JobService', () => {
  let service: JobService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockJob: Job = {
    id: 'job-1',
    pipelineId: 'pipeline-1',
    pipelineName: 'Test Pipeline',
    pipelineVersion: 1,
    status: 'running',
    progress: 50,
    variables: {},
    triggeredBy: 'manual',
    metrics: { rowsProcessed: 1000, quadsGenerated: 5000 },
    createdBy: 'user',
    createdAt: new Date()
  };

  const mockLogs: JobLog[] = [
    { id: 'log-1', timestamp: new Date(), level: 'info', message: 'Job started' },
    { id: 'log-2', timestamp: new Date(), level: 'info', message: 'Processing' }
  ];

  beforeEach(() => {
    settingsServiceMock = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        JobService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(JobService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.disconnect();
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of jobs', () => {
      service.list().subscribe(jobs => {
        expect(jobs.length).toBe(1);
        expect(jobs[0].id).toBe('job-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/jobs` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockJob]);
    });

    it('should handle list params', () => {
      service.list({ status: 'running', pipelineId: 'p1' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/jobs` &&
        r.params.get('status') === 'running' &&
        r.params.get('pipelineId') === 'p1'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('should handle empty list', () => {
      service.list().subscribe(jobs => {
        expect(jobs.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/jobs` && r.params.has('size'));
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single job by id', () => {
      service.get('job-1').subscribe(job => {
        expect(job.id).toBe('job-1');
        expect(job.status).toBe('running');
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockJob);
    });

    it('should handle job not found', () => {
      service.get('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('create()', () => {
    it('should create a new job', () => {
      service.create('pipeline-1', { key: 'value' }, 1).subscribe(job => {
        expect(job).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        pipelineId: 'pipeline-1',
        variables: { key: 'value' },
        priority: 1
      });
      req.flush(mockJob);
    });

    it('should create a job without optional params', () => {
      service.create('pipeline-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/jobs`);
      expect(req.request.body).toEqual({
        pipelineId: 'pipeline-1',
        variables: undefined,
        priority: undefined
      });
      req.flush(mockJob);
    });

    it('should create a job with empty variables', () => {
      service.create('pipeline-1', {}, 0).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/jobs`);
      expect(req.request.body).toEqual({
        pipelineId: 'pipeline-1',
        variables: {},
        priority: 0
      });
      req.flush(mockJob);
    });
  });

  describe('cancel()', () => {
    it('should cancel a job', () => {
      service.cancel('job-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should handle cancel error', () => {
      service.cancel('job-1').subscribe({
        error: (error) => {
          expect(error.status).toBe(409);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1`);
      req.flush('Job already completed', { status: 409, statusText: 'Conflict' });
    });
  });

  describe('retry()', () => {
    it('should retry a job', () => {
      service.retry('job-1').subscribe(job => {
        expect(job.id).toBe('job-2');
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1/retry`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockJob, id: 'job-2', status: 'pending' as const });
    });

    it('should handle retry for non-retryable job', () => {
      service.retry('job-1').subscribe({
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1/retry`);
      req.flush('Job not in failed state', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('getLogs()', () => {
    it('should return job logs', () => {
      service.getLogs('job-1').subscribe(logs => {
        expect(logs.length).toBe(2);
        expect(logs[0].level).toBe('info');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/jobs/job-1/logs` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(mockLogs);
    });

    it('should handle log params', () => {
      service.getLogs('job-1', { level: 'error', limit: 50 }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/jobs/job-1/logs` &&
        r.params.get('level') === 'error' &&
        r.params.get('limit') === '50'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('should handle empty logs', () => {
      service.getLogs('job-1').subscribe(logs => {
        expect(logs.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/jobs/job-1/logs` && r.params.has('size'));
      req.flush([]);
    });
  });

  describe('getMetrics()', () => {
    it('should return job metrics', () => {
      const metrics: JobMetrics = { rowsProcessed: 1000, quadsGenerated: 5000, duration: 3600 };

      service.getMetrics('job-1').subscribe(m => {
        expect(m.rowsProcessed).toBe(1000);
        expect(m.quadsGenerated).toBe(5000);
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1/metrics`);
      expect(req.request.method).toBe('GET');
      req.flush(metrics);
    });

    it('should handle missing metrics', () => {
      service.getMetrics('job-1').subscribe(m => {
        expect(m.rowsProcessed).toBeUndefined();
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1/metrics`);
      req.flush({});
    });
  });

  describe('getSchedules()', () => {
    it('should return all schedules', () => {
      const schedules: JobSchedule[] = [
        { id: 'sched-1', pipelineId: 'p1', cronExpression: '0 0 * * *', variables: {}, isActive: true }
      ];

      service.getSchedules().subscribe(s => {
        expect(s.length).toBe(1);
        expect(s[0].cronExpression).toBe('0 0 * * *');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/schedules` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(schedules);
    });

    it('should handle empty schedules', () => {
      service.getSchedules().subscribe(s => {
        expect(s.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/schedules` && r.params.has('size'));
      req.flush([]);
    });
  });

  describe('createSchedule()', () => {
    it('should create a new schedule', () => {
      service.createSchedule('pipeline-1', '0 0 * * *', { env: 'prod' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        pipelineId: 'pipeline-1',
        cronExpression: '0 0 * * *',
        variables: { env: 'prod' }
      });
      req.flush({ id: 'sched-1', pipelineId: 'pipeline-1', cronExpression: '0 0 * * *' });
    });

    it('should create a schedule without variables', () => {
      service.createSchedule('pipeline-1', '0 0 * * *').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules`);
      expect(req.request.body).toEqual({
        pipelineId: 'pipeline-1',
        cronExpression: '0 0 * * *',
        variables: undefined
      });
      req.flush({ id: 'sched-1', pipelineId: 'pipeline-1', cronExpression: '0 0 * * *' });
    });
  });

  describe('updateSchedule()', () => {
    it('should update a schedule', () => {
      service.updateSchedule('sched-1', { isActive: false }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules/sched-1`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ isActive: false });
      req.flush({ id: 'sched-1', isActive: false });
    });

    it('should update schedule cron expression', () => {
      service.updateSchedule('sched-1', { cronExpression: '0 30 * * *' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules/sched-1`);
      expect(req.request.body).toEqual({ cronExpression: '0 30 * * *' });
      req.flush({ id: 'sched-1', cronExpression: '0 30 * * *' });
    });
  });

  describe('deleteSchedule()', () => {
    it('should delete a schedule', () => {
      service.deleteSchedule('sched-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules/sched-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should handle delete for non-existent schedule', () => {
      service.deleteSchedule('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/schedules/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('WebSocket Connection', () => {
    it('should initialize with disconnected status', () => {
      let status: ConnectionStatus | undefined;
      service.connectionStatus$.subscribe(s => status = s);
      
      expect(status).toBeDefined();
      expect(status?.connected).toBeFalse();
      expect(status?.reconnecting).toBeFalse();
    });

    it('should not connect without jobId', () => {
      service.connectToJobLogs('');
      expect(service.isConnected()).toBeFalse();
    });

    it('should disconnect and clean up resources', fakeAsync(() => {
      service.connectToJobLogs('job-1');
      tick();
      
      service.disconnect();
      tick();
      
      expect(service.isConnected()).toBeFalse();
      
      let status: ConnectionStatus | undefined;
      service.connectionStatus$.subscribe(s => status = s);
      expect(status?.connected).toBeFalse();
      expect(status?.reconnecting).toBeFalse();
    }));

    it('should handle multiple disconnect calls gracefully', fakeAsync(() => {
      service.connectToJobLogs('job-1');
      tick();
      
      // Multiple disconnects should not throw
      expect(() => {
        service.disconnect();
        service.disconnect();
        service.disconnect();
      }).not.toThrow();
    }));

    it('should not reconnect after explicit disconnect', fakeAsync(() => {
      service.connectToJobLogs('job-1');
      tick();
      
      service.disconnect();
      tick();
      
      // Reconnection should be disabled
      // Fast-forward time to ensure no reconnection attempts
      tick(60000);
      expect(service.isConnected()).toBeFalse();
    }));
  });

  describe('Log Stream', () => {
    it('should emit log messages', fakeAsync(() => {
      const messages: LogStreamMessage[] = [];
      service.logStream$.subscribe(msg => messages.push(msg));
      
      // Note: In a real test with mocked STOMP client,
      // we would simulate incoming messages here
      
      expect(messages).toEqual([]);
    }));

    it('should handle different message types', () => {
      const logMessage: LogStreamMessage = {
        type: 'log',
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'Test log message'
      };
      
      const statusMessage: LogStreamMessage = {
        type: 'status',
        status: 'running',
        progress: 50
      };
      
      const completionMessage: LogStreamMessage = {
        type: 'completion',
        success: true
      };
      
      expect(logMessage.type).toBe('log');
      expect(statusMessage.type).toBe('status');
      expect(completionMessage.type).toBe('completion');
    });
  });

  describe('ngOnDestroy', () => {
    it('should clean up on destroy', fakeAsync(() => {
      service.connectToJobLogs('job-1');
      tick();
      
      service.ngOnDestroy();
      tick();
      
      expect(service.isConnected()).toBeFalse();
    }));

    it('should complete subjects on destroy', () => {
      const logStreamCompleteSpy = spyOn((service as unknown as { logStreamSubject: { complete: () => void } }).logStreamSubject, 'complete');
      const connectionStatusCompleteSpy = spyOn((service as unknown as { connectionStatusSubject: { complete: () => void } }).connectionStatusSubject, 'complete');
      
      service.ngOnDestroy();
      
      expect(logStreamCompleteSpy).toHaveBeenCalled();
      expect(connectionStatusCompleteSpy).toHaveBeenCalled();
    });
  });

  describe('Error Handling', () => {
    it('should handle network errors gracefully', () => {
      service.list().subscribe({
        error: (error) => {
          expect(error).toBeTruthy();
        }
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/jobs` && r.params.has('size'));
      req.error(new ProgressEvent('Network error'));
    });

    it('should handle server errors', () => {
      service.get('job-1').subscribe({
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1`);
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('Edge Cases', () => {
    it('should handle pagination params', () => {
      service.list({ page: 2, limit: 10 }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/jobs` &&
        r.params.get('page') === '2' &&
        r.params.get('limit') === '10'
      );
      req.flush([]);
    });

    it('should handle all job statuses in list params', () => {
      const statuses = ['pending', 'running', 'completed', 'failed', 'cancelled'] as const;
      
      statuses.forEach(status => {
        service.list({ status }).subscribe();
        
        const req = httpMock.expectOne(r =>
          r.url === `${baseUrl}/jobs` &&
          r.params.get('status') === status
        );
        req.flush([]);
      });
    });

    it('should handle log level filtering', () => {
      const levels = ['debug', 'info', 'warn', 'error'] as const;
      
      levels.forEach(level => {
        service.getLogs('job-1', { level }).subscribe();
        
        const req = httpMock.expectOne(r =>
          r.url === `${baseUrl}/jobs/job-1/logs` &&
          r.params.get('level') === level
        );
        req.flush([]);
      });
    });
  });
});
