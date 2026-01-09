import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { DimensionService } from './dimension.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Dimension, DimensionValue, Hierarchy, DimensionStats } from '../models';

describe('DimensionService', () => {
  let service: DimensionService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockDimension: Dimension = {
    id: 'dim-1',
    uri: 'http://example.org/dimension/1',
    name: 'Test Dimension',
    type: 'KEY',
    description: 'A test dimension',
    valueCount: 10,
    createdAt: '2024-01-01T00:00:00Z'
  };

  const mockValue: DimensionValue = {
    id: 'val-1',
    dimensionId: 'dim-1',
    uri: 'http://example.org/value/1',
    code: 'V1',
    label: 'Test Value'
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
        DimensionService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(DimensionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of dimensions', () => {
      service.list().subscribe(dimensions => {
        expect(dimensions.length).toBe(1);
        expect(dimensions[0].id).toBe('dim-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/dimensions` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockDimension]);
    });

    it('should handle list params', () => {
      service.list({ projectId: 'proj-1', type: 'TEMPORAL' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/dimensions` &&
        r.params.get('projectId') === 'proj-1' &&
        r.params.get('type') === 'TEMPORAL'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single dimension by id', () => {
      service.get('dim-1').subscribe(dimension => {
        expect(dimension.id).toBe('dim-1');
        expect(dimension.name).toBe('Test Dimension');
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDimension);
    });
  });

  describe('create()', () => {
    it('should create a new dimension', () => {
      service.create(mockDimension).subscribe(dimension => {
        expect(dimension.name).toBe('Test Dimension');
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockDimension);
      req.flush(mockDimension);
    });
  });

  describe('update()', () => {
    it('should update a dimension', () => {
      const updateData = { name: 'Updated Dimension' };

      service.update('dim-1', updateData).subscribe(dimension => {
        expect(dimension.name).toBe('Updated Dimension');
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush({ ...mockDimension, name: 'Updated Dimension' });
    });
  });

  describe('delete()', () => {
    it('should delete a dimension', () => {
      service.delete('dim-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('getValues()', () => {
    it('should return dimension values', () => {
      service.getValues('dim-1').subscribe(values => {
        expect(values.length).toBe(1);
        expect(values[0].label).toBe('Test Value');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/dimensions/dim-1/values` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockValue]);
    });

    it('should handle value params', () => {
      service.getValues('dim-1', { search: 'test', limit: 50 }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/dimensions/dim-1/values` &&
        r.params.get('search') === 'test' &&
        r.params.get('limit') === '50'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('addValue()', () => {
    it('should add a value to dimension', () => {
      service.addValue('dim-1', mockValue).subscribe(value => {
        expect(value.label).toBe('Test Value');
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1/values`);
      expect(req.request.method).toBe('POST');
      req.flush(mockValue);
    });
  });

  describe('updateValue()', () => {
    it('should update a dimension value', () => {
      service.updateValue('val-1', { label: 'Updated' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/dimensions/values/val-1`);
      expect(req.request.method).toBe('PUT');
      req.flush({ ...mockValue, label: 'Updated' });
    });
  });

  describe('deleteValue()', () => {
    it('should delete a dimension value', () => {
      service.deleteValue('val-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/dimensions/values/val-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('importCsv()', () => {
    it('should import values from CSV', () => {
      const file = new File(['label\nValue1\nValue2'], 'values.csv', { type: 'text/csv' });

      service.importCsv('dim-1', file).subscribe(result => {
        expect(result.imported).toBe(2);
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1/import/csv`);
      expect(req.request.method).toBe('POST');
      req.flush({ imported: 2 });
    });
  });

  describe('exportTurtle()', () => {
    it('should export dimension as Turtle', () => {
      const turtle = '@prefix : <http://example.org/> .\n:dim-1 a :Dimension .';

      service.exportTurtle('dim-1').subscribe(blob => {
        expect(blob).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/dim-1/export/turtle`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob([turtle], { type: 'text/turtle' }));
    });
  });

  describe('getTree()', () => {
    it('should return dimension value tree', () => {
      const tree = [{ ...mockValue, children: [] }];

      service.getTree('dim-1').subscribe(values => {
        expect(values.length).toBe(1);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/dimensions/dim-1/tree` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(tree);
    });
  });

  describe('getStats()', () => {
    it('should return dimension stats', () => {
      const stats: DimensionStats = {
        totalDimensions: 5,
        totalValues: 100,
        byType: { TEMPORAL: 2, GEO: 0, KEY: 3, MEASURE: 0, ATTRIBUTE: 0, CODED: 0 }
      };

      service.getStats('proj-1').subscribe(s => {
        expect(s.totalDimensions).toBe(5);
      });

      const req = httpMock.expectOne(`${baseUrl}/dimensions/stats?projectId=proj-1`);
      expect(req.request.method).toBe('GET');
      req.flush(stats);
    });
  });

  describe('listHierarchies()', () => {
    it('should return hierarchies for dimension', () => {
      const hierarchies: Hierarchy[] = [
        { id: 'hier-1', uri: 'http://example.org/hier/1', name: 'Test Hierarchy', dimensionId: 'dim-1', hierarchyType: 'SKOS_CONCEPT_SCHEME' }
      ];

      service.listHierarchies('dim-1').subscribe(h => {
        expect(h.length).toBe(1);
      });

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/hierarchies` &&
        r.params.get('dimensionId') === 'dim-1' &&
        r.params.has('size')
      );
      expect(req.request.method).toBe('GET');
      req.flush(hierarchies);
    });
  });

  describe('createHierarchy()', () => {
    it('should create a hierarchy', () => {
      const hierarchy: Hierarchy = { id: 'hier-1', uri: 'http://example.org/hier/1', name: 'New Hierarchy', dimensionId: 'dim-1', hierarchyType: 'SKOS_CONCEPT_SCHEME' };

      service.createHierarchy(hierarchy).subscribe(h => {
        expect(h.name).toBe('New Hierarchy');
      });

      const req = httpMock.expectOne(`${baseUrl}/hierarchies`);
      expect(req.request.method).toBe('POST');
      req.flush(hierarchy);
    });
  });

  describe('exportSkos()', () => {
    it('should export hierarchy as SKOS', () => {
      service.exportSkos('hier-1', 'dim-1').subscribe(blob => {
        expect(blob).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/hierarchies/hier-1/export/skos?dimensionId=dim-1`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['<rdf>'], { type: 'application/rdf+xml' }));
    });
  });
});
