import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ProviderService } from './provider.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { TriplestoreProviderInfo, DataFormatInfo, StorageProviderInfo, DestinationInfo } from '../models';

describe('ProviderService', () => {
  let service: ProviderService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockTriplestoreProvider: TriplestoreProviderInfo = {
    type: 'FUSEKI',
    displayName: 'Apache Fuseki',
    description: 'Apache Jena Fuseki',
    vendor: 'Apache',
    documentationUrl: 'https://jena.apache.org/documentation/fuseki2/',
    configFields: {},
    capabilities: ['SPARQL_1_1', 'GRAPH_STORE']
  };

  const mockDataFormat: DataFormatInfo = {
    format: 'CSV',
    displayName: 'Comma-Separated Values',
    description: 'CSV file format',
    extensions: ['.csv'],
    mimeTypes: ['text/csv'],
    options: {},
    capabilities: ['read', 'write']
  };

  const mockStorageProvider: StorageProviderInfo = {
    type: 'local',
    displayName: 'Local Storage',
    description: 'Local file system storage',
    vendor: 'System',
    documentationUrl: '',
    configFields: {},
    capabilities: ['read', 'write']
  };

  const mockDestination: DestinationInfo = {
    type: 'triplestore',
    displayName: 'Triplestore',
    description: 'RDF Triplestore',
    category: 'triplestore',
    supportedFormats: ['TURTLE', 'NTRIPLES'],
    configFields: {},
    capabilities: ['graph-store']
  };

  beforeEach(() => {
    settingsServiceMock = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        ProviderService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(ProviderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getTriplestoreProviders()', () => {
    it('should return triplestore providers', () => {
      service.getTriplestoreProviders().subscribe(providers => {
        expect(providers.length).toBe(1);
        expect(providers[0].type).toBe('FUSEKI');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/triplestores/providers` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockTriplestoreProvider]);
    });

    it('should cache providers', () => {
      service.getTriplestoreProviders().subscribe();
      const req1 = httpMock.expectOne(r => r.url === `${baseUrl}/triplestores/providers`);
      req1.flush([mockTriplestoreProvider]);

      // Second call should use cache
      service.getTriplestoreProviders().subscribe(providers => {
        expect(providers.length).toBe(1);
      });

      httpMock.expectNone(r => r.url === `${baseUrl}/triplestores/providers`);
    });
  });

  describe('getTriplestoreProvider()', () => {
    it('should return a specific triplestore provider', () => {
      service.getTriplestoreProvider('FUSEKI').subscribe(provider => {
        expect(provider.type).toBe('FUSEKI');
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/providers/FUSEKI`);
      expect(req.request.method).toBe('GET');
      req.flush(mockTriplestoreProvider);
    });
  });

  describe('getDataFormats()', () => {
    it('should return data formats', () => {
      service.getDataFormats().subscribe(formats => {
        expect(formats.length).toBe(1);
        expect(formats[0].format).toBe('CSV');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/data/formats` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockDataFormat]);
    });

    it('should cache data formats', () => {
      service.getDataFormats().subscribe();
      const req1 = httpMock.expectOne(r => r.url === `${baseUrl}/data/formats`);
      req1.flush([mockDataFormat]);

      service.getDataFormats().subscribe();
      httpMock.expectNone(r => r.url === `${baseUrl}/data/formats`);
    });
  });

  describe('getDataFormat()', () => {
    it('should return a specific data format', () => {
      service.getDataFormat('CSV').subscribe(format => {
        expect(format.format).toBe('CSV');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/formats/CSV`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDataFormat);
    });
  });

  describe('getDataFormatByExtension()', () => {
    it('should return data format by extension', () => {
      service.getDataFormatByExtension('.csv').subscribe(format => {
        expect(format.format).toBe('CSV');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/formats/by-extension/.csv`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDataFormat);
    });
  });

  describe('getStorageProviders()', () => {
    it('should return storage providers', () => {
      service.getStorageProviders().subscribe(providers => {
        expect(providers.length).toBe(1);
        expect(providers[0].type).toBe('local');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/data/storage/providers` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockStorageProvider]);
    });
  });

  describe('getActiveStorageProvider()', () => {
    it('should return the active storage provider', () => {
      service.getActiveStorageProvider().subscribe(result => {
        expect(result.type).toBe('local');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/storage/providers/active`);
      expect(req.request.method).toBe('GET');
      req.flush({ type: 'local', provider: mockStorageProvider });
    });
  });

  describe('getDestinations()', () => {
    it('should return destinations', () => {
      service.getDestinations().subscribe(destinations => {
        expect(destinations.length).toBe(1);
        expect(destinations[0].type).toBe('triplestore');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/destinations` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockDestination]);
    });
  });

  describe('getDestinationsByCategory()', () => {
    it('should return destinations grouped by category', () => {
      service.getDestinationsByCategory().subscribe(result => {
        expect(result['graph-database'].length).toBe(1);
      });

      const req = httpMock.expectOne(`${baseUrl}/destinations/by-category`);
      expect(req.request.method).toBe('GET');
      req.flush({ 'graph-database': [mockDestination] });
    });
  });

  describe('getDestination()', () => {
    it('should return a specific destination', () => {
      service.getDestination('triplestore').subscribe(dest => {
        expect(dest.type).toBe('triplestore');
      });

      const req = httpMock.expectOne(`${baseUrl}/destinations/triplestore`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDestination);
    });
  });

  describe('getSupportedFormats()', () => {
    it('should return supported formats', () => {
      service.getSupportedFormats().subscribe(formats => {
        expect(formats).toContain('TURTLE');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/destinations/formats` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(['TURTLE', 'NTRIPLES', 'JSONLD']);
    });
  });

  describe('getDestinationsByFormat()', () => {
    it('should return destinations by format', () => {
      service.getDestinationsByFormat('TURTLE').subscribe(destinations => {
        expect(destinations.length).toBe(1);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/destinations/by-format/TURTLE` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockDestination]);
    });
  });

  describe('validateDestinationConfig()', () => {
    it('should validate destination config', () => {
      const config = { url: 'http://localhost:3030' };

      service.validateDestinationConfig('triplestore', config).subscribe(result => {
        expect(result.conforms).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/destinations/triplestore/validate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(config);
      req.flush({ conforms: true, violationCount: 0, violations: [], executionTime: 10 });
    });
  });

  describe('checkDestinationAvailability()', () => {
    it('should check destination availability', () => {
      const config = { url: 'http://localhost:3030' };

      service.checkDestinationAvailability('triplestore', config).subscribe(result => {
        expect(result.available).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/destinations/triplestore/check-availability`);
      expect(req.request.method).toBe('POST');
      req.flush({ available: true, message: 'Connected' });
    });
  });

  describe('getDestinationCategories()', () => {
    it('should return destination categories', () => {
      service.getDestinationCategories().subscribe(categories => {
        expect(categories).toContain('triplestore');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/destinations/categories` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(['triplestore', 'file', 'cloud-storage']);
    });
  });

  describe('clearCache()', () => {
    it('should clear all cached data', () => {
      // First, load some data to populate cache
      service.getTriplestoreProviders().subscribe();
      httpMock.expectOne(r => r.url === `${baseUrl}/triplestores/providers`).flush([mockTriplestoreProvider]);

      service.getDataFormats().subscribe();
      httpMock.expectOne(r => r.url === `${baseUrl}/data/formats`).flush([mockDataFormat]);

      // Clear cache
      service.clearCache();

      // Now both should make new requests
      service.getTriplestoreProviders().subscribe();
      httpMock.expectOne(r => r.url === `${baseUrl}/triplestores/providers`).flush([mockTriplestoreProvider]);

      service.getDataFormats().subscribe();
      httpMock.expectOne(r => r.url === `${baseUrl}/data/formats`).flush([mockDataFormat]);
    });
  });
});
