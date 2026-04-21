import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ReconciliationService } from './reconciliation.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { MatchCandidate, MatchStats, SuggestResponse } from '../models/reconciliation.model';

describe('ReconciliationService', () => {
  let service: ReconciliationService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  const candidate: MatchCandidate = {
    id: 'c1',
    projectId: 'p1',
    sourceUri: 'http://example.org/a',
    targetUri: 'http://example.org/b',
    predicate: 'SAME_AS',
    confidence: 0.9,
    source: 'LOCAL_DUPLICATE',
    matcherName: 'local-duplicate',
    status: 'PENDING',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
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
        ReconciliationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsSpy }
      ]
    });
    service = TestBed.inject(ReconciliationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists candidates for a project', () => {
    let actual: MatchCandidate[] | undefined;
    service.list('p1', { status: 'PENDING' }).subscribe(r => (actual = r));

    const req = http.expectOne(r =>
      r.url === `${base}/reconciliation/candidates`
      && r.params.get('projectId') === 'p1'
      && r.params.get('status') === 'PENDING'
    );
    expect(req.request.method).toBe('GET');
    req.flush([candidate]);
    expect(actual?.length).toBe(1);
  });

  it('suggests candidates', () => {
    const response: SuggestResponse = { persisted: 1, duplicatesSkipped: 0, candidates: [candidate] };
    let actual: SuggestResponse | undefined;
    service.suggest({
      projectId: 'p1',
      sourceUri: 'http://example.org/a',
      label: 'Paris'
    }).subscribe(r => (actual = r));

    const req = http.expectOne(`${base}/reconciliation/candidates/suggest`);
    expect(req.request.method).toBe('POST');
    req.flush(response);
    expect(actual?.persisted).toBe(1);
  });

  it('approves a candidate', () => {
    let approved: MatchCandidate | undefined;
    service.approve('c1').subscribe(r => (approved = r));
    const req = http.expectOne(`${base}/reconciliation/candidates/c1/approve`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...candidate, status: 'APPROVED' });
    expect(approved?.status).toBe('APPROVED');
  });

  it('fetches stats', () => {
    const stats: MatchStats = {
      projectId: 'p1',
      pending: 3, approved: 1, rejected: 0, archived: 0,
      byPredicate: { SAME_AS: 4 },
      byMatcher: { 'local-duplicate': 4 }
    };
    let actual: MatchStats | undefined;
    service.stats('p1').subscribe(r => (actual = r));
    const req = http.expectOne(r => r.url === `${base}/reconciliation/stats`);
    req.flush(stats);
    expect(actual?.pending).toBe(3);
  });
});
