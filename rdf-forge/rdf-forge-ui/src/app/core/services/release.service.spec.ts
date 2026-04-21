import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ReleaseService } from './release.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Release, ReleaseBuildResponse } from '../models/release.model';

describe('ReleaseService', () => {
  let service: ReleaseService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  const mockRelease: Release = {
    id: 'r-1',
    projectId: 'p-1',
    version: '1.0.0',
    name: 'Test Release',
    status: 'DRAFT',
    artifactSizeBytes: 0,
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
        ReleaseService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settings }
      ]
    });
    service = TestBed.inject(ReleaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listByProject issues GET /releases?projectId=', () => {
    service.listByProject('p-1').subscribe(list => {
      expect(list.length).toBe(1);
      expect(list[0].id).toBe('r-1');
    });
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/releases` && r.params.get('projectId') === 'p-1');
    expect(req.request.method).toBe('GET');
    req.flush([mockRelease]);
  });

  it('get issues GET /releases/:id', () => {
    service.get('r-1').subscribe(r => expect(r.id).toBe('r-1'));
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockRelease);
  });

  it('create POSTs to /releases?projectId=', () => {
    service.create('p-1', {
      version: '1.0.0',
      name: 'rel',
      manifestRefs: { mappings: ['m-1'] }
    }).subscribe(r => expect(r.id).toBe('r-1'));
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/releases?projectId=p-1`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.version).toBe('1.0.0');
    req.flush(mockRelease);
  });

  it('build POSTs /releases/:id/build', () => {
    const resp: ReleaseBuildResponse = {
      releaseId: 'r-1',
      artifactUri: '/tmp/zip',
      artifactSizeBytes: 1024
    };
    service.build('r-1').subscribe(r => {
      expect(r.artifactSizeBytes).toBe(1024);
    });
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1/build`);
    expect(req.request.method).toBe('POST');
    req.flush(resp);
  });

  it('archive POSTs /releases/:id/archive', () => {
    service.archive('r-1').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1/archive`);
    expect(req.request.method).toBe('POST');
    req.flush(mockRelease);
  });

  it('delete DELETEs /releases/:id', () => {
    service.delete('r-1').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('getManifest GETs /releases/:id/manifest', () => {
    service.getManifest('r-1').subscribe(m => {
      expect(m['foo']).toBe('bar');
    });
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1/manifest`);
    req.flush({ foo: 'bar' });
  });

  it('download GETs /releases/:id/download as blob', () => {
    const blob = new Blob([new Uint8Array([0x50, 0x4b])], { type: 'application/zip' });
    service.download('r-1').subscribe(b => {
      expect(b.size).toBe(2);
    });
    const req = httpMock.expectOne(`${baseUrl}/releases/r-1/download`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(blob);
  });
});
