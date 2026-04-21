import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ValidationService } from './validation.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import {
  ValidationRun,
  ValidationSuite,
  ValidationSuiteCreateRequest
} from '../models/validation.model';

describe('ValidationService', () => {
  let service: ValidationService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  const suite: ValidationSuite = {
    id: 's1',
    projectId: 'p1',
    name: 'suite-1',
    rules: [],
    gate: 'FAIL_ON_ERROR'
  };

  const run: ValidationRun = {
    id: 'r1',
    suiteId: 's1',
    projectId: 'p1',
    ranAt: new Date().toISOString(),
    durationMs: 5,
    status: 'PASSED',
    issueCount: 0,
    errorCount: 0,
    warningCount: 0,
    infoCount: 0,
    fatalCount: 0
  };

  beforeEach(() => {
    const settingsSpy = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });
    TestBed.configureTestingModule({
      providers: [
        ValidationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsSpy }
      ]
    });
    service = TestBed.inject(ValidationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists suites for a project', () => {
    service.listSuites('p1').subscribe(list => {
      expect(list.length).toBe(1);
      expect(list[0].name).toBe('suite-1');
    });
    const req = http.expectOne(r =>
      r.method === 'GET' && r.url === `${base}/validation/suites`);
    expect(req.request.params.get('projectId')).toBe('p1');
    req.flush([suite]);
  });

  it('creates a suite', () => {
    const payload: ValidationSuiteCreateRequest = {
      projectId: 'p1', name: 'new-suite', rules: [], gate: 'FAIL_ON_ERROR'
    };
    service.createSuite(payload).subscribe(r => expect(r.id).toBe('s1'));
    const req = http.expectOne(`${base}/validation/suites`);
    expect(req.request.method).toBe('POST');
    req.flush(suite);
  });

  it('runs a suite', () => {
    service.runSuite('s1', { targetGraph: 'urn:g', targetTriplestoreId: 't1' })
      .subscribe(r => expect(r.status).toBe('PASSED'));
    const req = http.expectOne(`${base}/validation/suites/s1/run`);
    expect(req.request.method).toBe('POST');
    req.flush(run);
  });

  it('fetches history', () => {
    service.history('s1', 5).subscribe(h => expect(h.length).toBe(1));
    const req = http.expectOne(r =>
      r.method === 'GET' && r.url === `${base}/validation/runs`);
    expect(req.request.params.get('suiteId')).toBe('s1');
    req.flush([run]);
  });

  it('fetches issues with severity filter', () => {
    service.issues('r1', 'ERROR').subscribe(i => expect(i).toEqual([]));
    const req = http.expectOne(r =>
      r.method === 'GET' && r.url === `${base}/validation/runs/r1/issues`);
    expect(req.request.params.get('severity')).toBe('ERROR');
    req.flush([]);
  });

  it('updates a suite', () => {
    service.updateSuite('s1', { name: 'renamed', rules: [], gate: 'WARN_ONLY' })
      .subscribe(r => expect(r.id).toBe('s1'));
    const req = http.expectOne(`${base}/validation/suites/s1`);
    expect(req.request.method).toBe('PUT');
    req.flush(suite);
  });

  it('deletes a suite', () => {
    service.deleteSuite('s1').subscribe();
    const req = http.expectOne(`${base}/validation/suites/s1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
