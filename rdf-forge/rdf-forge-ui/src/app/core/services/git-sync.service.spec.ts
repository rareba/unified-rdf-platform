import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GitSyncService, GitSyncConfig, GitSyncStatus, ConnectionTestResult } from './git-sync.service';
import { environment } from '../../../environments/environment';

describe('GitSyncService', () => {
  let service: GitSyncService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/git-sync`;

  const mockConfig: GitSyncConfig = {
    id: 'config-1',
    name: 'Test Config',
    provider: 'GITHUB',
    repositoryUrl: 'https://github.com/test/repo',
    branch: 'main',
    configPath: '/config',
    syncPipelines: true,
    syncShapes: true,
    syncSettings: false,
    autoSync: false
  };

  const mockStatus: GitSyncStatus = {
    operationId: 'op-1',
    state: 'COMPLETED',
    direction: 'PUSH',
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: '2024-01-01T00:01:00Z',
    commitSha: 'abc123',
    commitMessage: 'Sync configuration',
    syncedFiles: [
      { path: 'pipelines/p1.json', type: 'pipeline', action: 'CREATED' }
    ],
    errors: [],
    warnings: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GitSyncService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(GitSyncService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadConfigs()', () => {
    it('should load configs and update signal', () => {
      service.loadConfigs().subscribe(configs => {
        expect(configs.length).toBe(1);
        expect(configs[0].name).toBe('Test Config');
      });

      const req = httpMock.expectOne(`${baseUrl}/configs`);
      expect(req.request.method).toBe('GET');
      req.flush([mockConfig]);

      expect(service.configs().length).toBe(1);
      expect(service.loading()).toBeFalse();
    });

    it('should filter by projectId when provided', () => {
      service.loadConfigs('proj-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/configs?projectId=proj-1`);
      req.flush([]);
    });

    it('should handle errors gracefully', () => {
      service.loadConfigs().subscribe(configs => {
        expect(configs).toEqual([]);
      });

      const req = httpMock.expectOne(`${baseUrl}/configs`);
      req.error(new ErrorEvent('Network error'));

      expect(service.loading()).toBeFalse();
    });
  });

  describe('getConfig()', () => {
    it('should return a single config', () => {
      service.getConfig('config-1').subscribe(config => {
        expect(config.id).toBe('config-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockConfig);
    });
  });

  describe('createConfig()', () => {
    it('should create config and update signal', () => {
      service.createConfig(mockConfig).subscribe(config => {
        expect(config.name).toBe('Test Config');
      });

      const req = httpMock.expectOne(`${baseUrl}/configs`);
      expect(req.request.method).toBe('POST');
      req.flush(mockConfig);

      expect(service.configs().length).toBe(1);
    });
  });

  describe('updateConfig()', () => {
    it('should update config and update signal', () => {
      // First add the config
      service.configs.set([mockConfig]);

      const updated = { ...mockConfig, name: 'Updated Config' };
      service.updateConfig('config-1', updated).subscribe(config => {
        expect(config.name).toBe('Updated Config');
      });

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1`);
      expect(req.request.method).toBe('PUT');
      req.flush(updated);

      expect(service.configs()[0].name).toBe('Updated Config');
    });
  });

  describe('deleteConfig()', () => {
    it('should delete config and update signal', () => {
      service.configs.set([mockConfig]);

      service.deleteConfig('config-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(service.configs().length).toBe(0);
    });
  });

  describe('testConnection()', () => {
    it('should test connection', () => {
      const result: ConnectionTestResult = { connected: true, message: 'Success' };

      service.testConnection({ repositoryUrl: 'https://github.com/test/repo' }).subscribe(r => {
        expect(r.connected).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/test-connection`);
      expect(req.request.method).toBe('POST');
      req.flush(result);
    });
  });

  describe('push()', () => {
    it('should push and update signals', () => {
      service.push('config-1', 'Sync update').subscribe(status => {
        expect(status.state).toBe('COMPLETED');
      });

      expect(service.syncing()).toBeTrue();

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1/push`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ commitMessage: 'Sync update' });
      req.flush(mockStatus);

      expect(service.syncing()).toBeFalse();
      expect(service.currentStatus()?.operationId).toBe('op-1');
    });

    it('should push without commit message', () => {
      service.push('config-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1/push`);
      expect(req.request.body).toEqual({});
      req.flush(mockStatus);
    });
  });

  describe('pull()', () => {
    it('should pull and update signals', () => {
      service.pull('config-1').subscribe(status => {
        expect(status.direction).toBe('PUSH');
      });

      expect(service.syncing()).toBeTrue();

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1/pull?dryRun=false`);
      expect(req.request.method).toBe('POST');
      req.flush(mockStatus);

      expect(service.syncing()).toBeFalse();
    });

    it('should support dry run', () => {
      service.pull('config-1', true).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/configs/config-1/pull?dryRun=true`);
      req.flush(mockStatus);
    });
  });

  describe('getSyncStatus()', () => {
    it('should get sync status', () => {
      service.getSyncStatus('op-1').subscribe(status => {
        expect(status.operationId).toBe('op-1');
      });

      const req = httpMock.expectOne(`${baseUrl}/status/op-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockStatus);
    });
  });
});
