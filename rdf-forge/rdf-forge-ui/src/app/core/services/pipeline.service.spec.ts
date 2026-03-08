import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { PipelineService } from './pipeline.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Pipeline, Operation, PipelineVersion, PipelineValidationResult } from '../models';

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

    it('should handle pagination params', () => {
      service.list({ page: 2, limit: 10 }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/pipelines` &&
        r.params.get('page') === '2' &&
        r.params.get('limit') === '10'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('should handle empty list', () => {
      service.list().subscribe(pipelines => {
        expect(pipelines.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
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

    it('should handle pipeline not found', () => {
      service.get('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });

    it('should enrich pipeline with calculated fields', () => {
      const partialPipeline = {
        id: 'p1',
        name: 'Test',
        definition: '{"steps":[{"id":"s1"}]}',
        definitionFormat: 'JSON',
        variables: {},
        createdBy: 'user',
        createdAt: new Date(),
        updatedAt: new Date()
      };

      service.get('p1').subscribe(pipeline => {
        expect(pipeline.stepsCount).toBe(1);
        expect(pipeline.status).toBe('active');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/p1`);
      req.flush(partialPipeline);
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

    it('should create a pipeline with description', () => {
      const createData = { 
        name: 'New Pipeline', 
        description: 'A description',
        definition: '{}', 
        definitionFormat: 'JSON' as const 
      };

      service.create(createData).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines`);
      expect(req.request.body).toEqual(createData);
      req.flush(mockPipeline);
    });

    it('should create a pipeline with tags', () => {
      const createData = { 
        name: 'Tagged Pipeline', 
        definition: '{}', 
        definitionFormat: 'JSON' as const,
        tags: ['tag1', 'tag2']
      };

      service.create(createData).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines`);
      expect(req.request.body).toEqual(createData);
      req.flush(mockPipeline);
    });

    it('should create a pipeline with variables', () => {
      const createData = { 
        name: 'Pipeline with vars', 
        definition: '{}', 
        definitionFormat: 'JSON' as const,
        variables: { key1: 'value1', key2: 123 }
      };

      service.create(createData).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines`);
      expect(req.request.body).toEqual(createData);
      req.flush(mockPipeline);
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

    it('should update multiple fields', () => {
      const updateData = { 
        name: 'Updated Name',
        description: 'Updated Description',
        definition: '{"steps":[]}'
      };

      service.update('pipeline-1', updateData).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      expect(req.request.body).toEqual(updateData);
      req.flush(mockPipeline);
    });

    it('should handle update for non-existent pipeline', () => {
      service.update('non-existent', { name: 'New Name' }).subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('delete()', () => {
    it('should delete a pipeline', () => {
      service.delete('pipeline-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should handle delete for non-existent pipeline', () => {
      service.delete('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });

    it('should handle delete for running pipeline', () => {
      service.delete('running-pipeline').subscribe({
        error: (error) => {
          expect(error.status).toBe(409);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/running-pipeline`);
      req.flush('Pipeline is running', { status: 409, statusText: 'Conflict' });
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

    it('should handle duplicate for non-existent pipeline', () => {
      service.duplicate('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/non-existent/duplicate`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('validate()', () => {
    it('should validate a pipeline definition in JSON format', () => {
      const result: PipelineValidationResult = { valid: true, errors: [] };

      service.validate('{}', 'json').subscribe(r => {
        expect(r.valid).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/validate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ definition: '{}', format: 'json' });
      req.flush(result);
    });

    it('should validate a pipeline definition in YAML format', () => {
      const yaml = 'steps:\n  - id: step1';
      const result: PipelineValidationResult = { valid: true, errors: [] };

      service.validate(yaml, 'yaml').subscribe(r => {
        expect(r.valid).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/validate`);
      expect(req.request.body).toEqual({ definition: yaml, format: 'yaml' });
      req.flush(result);
    });

    it('should validate a pipeline definition in Turtle format', () => {
      const turtle = '@prefix ex: <http://example.org/> .';
      const result: PipelineValidationResult = { valid: true, errors: [] };

      service.validate(turtle, 'turtle').subscribe(r => {
        expect(r.valid).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/validate`);
      expect(req.request.body).toEqual({ definition: turtle, format: 'turtle' });
      req.flush(result);
    });

    it('should handle validation errors', () => {
      const result: PipelineValidationResult = { 
        valid: false, 
        errors: ['Missing required step', 'Invalid connection'] 
      };

      service.validate('{}', 'json').subscribe(r => {
        expect(r.valid).toBeFalse();
        expect(r.errors.length).toBe(2);
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/validate`);
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

    it('should run a pipeline without variables', () => {
      service.run('pipeline-1').subscribe(result => {
        expect(result.jobId).toBe('job-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1/run`);
      expect(req.request.body).toEqual({ variables: {} });
      req.flush({ jobId: 'job-1' });
    });

    it('should handle run for non-existent pipeline', () => {
      service.run('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/non-existent/run`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });

    it('should handle run for invalid pipeline', () => {
      service.run('invalid-pipeline').subscribe({
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/invalid-pipeline/run`);
      req.flush('Invalid pipeline definition', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('getVersions()', () => {
    it('should return pipeline versions', () => {
      const versions: PipelineVersion[] = [
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

    it('should handle empty versions', () => {
      service.getVersions('pipeline-1').subscribe(v => {
        expect(v.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines/pipeline-1/versions` && r.params.has('size'));
      req.flush([]);
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

    it('should handle non-existent version', () => {
      service.getVersion('pipeline-1', 999).subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1/versions/999`);
      req.flush('Version not found', { status: 404, statusText: 'Not Found' });
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

    it('should handle empty operations', () => {
      service.getOperations().subscribe(operations => {
        expect(operations.length).toBe(0);
      });

      const req = httpMock.expectOne(`${baseUrl}/operations`);
      req.flush([]);
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

    it('should handle non-existent operation', () => {
      service.getOperation('non-existent').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/operations/non-existent`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
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

    it('should count steps from JSON object definition', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: '{"steps":[{"id":"s1"},{"id":"s2"},{"id":"s3"}]}'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(3);
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

    it('should handle null definition', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: null as unknown as string
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

    it('should return 0 when no operation occurrences found', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: undefined,
        definition: 'invalid json without operation keyword'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });
  });

  describe('enrichment', () => {
    it('should use existing stepsCount when available', () => {
      const pipeline = {
        ...mockPipeline,
        stepsCount: 10,
        definition: '{"steps":[]}'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].stepsCount).toBe(10);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });

    it('should use existing status when available', () => {
      const pipeline = {
        ...mockPipeline,
        status: 'draft' as const
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].status).toBe('draft');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });

    it('should use existing tags when available', () => {
      const pipeline = {
        ...mockPipeline,
        tags: ['custom', 'tags']
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].tags).toEqual(['custom', 'tags']);
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });

    it('should use existing description when available', () => {
      const pipeline = {
        ...mockPipeline,
        description: 'Custom description'
      };

      service.list().subscribe(pipelines => {
        expect(pipelines[0].description).toBe('Custom description');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush([pipeline]);
    });
  });

  describe('error handling', () => {
    it('should handle server errors', () => {
      service.list().subscribe({
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/pipelines` && r.params.has('size'));
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });
    });

    it('should handle network errors', () => {
      service.get('pipeline-1').subscribe({
        error: (error) => {
          expect(error).toBeTruthy();
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      req.error(new ProgressEvent('Network error'));
    });

    it('should handle unauthorized errors', () => {
      service.create({ name: 'Test', definition: '{}', definitionFormat: 'JSON' }).subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines`);
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should handle forbidden errors', () => {
      service.delete('pipeline-1').subscribe({
        error: (error) => {
          expect(error.status).toBe(403);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/pipelines/pipeline-1`);
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    });
  });
});
