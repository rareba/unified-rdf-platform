import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { LineageService } from './lineage.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { LineageGraph } from '../models/lineage.model';

describe('LineageService', () => {
  let service: LineageService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  const mockGraph: LineageGraph = {
    projectId: 'p-1',
    nodes: [{ id: 'uuid:project-p-1', kind: 'PROJECT', label: 'P' }],
    edges: []
  };

  beforeEach(() => {
    const settings = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });
    TestBed.configureTestingModule({
      providers: [
        LineageService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settings }
      ]
    });
    service = TestBed.inject(LineageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('forProject GETs /lineage/project/:id', () => {
    service.forProject('p-1').subscribe(g => {
      expect(g.nodes.length).toBe(1);
      expect(g.projectId).toBe('p-1');
    });
    const req = httpMock.expectOne(`${baseUrl}/lineage/project/p-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockGraph);
  });

  it('forResource GETs /lineage/resource/:kind/:id', () => {
    service.forResource('MAPPING', 'm-1').subscribe(g => {
      expect(g.projectId).toBe('p-1');
    });
    const req = httpMock.expectOne(`${baseUrl}/lineage/resource/MAPPING/m-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockGraph);
  });
});
