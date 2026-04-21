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

  it('listAll calls only the aggregated endpoint', () => {
    service.listAll().subscribe(list => expect(list).toEqual(sample));
    const req = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('listAll surfaces aggregator errors instead of silently fanning out', () => {
    let failed = false;
    service.listAll().subscribe({
      next: () => fail('should not emit on meta failure'),
      error: () => { failed = true; }
    });
    const meta = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    meta.flush('boom', { status: 500, statusText: 'Server Error' });
    expect(failed).toBe(true);
  });

  it('listByKind filters via the aggregated endpoint', () => {
    service.listByKind('FORMAT').subscribe(list => expect(list).toEqual(sample));
    const r = http.expectOne(`${environment.apiBaseUrl}/admin/extensions?kind=FORMAT`);
    expect(r.request.method).toBe('GET');
    r.flush(sample);
  });
});
