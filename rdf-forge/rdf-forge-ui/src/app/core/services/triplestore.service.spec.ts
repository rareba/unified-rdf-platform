import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TriplestoreService } from './triplestore.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { TriplestoreConnection, Graph, Resource, QueryResult } from '../models';
import { signal } from '@angular/core';

describe('TriplestoreService', () => {
  let service: TriplestoreService;
  let httpMock: HttpTestingController;
  let settingsServiceSpy: jasmine.SpyObj<SettingsService>;
  const baseUrl = environment.apiBaseUrl;

  const mockConnection: TriplestoreConnection = {
    id: 'conn-1',
    name: 'Test Triplestore',
    type: 'FUSEKI',
    url: 'http://localhost:3030',
    authType: 'none',
    isDefault: false,
    healthStatus: 'healthy',
    createdBy: 'user',
    createdAt: new Date()
  };

  const mockGraph: Graph = {
    uri: 'http://example.org/graph/1',
    name: 'Test Graph',
    tripleCount: 1000
  };

  beforeEach(() => {
    settingsServiceSpy = jasmine.createSpyObj('SettingsService', [], {
      defaultTriplestoreId: signal('conn-1'),
      sparqlResultLimit: signal(1000),
      pageSize: signal(20),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        TriplestoreService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceSpy }
      ]
    });
    service = TestBed.inject(TriplestoreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should expose default triplestore id from settings', () => {
    expect(service.defaultTriplestoreId()).toBe('conn-1');
  });

  it('should expose result limit from settings', () => {
    expect(service.resultLimit()).toBe(1000);
  });

  describe('list()', () => {
    it('should return a list of connections', () => {
      service.list().subscribe(connections => {
        expect(connections.length).toBe(1);
        expect(connections[0].id).toBe('conn-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/triplestores` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockConnection]);
    });
  });

  describe('get()', () => {
    it('should return a single connection by id', () => {
      service.get('conn-1').subscribe(conn => {
        expect(conn.id).toBe('conn-1');
        expect(conn.name).toBe('Test Triplestore');
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockConnection);
    });
  });

  describe('getDefault()', () => {
    it('should return the default connection', () => {
      service.getDefault().subscribe(conn => {
        expect(conn?.id).toBe('conn-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1`);
      req.flush(mockConnection);
    });

    it('should return null when no default is set', () => {
      // Reset with no default
      TestBed.resetTestingModule();
      const noDefaultSettings = jasmine.createSpyObj('SettingsService', [], {
        defaultTriplestoreId: signal(null),
        sparqlResultLimit: signal(1000),
        pageSize: signal(20),
        autoRetryFailed: signal(false),
        retryAttempts: signal(3)
      });
      TestBed.configureTestingModule({
        providers: [
          TriplestoreService,
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: SettingsService, useValue: noDefaultSettings }
        ]
      });
      const svc = TestBed.inject(TriplestoreService);

      svc.getDefault().subscribe(conn => {
        expect(conn).toBeNull();
      });
    });
  });

  describe('create()', () => {
    it('should create a new connection', () => {
      const createData = { name: 'New Connection', type: 'FUSEKI' as const, url: 'http://localhost:3030', authType: 'none' as const };

      service.create(createData).subscribe(conn => {
        expect(conn.name).toBe('New Connection');
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores`);
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockConnection, name: 'New Connection' });
    });
  });

  describe('update()', () => {
    it('should update a connection', () => {
      service.update('conn-1', { name: 'Updated' }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockConnection);
    });
  });

  describe('delete()', () => {
    it('should delete a connection', () => {
      service.delete('conn-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('test()', () => {
    it('should test connection health', () => {
      service.test('conn-1').subscribe(result => {
        expect(result.success).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1/health`);
      expect(req.request.method).toBe('GET');
      req.flush({ success: true, message: 'Connected', latencyMs: 50 });
    });
  });

  describe('connect()', () => {
    it('should connect to triplestore', () => {
      service.connect('conn-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1/connect`);
      expect(req.request.method).toBe('POST');
      req.flush(null);
    });
  });

  describe('getGraphs()', () => {
    it('should return graphs for connection', () => {
      service.getGraphs('conn-1').subscribe(graphs => {
        expect(graphs.length).toBe(1);
        expect(graphs[0].uri).toBe('http://example.org/graph/1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/triplestores/conn-1/graphs` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockGraph]);
    });
  });

  describe('getGraphResources()', () => {
    it('should return resources for graph', () => {
      const resources: Resource[] = [
        { uri: 'http://example.org/resource/1', label: 'Resource 1', types: [], properties: [] }
      ];

      service.getGraphResources('conn-1', 'http://example.org/graph/1').subscribe(r => {
        expect(r.length).toBe(1);
      });

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph/1')}/resources` &&
        r.params.has('limit') &&
        r.params.has('size')
      );
      expect(req.request.method).toBe('GET');
      req.flush(resources);
    });

    it('should use custom limit when provided', () => {
      service.getGraphResources('conn-1', 'http://example.org/graph/1', { limit: 50 }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph/1')}/resources` &&
        r.params.get('limit') === '50' &&
        r.params.has('size')
      );
      req.flush([]);
    });
  });

  describe('searchResources()', () => {
    it('should search resources in graph', () => {
      service.searchResources('conn-1', 'http://example.org/graph', 'test').subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph')}/search` &&
        r.params.get('q') === 'test' &&
        r.params.has('limit') &&
        r.params.has('size')
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('getResource()', () => {
    it('should get a specific resource', () => {
      const resource: Resource = { uri: 'http://example.org/r/1', label: 'Resource', types: [], properties: [] };

      service.getResource('conn-1', 'http://example.org/graph', 'http://example.org/r/1').subscribe(r => {
        expect(r.uri).toBe('http://example.org/r/1');
      });

      const req = httpMock.expectOne(
        `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph')}/resources/${encodeURIComponent('http://example.org/r/1')}`
      );
      req.flush(resource);
    });
  });

  describe('executeSparql()', () => {
    it('should execute SPARQL query', () => {
      const result: QueryResult = {
        variables: ['s', 'p', 'o'],
        bindings: [],
        executionTime: 100
      };

      service.executeSparql('conn-1', 'SELECT * WHERE { ?s ?p ?o }').subscribe(r => {
        expect(r.variables).toContain('s');
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1/sparql`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.query).toBe('SELECT * WHERE { ?s ?p ?o }');
      expect(req.request.body.limit).toBe(1000);
      req.flush(result);
    });
  });

  describe('executeSparqlOnDefault()', () => {
    it('should execute SPARQL on default triplestore', () => {
      service.executeSparqlOnDefault('SELECT * WHERE { ?s ?p ?o }').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1/sparql`);
      req.flush({ variables: [], bindings: [], executionTime: 0 });
    });
  });

  describe('uploadRdf()', () => {
    it('should upload RDF content', () => {
      const content = '@prefix : <http://example.org/> .\n:s :p :o .';

      service.uploadRdf('conn-1', 'http://example.org/graph', content, 'turtle').subscribe(result => {
        expect(result.triplesLoaded).toBe(1);
      });

      const req = httpMock.expectOne(`${baseUrl}/triplestores/conn-1/upload`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.content).toBe(content);
      expect(req.request.body.format).toBe('turtle');
      req.flush({ triplesLoaded: 1 });
    });
  });

  describe('deleteGraph()', () => {
    it('should delete a graph', () => {
      service.deleteGraph('conn-1', 'http://example.org/graph').subscribe();

      const req = httpMock.expectOne(
        `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph')}`
      );
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('exportGraph()', () => {
    it('should export graph content', () => {
      service.exportGraph('conn-1', 'http://example.org/graph', 'turtle').subscribe(content => {
        expect(content).toContain('@prefix');
      });

      const req = httpMock.expectOne(
        `${baseUrl}/triplestores/conn-1/graphs/${encodeURIComponent('http://example.org/graph')}/export?format=turtle`
      );
      expect(req.request.method).toBe('GET');
      req.flush('@prefix : <http://example.org/> .');
    });
  });
});
