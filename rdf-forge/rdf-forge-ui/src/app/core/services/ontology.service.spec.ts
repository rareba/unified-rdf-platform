import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { OntologyService } from './ontology.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Ontology, OntologyImportRequest } from '../models';

describe('OntologyService', () => {
  let service: OntologyService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  const mockOntology: Ontology = {
    id: 'ont-1',
    projectId: 'proj-1',
    name: 'Test Ontology',
    namespace: 'http://example.org/schema/',
    prefix: 'ex',
    format: 'TURTLE',
    version: 1,
    createdBy: 'user-1',
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    const settingsStub = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        OntologyService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsStub }
      ]
    });
    service = TestBed.inject(OntologyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list() GETs with projectId', () => {
    service.list('proj-1').subscribe(list => expect(list.length).toBe(1));
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/ontologies` && r.params.get('projectId') === 'proj-1'
    );
    expect(req.request.method).toBe('GET');
    req.flush([mockOntology]);
  });

  it('get() fetches by id', () => {
    service.get('ont-1').subscribe(o => expect(o.id).toBe('ont-1'));
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockOntology);
  });

  it('import() POSTs to /ontologies/import', () => {
    const payload: OntologyImportRequest = {
      projectId: 'proj-1',
      name: 'New',
      format: 'TURTLE',
      content: '@prefix ex: <http://example.org/> .'
    };
    service.import(payload).subscribe(o => expect(o.name).toBe('Test Ontology'));
    const req = httpMock.expectOne(`${baseUrl}/ontologies/import`);
    expect(req.request.method).toBe('POST');
    req.flush(mockOntology);
  });

  it('updateMetadata() PUTs to /ontologies/:id', () => {
    service.updateMetadata('ont-1', { description: 'x' }).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockOntology);
  });

  it('updateContent() PUTs to /ontologies/:id/content', () => {
    service.updateContent('ont-1', '...', 'TURTLE').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1/content`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockOntology);
  });

  it('delete() DELETEs', () => {
    service.delete('ont-1').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('namespaces() fetches namespace map', () => {
    service.namespaces('ont-1').subscribe(ns => expect(ns.entries.length).toBe(1));
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1/namespaces`);
    expect(req.request.method).toBe('GET');
    req.flush({ entries: [{ prefix: 'ex', uri: 'http://example.org/' }] });
  });

  it('classes() searches with q and limit', () => {
    service.classes('ont-1', 'Person', 25).subscribe(list => expect(list.length).toBe(1));
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/ontologies/ont-1/classes` &&
      r.params.get('q') === 'Person' &&
      r.params.get('limit') === '25'
    );
    req.flush([{ uri: 'http://example.org/Person', type: 'CLASS', label: 'Person' }]);
  });

  it('properties() fetches without q', () => {
    service.properties('ont-1').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/ontologies/ont-1/properties` &&
      !r.params.has('q')
    );
    req.flush([]);
  });

  it('skosConcepts() uses /skos-concepts path', () => {
    service.skosConcepts('ont-1').subscribe();
    const req = httpMock.expectOne(r => r.url === `${baseUrl}/ontologies/ont-1/skos-concepts`);
    req.flush([]);
  });

  it('termDetail() passes uri query param', () => {
    service.termDetail('ont-1', 'http://example.org/Person').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/ontologies/ont-1/term` &&
      r.params.get('uri') === 'http://example.org/Person'
    );
    req.flush({ uri: 'http://example.org/Person', type: 'CLASS' });
  });

  it('exportContent() passes format if given', () => {
    service.exportContent('ont-1', 'JSON_LD').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/ontologies/ont-1/content` && r.params.get('format') === 'JSON_LD'
    );
    req.flush({ id: 'ont-1', name: 'x', format: 'JSON_LD', content: '{}' });
  });

  it('validate() POSTs to /ontologies/:id/validate', () => {
    service.validate('ont-1').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/ontologies/ont-1/validate`);
    expect(req.request.method).toBe('POST');
    req.flush({ valid: true, errors: [], tripleCount: 0 });
  });
});
