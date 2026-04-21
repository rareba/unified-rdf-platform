import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ProjectService } from './project.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { Project, ProjectSummary } from '../models';

describe('ProjectService', () => {
  let service: ProjectService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockProject: Project = {
    id: 'proj-1',
    name: 'Test Project',
    description: 'A test project',
    baseUri: 'https://example.org/proj-1/',
    status: 'ACTIVE',
    createdBy: 'user',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
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
        ProjectService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(ProjectService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of projects', () => {
      service.list().subscribe(projects => {
        expect(projects.length).toBe(1);
        expect(projects[0].id).toBe('proj-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/projects` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockProject]);
    });

    it('should pass status query param when provided', () => {
      service.list('ACTIVE').subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/projects` && r.params.get('status') === 'ACTIVE'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('should not pass status param when omitted', () => {
      service.list().subscribe();

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/projects`);
      expect(req.request.params.has('status')).toBeFalse();
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single project by id', () => {
      service.get('proj-1').subscribe(project => {
        expect(project.id).toBe('proj-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProject);
    });

    it('should propagate 404 errors', () => {
      service.get('missing').subscribe({
        error: err => expect(err.status).toBe(404)
      });

      const req = httpMock.expectOne(`${baseUrl}/projects/missing`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('create()', () => {
    it('should POST new project', () => {
      const payload = {
        name: 'New Project',
        description: 'desc',
        baseUri: 'https://example.org/new/'
      };

      service.create(payload).subscribe(project => {
        expect(project.name).toBe('Test Project');
      });

      const req = httpMock.expectOne(`${baseUrl}/projects`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(payload);
      req.flush(mockProject);
    });
  });

  describe('update()', () => {
    it('should PUT updated project', () => {
      const payload = { name: 'Renamed' };

      service.update('proj-1', payload).subscribe(project => {
        expect(project.id).toBe('proj-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(payload);
      req.flush(mockProject);
    });
  });

  describe('archive() / unarchive()', () => {
    it('should POST to archive endpoint', () => {
      service.archive('proj-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1/archive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush({ ...mockProject, status: 'ARCHIVED' });
    });

    it('should POST to unarchive endpoint', () => {
      service.unarchive('proj-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1/unarchive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(mockProject);
    });
  });

  describe('delete()', () => {
    it('should DELETE project', () => {
      service.delete('proj-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('summary()', () => {
    it('should GET project summary', () => {
      const mockSummary: ProjectSummary = {
        ...mockProject,
        counts: { pipelines: 2, cubes: 1 }
      };

      service.summary('proj-1').subscribe(summary => {
        expect(summary.counts['pipelines']).toBe(2);
      });

      const req = httpMock.expectOne(`${baseUrl}/projects/proj-1/summary`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSummary);
    });
  });
});
