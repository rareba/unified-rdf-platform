import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { GitSyncComponent } from './git-sync';
import { GitSyncService, GitSyncConfig, GitSyncStatus } from '../../../core/services/git-sync.service';

describe('GitSyncComponent', () => {
  let component: GitSyncComponent;
  let fixture: ComponentFixture<GitSyncComponent>;
  let gitSyncService: jasmine.SpyObj<GitSyncService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let dialog: jasmine.SpyObj<MatDialog>;

  const mockConfig: GitSyncConfig = {
    id: 'config-1',
    name: 'Test Config',
    provider: 'GITHUB',
    repositoryUrl: 'https://github.com/test/repo',
    branch: 'main',
    configPath: 'config',
    accessToken: 'ghp_test',
    syncPipelines: true,
    syncShapes: true,
    syncSettings: false,
    autoSync: false,
    syncIntervalMinutes: 60
  };

  const mockStatus: GitSyncStatus = {
    operationId: 'op-1',
    state: 'COMPLETED',
    direction: 'PULL',
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: '2024-01-01T00:01:00Z',
    syncedFiles: [
      { path: 'pipelines/test.json', type: 'PIPELINE', action: 'CREATED' }
    ],
    errors: [],
    warnings: []
  };

  beforeEach(async () => {
    gitSyncService = jasmine.createSpyObj('GitSyncService',
      ['loadConfigs', 'createConfig', 'updateConfig', 'deleteConfig', 'testConnection', 'push', 'pull'],
      {
        configs: signal([mockConfig]),
        loading: signal(false),
        syncing: signal(false),
        currentStatus: signal<GitSyncStatus | null>(null)
      }
    );
    gitSyncService.loadConfigs.and.returnValue(of([mockConfig]));
    gitSyncService.createConfig.and.returnValue(of(mockConfig));
    gitSyncService.updateConfig.and.returnValue(of(mockConfig));
    gitSyncService.deleteConfig.and.returnValue(of(void 0));
    gitSyncService.testConnection.and.returnValue(of({ connected: true, message: 'Success' }));
    gitSyncService.push.and.returnValue(of(mockStatus));
    gitSyncService.pull.and.returnValue(of(mockStatus));

    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [GitSyncComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GitSyncService, useValue: gitSyncService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialog, useValue: dialog }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GitSyncComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load configs on init', () => {
    fixture.detectChanges();
    expect(gitSyncService.loadConfigs).toHaveBeenCalled();
  });

  it('should get empty config', () => {
    const emptyConfig = component.getEmptyConfig();
    expect(emptyConfig.name).toBe('');
    expect(emptyConfig.provider).toBe('GITHUB');
    expect(emptyConfig.branch).toBe('main');
  });

  describe('testConnection', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should show error if repository URL is missing', () => {
      component.formConfig.repositoryUrl = '';
      component.formConfig.accessToken = 'token';
      component.testConnection();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Repository URL and Access Token are required',
        'Close',
        { duration: 3000 }
      );
    });

    it('should show error if access token is missing', () => {
      component.formConfig.repositoryUrl = 'https://github.com/test/repo';
      component.formConfig.accessToken = '';
      component.testConnection();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Repository URL and Access Token are required',
        'Close',
        { duration: 3000 }
      );
    });

    it('should test connection successfully', fakeAsync(() => {
      component.formConfig.repositoryUrl = 'https://github.com/test/repo';
      component.formConfig.accessToken = 'token';

      component.testConnection();

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Connection successful!',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should handle connection failure', fakeAsync(() => {
      gitSyncService.testConnection.and.returnValue(of({ connected: false, message: 'Auth failed' }));
      component.formConfig.repositoryUrl = 'https://github.com/test/repo';
      component.formConfig.accessToken = 'token';

      component.testConnection();
      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Connection failed: Auth failed',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should handle connection error', fakeAsync(() => {
      gitSyncService.testConnection.and.returnValue(throwError(() => new Error('Network error')));
      component.formConfig.repositoryUrl = 'https://github.com/test/repo';
      component.formConfig.accessToken = 'token';

      component.testConnection();
      tick();

      expect(component.testing()).toBeFalse();
      expect(snackBar.open).toHaveBeenCalledWith(
        'Connection test failed',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('saveConfig', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should create new config', fakeAsync(() => {
      component.formConfig = { ...mockConfig, id: undefined };
      component.saveConfig();

      tick();

      expect(gitSyncService.createConfig).toHaveBeenCalled();
      expect(snackBar.open).toHaveBeenCalledWith(
        'Configuration created',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should update existing config', fakeAsync(() => {
      component.editingConfig.set(mockConfig);
      component.formConfig = { ...mockConfig, name: 'Updated Name' };
      component.saveConfig();

      tick();

      expect(gitSyncService.updateConfig).toHaveBeenCalledWith('config-1', jasmine.any(Object));
      expect(snackBar.open).toHaveBeenCalledWith(
        'Configuration updated',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should handle create error', fakeAsync(() => {
      gitSyncService.createConfig.and.returnValue(throwError(() => new Error('Error')));
      component.formConfig = { ...mockConfig, id: undefined };
      component.saveConfig();

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Failed to create configuration',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should handle update error', fakeAsync(() => {
      gitSyncService.updateConfig.and.returnValue(throwError(() => new Error('Error')));
      component.editingConfig.set(mockConfig);
      component.formConfig = mockConfig;
      component.saveConfig();

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Failed to update configuration',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('editConfig', () => {
    it('should set editing config and populate form', () => {
      fixture.detectChanges();
      component.editConfig(mockConfig);

      expect(component.editingConfig()).toBe(mockConfig);
      expect(component.formConfig.name).toBe('Test Config');
      expect(component.formConfig.accessToken).toBe('');
    });
  });

  describe('cancelEdit', () => {
    it('should clear editing config and reset form', () => {
      fixture.detectChanges();
      component.editingConfig.set(mockConfig);
      component.formConfig = { ...mockConfig };

      component.cancelEdit();

      expect(component.editingConfig()).toBeNull();
      expect(component.formConfig.name).toBe('');
    });
  });

  describe('deleteConfig', () => {
    beforeEach(() => {
      fixture.detectChanges();
      spyOn(window, 'confirm').and.returnValue(true);
    });

    it('should delete config when confirmed', fakeAsync(() => {
      component.deleteConfig(mockConfig);

      tick();

      expect(gitSyncService.deleteConfig).toHaveBeenCalledWith('config-1');
      expect(snackBar.open).toHaveBeenCalledWith(
        'Configuration deleted',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should not delete if not confirmed', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.deleteConfig(mockConfig);

      expect(gitSyncService.deleteConfig).not.toHaveBeenCalled();
    });

    it('should handle delete error', fakeAsync(() => {
      gitSyncService.deleteConfig.and.returnValue(throwError(() => new Error('Error')));
      component.deleteConfig(mockConfig);

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Failed to delete configuration',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('pushToGit', () => {
    beforeEach(() => {
      fixture.detectChanges();
      spyOn(window, 'prompt').and.returnValue('Test commit');
    });

    it('should push to git with commit message', fakeAsync(() => {
      component.pushToGit(mockConfig);

      tick();

      expect(gitSyncService.push).toHaveBeenCalledWith('config-1', 'Test commit');
      expect(snackBar.open).toHaveBeenCalledWith(
        'Pushed 1 files',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should not push if prompt is cancelled', () => {
      (window.prompt as jasmine.Spy).and.returnValue(null);
      component.pushToGit(mockConfig);

      expect(gitSyncService.push).not.toHaveBeenCalled();
    });

    it('should handle push failure', fakeAsync(() => {
      const failedStatus: GitSyncStatus = { ...mockStatus, state: 'FAILED', errors: ['Auth failed'] };
      gitSyncService.push.and.returnValue(of(failedStatus));

      component.pushToGit(mockConfig);
      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Push failed: Auth failed',
        'Close',
        { duration: 5000 }
      );
    }));

    it('should handle push error', fakeAsync(() => {
      gitSyncService.push.and.returnValue(throwError(() => new Error('Network error')));
      component.pushToGit(mockConfig);

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Push failed',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('pullFromGit', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should pull from git', fakeAsync(() => {
      component.pullFromGit(mockConfig);

      tick();

      expect(gitSyncService.pull).toHaveBeenCalledWith('config-1', false);
      expect(snackBar.open).toHaveBeenCalledWith(
        'Pulled 1 files',
        'Close',
        { duration: 3000 }
      );
    }));

    it('should handle pull failure', fakeAsync(() => {
      const failedStatus: GitSyncStatus = { ...mockStatus, state: 'FAILED', errors: ['Conflict'] };
      gitSyncService.pull.and.returnValue(of(failedStatus));

      component.pullFromGit(mockConfig);
      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Pull failed: Conflict',
        'Close',
        { duration: 5000 }
      );
    }));

    it('should handle pull error', fakeAsync(() => {
      gitSyncService.pull.and.returnValue(throwError(() => new Error('Network error')));
      component.pullFromGit(mockConfig);

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Pull failed',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('status helpers', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should return correct status icon', () => {
      expect(component.getStatusIcon({ ...mockStatus, state: 'COMPLETED' })).toBe('check_circle');
      expect(component.getStatusIcon({ ...mockStatus, state: 'FAILED' })).toBe('error');
      expect(component.getStatusIcon({ ...mockStatus, state: 'IN_PROGRESS' })).toBe('sync');
      expect(component.getStatusIcon({ ...mockStatus, state: 'PENDING' })).toBe('pending');
    });

    it('should return correct status color', () => {
      expect(component.getStatusColor('COMPLETED')).toBe('accent');
      expect(component.getStatusColor('FAILED')).toBe('warn');
      expect(component.getStatusColor('IN_PROGRESS')).toBe('primary');
    });

    it('should return correct action color', () => {
      expect(component.getActionColor('CREATED')).toBe('accent');
      expect(component.getActionColor('UPDATED')).toBe('primary');
      expect(component.getActionColor('DELETED')).toBe('warn');
    });
  });
});
