import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { JobService } from './job.service';
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
  });

  describe('cancel()', () => {
    it('should cancel a job', () => {
      service.cancel('job-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('retry()', () => {
    it('should retry a job', () => {
      service.retry('job-1').subscribe(job => {
        expect(job.id).toBe('job-2');
      });

      const req = httpMock.expectOne(`${baseUrl}/jobs/job-1/retry`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockJob, id: 'job-2', status: 'pending' });
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
  });

  describe('updateSchedule()', () => {
    it('should update a schedule', () => {
      service.updateSchedule('sched-1', { isActive: false }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules/sched-1`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ isActive: false });
      req.flush({ id: 'sched-1', isActive: false });
    });
  });

  describe('deleteSchedule()', () => {
    it('should delete a schedule', () => {
      service.deleteSchedule('sched-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/schedules/sched-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });
});
