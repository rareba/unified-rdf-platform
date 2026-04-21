import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ExtensionService } from './extension.service';
import { environment } from '../../../environments/environment';
import { ExtensionDescriptor } from '../models/extension.model';

describe('ExtensionService', () => {
  let service: ExtensionService;
  let http: HttpTestingController;

  const sample: ExtensionDescriptor[] = [
    {
      id: 'csv',
      kind: 'FORMAT',
      name: 'CSV',
      version: '1.0',
      description: 'Comma-separated values',
      capabilities: ['preview'],
      parameters: {},
      providedBy: 'rdf-forge-data-service',
      docUrl: null,
      available: true
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ExtensionService]
    });
    service = TestBed.inject(ExtensionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listAll calls the aggregated endpoint first', () => {
    service.listAll().subscribe(list => expect(list).toEqual(sample));
    const req = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('listAll falls back to per-service fan-out on meta failure', () => {
    service.listAll().subscribe(list => {
      expect(list).toEqual(sample);
    });
    const meta = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    meta.flush('boom', { status: 500, statusText: 'Server Error' });

    // Eight per-kind endpoints are called in parallel — at least one succeeds.
    const endpoints = [
      '/extensions/operations',
      '/extensions/formats',
      '/extensions/storage-providers',
      '/extensions/destinations',
      '/extensions/triplestore-providers',
      '/extensions/validators',
      '/extensions/cube-profiles',
      '/extensions/matchers'
    ];
    for (const ep of endpoints) {
      const r = http.expectOne(`${environment.apiBaseUrl}${ep}`);
      r.flush(ep === '/extensions/formats' ? sample : []);
    }
  });

  it('listByKind hits the kind-specific endpoint', () => {
    service.listByKind('FORMAT').subscribe(list => expect(list).toEqual(sample));
    const r = http.expectOne(`${environment.apiBaseUrl}/extensions/formats`);
    expect(r.request.method).toBe('GET');
    r.flush(sample);
  });
});
