import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ShaclService } from './shacl.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Shape, ValidationResult } from '../models';

describe('ShaclService', () => {
  let service: ShaclService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockShape: Shape = {
    id: 'shape-1',
    uri: 'http://example.org/shapes/1',
    name: 'Test Shape',
    content: '@prefix sh: <http://www.w3.org/ns/shacl#> .',
    contentFormat: 'turtle',
    category: 'validation',
    tags: [],
    isTemplate: false,
    version: 1,
    createdBy: 'user',
    createdAt: new Date(),
    updatedAt: new Date()
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
        ShaclService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(ShaclService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of shapes', () => {
      service.list().subscribe(shapes => {
        expect(shapes.length).toBe(1);
        expect(shapes[0].id).toBe('shape-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/shapes` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockShape]);
    });

    it('should handle list params', () => {
      service.list({ search: 'test', category: 'validation' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/shapes` &&
        r.params.get('search') === 'test' &&
        r.params.get('category') === 'validation'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single shape by id', () => {
      service.get('shape-1').subscribe(shape => {
        expect(shape.id).toBe('shape-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/shape-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockShape);
    });
  });

  describe('create()', () => {
    it('should create a new shape', () => {
      const createData = { uri: 'http://example.org/shapes/new', name: 'New Shape', content: '...', contentFormat: 'turtle' as const };

      service.create(createData).subscribe(shape => {
        expect(shape.name).toBe('New Shape');
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockShape, name: 'New Shape' });
    });
  });

  describe('update()', () => {
    it('should update a shape', () => {
      service.update('shape-1', { name: 'Updated' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/shapes/shape-1`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockShape);
    });
  });

  describe('delete()', () => {
    it('should delete a shape', () => {
      service.delete('shape-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/shapes/shape-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('validateSyntax()', () => {
    it('should validate shape syntax', () => {
      service.validateSyntax('@prefix sh: ...', 'turtle').subscribe(result => {
        expect(result.valid).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/validate-syntax`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.content).toBe('@prefix sh: ...');
      expect(req.request.body.format).toBe('turtle');
      req.flush({ valid: true, errors: [] });
    });
  });

  describe('runValidation()', () => {
    it('should run validation against data', () => {
      const result: ValidationResult = {
        conforms: true,
        violationCount: 0,
        violations: [],
        executionTime: 100
      };

      service.runValidation('shape-1', '@prefix : <http://example.org/> .', 'turtle').subscribe(r => {
        expect(r.conforms).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/validation/run`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.shapeId).toBe('shape-1');
      req.flush(result);
    });
  });

  describe('runValidationOnGraph()', () => {
    it('should run validation on triplestore graph', () => {
      service.runValidationOnGraph('shape-1', 'conn-1', 'http://example.org/graph').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/validation/run`);
      expect(req.request.body.triplestoreId).toBe('conn-1');
      expect(req.request.body.graphUri).toBe('http://example.org/graph');
      req.flush({ conforms: true, violationCount: 0, violations: [], executionTime: 50 });
    });
  });

  describe('inferShape()', () => {
    it('should infer shape from data', () => {
      service.inferShape('@prefix : <http://example.org/> .', 'turtle').subscribe(shape => {
        expect(shape).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/infer`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.data).toContain('@prefix');
      req.flush(mockShape);
    });

    it('should pass target class option', () => {
      service.inferShape('data', 'turtle', { targetClass: 'http://example.org/MyClass' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/shapes/infer`);
      expect(req.request.body.targetClass).toBe('http://example.org/MyClass');
      req.flush(mockShape);
    });
  });

  describe('generateTurtle()', () => {
    it('should generate turtle from node shape', () => {
      const nodeShape = {
        uri: 'http://example.org/MyShape',
        targetClass: 'http://example.org/MyClass',
        properties: []
      };

      service.generateTurtle(nodeShape).subscribe(turtle => {
        expect(turtle).toContain('@prefix');
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/generate`);
      expect(req.request.method).toBe('POST');
      req.flush('@prefix sh: <http://www.w3.org/ns/shacl#> .');
    });
  });

  describe('getVersions()', () => {
    it('should return shape versions', () => {
      service.getVersions('shape-1').subscribe(versions => {
        expect(versions.length).toBe(2);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/shapes/shape-1/versions` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([
        { version: 1, createdAt: new Date() },
        { version: 2, createdAt: new Date() }
      ]);
    });
  });

  describe('getTemplates()', () => {
    it('should return shape templates', () => {
      service.getTemplates().subscribe(templates => {
        expect(templates.length).toBe(1);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/templates/shapes` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockShape]);
    });
  });

  describe('getProfiles()', () => {
    it('should return validation profiles', () => {
      const profiles = [
        { id: 'standalone', name: 'Standalone', description: 'Standalone profile' },
        { id: 'visualize', name: 'Visualize', description: 'Visualize profile' }
      ];

      service.getProfiles().subscribe(p => {
        expect(p.length).toBe(2);
        expect(p[0].id).toBe('standalone');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/shapes/profiles` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(profiles);
    });
  });

  describe('validateAgainstProfile()', () => {
    it('should validate data against a profile', () => {
      service.validateAgainstProfile({
        profile: 'standalone',
        dataContent: '@prefix ...',
        dataFormat: 'turtle'
      }).subscribe(result => {
        expect(result.conforms).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/validate-profile`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.profile).toBe('standalone');
      req.flush({ conforms: true, violationCount: 0, violations: [], executionTime: 25 });
    });
  });

  describe('validateAgainstAllProfiles()', () => {
    it('should validate data against all profiles', () => {
      service.validateAgainstAllProfiles('@prefix ...', 'turtle').subscribe(results => {
        expect(results['standalone'].conforms).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/shapes/validate-all-profiles`);
      expect(req.request.method).toBe('POST');
      req.flush({
        standalone: { conforms: true, violationCount: 0, violations: [], executionTime: 20 },
        visualize: { conforms: false, violationCount: 1, violations: [{ focusNode: 'ex:node', message: 'Missing property', severity: 'Violation', constraint: 'minCount', sourceShape: 'ex:shape' }], executionTime: 30 }
      });
    });
  });
});
