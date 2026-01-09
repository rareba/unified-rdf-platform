import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { PipelineService } from './pipeline.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Pipeline, Operation } from '../models';

describe('PipelineService', () => {
  let service: PipelineService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockPipeline: Pipeline = {
    id: 'pipeline-1',
    name: 'Test Pipeline',
    description: 'A test pipeline',
    status: 'active',
    stepsCount: 3,
    tags: ['test', 'example'],
    definition: '{"steps":[{"id":"s1"},{"id":"s2"},{"id":"s3"}]}',
    definitionFormat: 'JSON',
    variables: {},
    createdBy: 'user',
    createdAt: new Date(),
    updatedAt: new Date()
  };

  const mockOperations: Operation[] = [
    { id: 'load-csv', name: 'Load CSV', type: 'SOURCE', description: 'Load CSV file', parameters: {} },
    { id: 'transform', name: 'Transform', type: 'TRANSFORM', description: 'Transform data', parameters: {} }
  ];

  beforeEach(() => {
    settingsServiceMock = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        PipelineService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(PipelineService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of pipelines', () => {
      service.list().subscribe(pipelines => {
        expect(pipelines.length).toBe(1);
        expect(pipelines[0].id).toBe('pipeline-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockPipeline]);
    });

    it('should handle list params', () => {
      service.list({ search: 'test', status: 'active' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/pipelines` &&
        r.params.get('search') === 'test' &&
        r.params.get('status') === 'active'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('should enrich pipeline with calculated fields', () => {
      const pipelineWithoutFields = {
        id: 'p1',
        name: 'Test',
        definition: '{"steps":[{"id":"s1"}]}',
        definitionFormat: 'JSON',
        variables: {},
        createdBy: 'user',
        createdAt: new Date(),
        updatedAt: new Date()
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(1);
        expect(pipelines[0].status).toBe('active');
        expect(pipelines[0].tags).toEqual([]);
        expect(pipelines[0].description).toBe('');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipelineWithoutFields]);
    });
  });

  describe('get()', () => {
    it('should return a single pipeline by id', () => {
      service.get('pipeline-1').subscribe(pipeline => {
        expect(pipeline.id).toBe('pipeline-1');
        expect(pipeline.name).toBe('Test Pipeline');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPipeline);
    });
  });

  describe('create()', () => {
    it('should create a new pipeline', () => {
      const createData = { name: 'New Pipeline', definition: '{}', definitionFormat: 'JSON' as const };

      service.create(createData).subscribe(pipeline => {
        expect(pipeline.name).toBe('New Pipeline');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createData);
      req.flush({ ...mockPipeline, name: 'New Pipeline' });
    });
  });

  describe('update()', () => {
    it('should update a pipeline', () => {
      const updateData = { name: 'Updated Pipeline' };

      service.update('pipeline-1', updateData).subscribe(pipeline => {
        expect(pipeline.name).toBe('Updated Pipeline');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush({ ...mockPipeline, name: 'Updated Pipeline' });
    });
  });

  describe('delete()', () => {
    it('should delete a pipeline', () => {
      service.delete('pipeline-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('duplicate()', () => {
    it('should duplicate a pipeline', () => {
      service.duplicate('pipeline-1').subscribe(pipeline => {
        expect(pipeline.id).toBe('pipeline-2');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1/duplicate`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockPipeline, id: 'pipeline-2', name: 'Test Pipeline (Copy)' });
    });
  });

  describe('validate()', () => {
    it('should validate a pipeline definition', () => {
      const result = { valid: true, errors: [] };

      service.validate('{}', 'json').subscribe(r => {
        expect(r.valid).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/validate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ definition: '{}', format: 'json' });
      req.flush(result);
    });
  });

  describe('run()', () => {
    it('should run a pipeline', () => {
      service.run('pipeline-1', { key: 'value' }).subscribe(result => {
        expect(result.jobId).toBe('job-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1/run`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ variables: { key: 'value' } });
      req.flush({ jobId: 'job-1' });
    });
  });

  describe('getVersions()', () => {
    it('should return pipeline versions', () => {
      const versions = [
        { version: 1, createdAt: new Date(), createdBy: 'user' },
        { version: 2, createdAt: new Date(), createdBy: 'user' }
      ];

      service.getVersions('pipeline-1').subscribe(v => {
        expect(v.length).toBe(2);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines/pipeline-1/versions` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush(versions);
    });
  });

  describe('getVersion()', () => {
    it('should return a specific pipeline version', () => {
      service.getVersion('pipeline-1', 1).subscribe(pipeline => {
        expect(pipeline).toBeTruthy();
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1/versions/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPipeline);
    });
  });

  describe('getOperations()', () => {
    it('should return available operations', () => {
      service.getOperations().subscribe(operations => {
        expect(operations.length).toBe(2);
        expect(operations[0].id).toBe('load-csv');
      });

      const req = httpMock.expectOne(`${baseUrl}/operations`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOperations);
    });
  });

  describe('getOperation()', () => {
    it('should return a specific operation', () => {
      service.getOperation('load-csv').subscribe(op => {
        expect(op.id).toBe('load-csv');
        expect(op.type).toBe('SOURCE');
      });

      const req = httpMock.expectOne(`${baseUrl}/operations/load-csv`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOperations[0]);
    });
  });

  describe('step counting', () => {
    it('should count steps from JSON array definition', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: '[{"id":"s1"},{"id":"s2"}]'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(2);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });

    it('should handle empty definition', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: ''
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });

    it('should fallback to counting operation occurrences on parse error', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: 'invalid json with "operation" and another "operation"'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(2);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });
  });
});
