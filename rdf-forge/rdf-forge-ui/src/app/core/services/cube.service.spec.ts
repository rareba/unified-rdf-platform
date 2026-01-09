import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { CubeService } from './cube.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Cube } from '../models/cube.model';

describe('CubeService', () => {
  let service: CubeService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockCube: Cube = {
    id: 'cube-1',
    uri: 'http://example.org/cube/1',
    name: 'Test Cube',
    createdAt: new Date()
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
        CubeService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(CubeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of cubes', () => {
      const mockCubes: Cube[] = [mockCube];

      service.list().subscribe(cubes => {
        expect(cubes.length).toBe(1);
        expect(cubes[0].id).toBe('cube-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/cubes` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(mockCubes);
    });

    it('should handle list params', () => {
      service.list({ projectId: 'proj-1', search: 'test' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/cubes` &&
        r.params.get('projectId') === 'proj-1' &&
        r.params.get('search') === 'test'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single cube by id', () => {
      service.get('cube-1').subscribe(cube => {
        expect(cube.id).toBe('cube-1');
        expect(cube.name).toBe('Test Cube');
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockCube);
    });
  });

  describe('create()', () => {
    it('should create a new cube', () => {
      const createData = { uri: 'http://example.org/cube/2', name: 'New Cube' };

      service.create(createData).subscribe(cube => {
        expect(cube.name).toBe('New Cube');
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createData);
      req.flush({ ...mockCube, name: 'New Cube' });
    });
  });

  describe('update()', () => {
    it('should update a cube', () => {
      const updateData = { name: 'Updated Cube' };

      service.update('cube-1', updateData).subscribe(cube => {
        expect(cube.name).toBe('Updated Cube');
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush({ ...mockCube, name: 'Updated Cube' });
    });
  });

  describe('delete()', () => {
    it('should delete a cube', () => {
      service.delete('cube-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('publish()', () => {
    it('should publish a cube', () => {
      service.publish('cube-1').subscribe(cube => {
        expect(cube).toBeTruthy();
        expect(cube.lastPublished).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/publish`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockCube, lastPublished: new Date() });
    });
  });

  describe('generateShape()', () => {
    it('should generate a SHACL shape from cube', () => {
      const result = { id: 'shape-1', name: 'Test Shape', type: 'SHACL_SHAPE' as const };

      service.generateShape('cube-1').subscribe(artifact => {
        expect(artifact.id).toBe('shape-1');
        expect(artifact.type).toBe('SHACL_SHAPE');
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/generate-shape`);
      expect(req.request.method).toBe('POST');
      req.flush(result);
    });

    it('should pass request options when generating shape', () => {
      const request = { name: 'Custom Shape', targetClass: 'http://example.org/MyClass' };

      service.generateShape('cube-1', request).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/generate-shape`);
      expect(req.request.body).toEqual(request);
      req.flush({ id: 'shape-1', name: 'Custom Shape', type: 'SHACL_SHAPE' });
    });
  });

  describe('generatePipeline()', () => {
    it('should generate a pipeline from cube', () => {
      const result = { id: 'pipeline-1', name: 'Test Pipeline', type: 'PIPELINE' as const };

      service.generatePipeline('cube-1').subscribe(artifact => {
        expect(artifact.id).toBe('pipeline-1');
        expect(artifact.type).toBe('PIPELINE');
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/generate-pipeline`);
      expect(req.request.method).toBe('POST');
      req.flush(result);
    });
  });

  describe('linkShape()', () => {
    it('should link a shape to the cube', () => {
      service.linkShape('cube-1', 'shape-1').subscribe(cube => {
        expect(cube).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/shape/shape-1`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockCube);
    });
  });

  describe('linkPipeline()', () => {
    it('should link a pipeline to the cube', () => {
      service.linkPipeline('cube-1', 'pipeline-1').subscribe(cube => {
        expect(cube).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/pipeline/pipeline-1`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockCube);
    });
  });

  describe('unlinkShape()', () => {
    it('should unlink the shape from the cube', () => {
      service.unlinkShape('cube-1').subscribe(cube => {
        expect(cube).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/shape`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockCube);
    });
  });

  describe('unlinkPipeline()', () => {
    it('should unlink the pipeline from the cube', () => {
      service.unlinkPipeline('cube-1').subscribe(cube => {
        expect(cube).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/cubes/cube-1/pipeline`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockCube);
    });
  });
});
