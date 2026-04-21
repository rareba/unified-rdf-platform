import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ExtensionCatalog } from './extension-catalog';
import { environment } from '../../../environments/environment';
import { ExtensionDescriptor } from '../../core/models';

describe('ExtensionCatalog', () => {
  let fixture: ComponentFixture<ExtensionCatalog>;
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
      available: true
    },
    {
      id: 'load',
      kind: 'OPERATION',
      name: 'Load',
      version: '1.1',
      description: 'Loads data',
      capabilities: ['source'],
      parameters: { 'uri': 'string (required)' },
      providedBy: 'rdf-forge-engine',
      available: true
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExtensionCatalog, HttpClientTestingModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ExtensionCatalog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads extensions and exposes tabs per kind', () => {
    const req = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    req.flush(sample);
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    expect(cmp.all().length).toBe(2);
    expect(cmp.countFor('FORMAT')).toBe(1);
    expect(cmp.countFor('OPERATION')).toBe(1);
    expect(cmp.tabs().some(t => t.kind === 'FORMAT' && t.items.length === 1)).toBeTrue();
  });

  it('filters by free-text', () => {
    const req = http.expectOne(`${environment.apiBaseUrl}/admin/extensions`);
    req.flush(sample);
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    cmp.filter.set('load');
    expect(cmp.filteredFor('OPERATION').length).toBe(1);
    expect(cmp.filteredFor('FORMAT').length).toBe(0);
  });
});
