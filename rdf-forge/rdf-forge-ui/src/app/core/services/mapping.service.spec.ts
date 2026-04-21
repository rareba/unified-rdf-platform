import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { MappingService } from './mapping.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Mapping, MappingPreviewResponse, ExplainResponse } from '../models/mapping.model';

describe('MappingService', () => {
  let service: MappingService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  const mockMapping: Mapping = {
    id: 'm-1',
    projectId: 'p-1',
    name: 'Test Mapping',
    sourceType: 'CSV',
    rules: [],
    mappingType: 'GENERIC',
    version: 1,
    createdBy: 'u-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
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
        MappingService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settings }
      ]
    });
    service = TestBed.inject(MappingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listByProject issues GET /mappings?projectId=', () => {
    service.listByProject('p-1').subscribe(list => {
      expect(list.length).toBe(1);
      expect(list[0].id).toBe('m-1');
    });
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/mappings` && r.params.get('projectId') === 'p-1');
    expect(req.request.method).toBe('GET');
    req.flush([mockMapping]);
  });

  it('get issues GET /mappings/:id', () => {
    service.get('m-1').subscribe(m => expect(m.id).toBe('m-1'));
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMapping);
  });

  it('create POSTs body to /mappings', () => {
    service.create({
      projectId: 'p-1', name: 'X', sourceType: 'CSV', rules: []
    }).subscribe(m => expect(m.id).toBe('m-1'));
    const req = httpMock.expectOne(`${baseUrl}/mappings`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.projectId).toBe('p-1');
    req.flush(mockMapping);
  });

  it('update PUTs to /mappings/:id', () => {
    service.update('m-1', { name: 'Renamed' }).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockMapping);
  });

  it('delete DELETEs /mappings/:id', () => {
    service.delete('m-1').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('preview POSTs and returns triples', () => {
    const resp: MappingPreviewResponse = {
      triples: [{ subject: 's', predicate: 'p', object: 'o', objectType: 'URI' }],
      sampleSize: 1, totalSourceRows: 1
    };
    service.preview('m-1', { sourceRows: [{ id: '1' }] }).subscribe(r => {
      expect(r.triples.length).toBe(1);
    });
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1/preview`);
    expect(req.request.method).toBe('POST');
    req.flush(resp);
  });

  it('explain POSTs and returns rows', () => {
    const resp: ExplainResponse = {
      rows: [{
        rowIndex: 0, row: { id: '1' }, triples: [{
          triple: { subject: 's', predicate: 'p', object: 'o', objectType: 'URI' },
          trace: {
            ruleId: 'r1', ruleType: 'FIXED_URI', source: null, target: null,
            uriTemplateUsed: null, sourceValue: null, transforms: [], finalValue: null
          }
        }]
      }]
    };
    service.explain('m-1', { sourceRows: [{ id: '1' }] }).subscribe(r => {
      expect(r.rows.length).toBe(1);
    });
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1/explain`);
    expect(req.request.method).toBe('POST');
    req.flush(resp);
  });

  it('validate POSTs to /mappings/:id/validate', () => {
    service.validate('m-1', { availableColumns: ['id'] }).subscribe(v => {
      expect(v.valid).toBeTrue();
    });
    const req = httpMock.expectOne(`${baseUrl}/mappings/m-1/validate`);
    expect(req.request.method).toBe('POST');
    req.flush({ valid: true, issues: [] });
  });
});
