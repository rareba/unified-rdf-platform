import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { SavedQueryService } from './saved-query.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { SavedQuery, SavedQueryRunResponse } from '../models/saved-query.model';

describe('SavedQueryService', () => {
  let service: SavedQueryService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  const sample: SavedQuery = {
    id: 'q1',
    projectId: 'p1',
    name: 'top-cities',
    type: 'SELECT',
    queryText: 'SELECT * WHERE { ?s ?p ?o } LIMIT 10',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    runCount: 0
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
        SavedQueryService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsSpy }
      ]
    });
    service = TestBed.inject(SavedQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists saved queries by project', () => {
    let received: SavedQuery[] | undefined;
    service.list('p1').subscribe(r => (received = r));
    const req = http.expectOne(r =>
      r.url === `${base}/sparql/queries` && r.params.get('projectId') === 'p1'
    );
    expect(req.request.method).toBe('GET');
    req.flush([sample]);
    expect(received?.length).toBe(1);
  });

  it('runs a saved query', () => {
    const mockResponse: SavedQueryRunResponse = {
      type: 'SELECT',
      variables: ['s'],
      bindings: [],
      durationMs: 5,
      executedAt: new Date().toISOString()
    };
    let actual: SavedQueryRunResponse | undefined;
    service.run('q1', { triplestoreId: 'ts1', parameters: {} })
      .subscribe(r => (actual = r));

    const req = http.expectOne(`${base}/sparql/queries/q1/run`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
    expect(actual?.type).toBe('SELECT');
  });

  it('runs an inline query', () => {
    const mockResponse: SavedQueryRunResponse = {
      type: 'ASK',
      askResult: true,
      durationMs: 2,
      executedAt: new Date().toISOString()
    };
    let actual: SavedQueryRunResponse | undefined;
    service.runInline({
      queryText: 'ASK { ?s ?p ?o }',
      triplestoreId: 'ts1'
    }).subscribe(r => (actual = r));

    const req = http.expectOne(`${base}/sparql/run`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
    expect(actual?.askResult).toBe(true);
  });

  it('deletes a saved query', () => {
    service.delete('q1').subscribe();
    const req = http.expectOne(`${base}/sparql/queries/q1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
