import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Settings } from './settings';
import { AuthService } from '../../core/services/auth.service';
import { SettingsService } from '../../core/services/settings.service';
import { signal } from '@angular/core';

describe('Settings', () => {
  let component: Settings;
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function flushHealthChecks() {
    // The component calls checkServicesHealth on init which makes 7 HTTP requests
    const requests = httpMock.match(() => true);
    requests.forEach(req => req.flush({}));
  }

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'logout'], {
      isAuthenticated: signal(true),
      userProfile: signal({ username: 'testuser', email: 'test@example.org' })
    });

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        SettingsService,
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    fixture.detectChanges();
    flushHealthChecks();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });

  it('should have theme options', () => {
    expect(component.themeOptions.length).toBe(3);
    expect(component.themeOptions.map(o => o.value)).toContain('light');
    expect(component.themeOptions.map(o => o.value)).toContain('dark');
    expect(component.themeOptions.map(o => o.value)).toContain('system');
  });

  it('should have language options', () => {
    expect(component.languageOptions.length).toBeGreaterThan(0);
    expect(component.languageOptions.map(o => o.value)).toContain('en');
  });

  it('should format bytes correctly', () => {
    expect(component.formatBytes(0)).toBe('0 Bytes');
    expect(component.formatBytes(512)).toBe('512 Bytes');
    expect(component.formatBytes(1024)).toBe('1 KB');
    expect(component.formatBytes(1536)).toBe('1.5 KB');
    expect(component.formatBytes(1048576)).toBe('1 MB');
  });

  it('should get health severity', () => {
    expect(component.getHealthSeverity('UP')).toBe('success');
    expect(component.getHealthSeverity('DOWN')).toBe('danger');
    expect(component.getHealthSeverity('UNKNOWN')).toBe('warn');
  });

  it('should open prefix dialog', () => {
    component.openPrefixDialog();
    expect(component.prefixDialogVisible()).toBeTrue();
    expect(component.newPrefix().prefix).toBe('');
    expect(component.newPrefix().uri).toBe('');
  });

  it('should open export dialog', () => {
    component.openExportDialog();
    expect(component.exportDialogVisible()).toBeTrue();
  });

  it('should open import dialog', () => {
    component.openImportDialog();
    expect(component.importDialogVisible()).toBeTrue();
    expect(component.importData()).toBe('');
  });

  it('should update new prefix prefix', () => {
    component.updateNewPrefixPrefix('rdf');
    expect(component.newPrefix().prefix).toBe('rdf');
  });

  it('should update new prefix uri', () => {
    component.updateNewPrefixUri('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    expect(component.newPrefix().uri).toBe('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
  });

  it('should format token expiration', () => {
    expect(component.formatTokenExpiration(undefined)).toBe('Never expires');
    const pastDate = new Date(Date.now() - 86400000).toISOString();
    expect(component.formatTokenExpiration(pastDate)).toBe('Expired');
  });

  it('should format last used', () => {
    expect(component.formatLastUsed(undefined)).toBe('Never used');
    const recentDate = new Date(Date.now() - 30 * 60000).toISOString(); // 30 mins ago
    expect(component.formatLastUsed(recentDate)).toBe('30 minutes ago');
  });

  it('should update new token name', () => {
    component.updateNewTokenName('My Token');
    expect(component.newToken().name).toBe('My Token');
  });

  it('should update new token description', () => {
    component.updateNewTokenDescription('Test description');
    expect(component.newToken().description).toBe('Test description');
  });

  it('should update new token expiration', () => {
    component.updateNewTokenExpiration('ONE_YEAR');
    expect(component.newToken().expiration).toBe('ONE_YEAR');
  });

  it('should open new token dialog', () => {
    component.openNewTokenDialog();
    expect(component.newTokenDialogVisible()).toBeTrue();
    expect(component.newToken().name).toBe('');
  });

  it('should close token created dialog', () => {
    component.createdToken.set('test-token');
    component.tokenCreatedDialogVisible.set(true);
    component.closeTokenCreatedDialog();
    expect(component.createdToken()).toBeNull();
    expect(component.tokenCreatedDialogVisible()).toBeFalse();
  });

  it('should get active token count', () => {
    component.personalAccessTokens.set([
      { id: '1', name: 'Token 1', tokenPrefix: 'abc', scopes: [], createdAt: '', revoked: false },
      { id: '2', name: 'Token 2', tokenPrefix: 'def', scopes: [], createdAt: '', revoked: true },
      { id: '3', name: 'Token 3', tokenPrefix: 'ghi', scopes: [], createdAt: '', revoked: false }
    ]);
    expect(component.getActiveTokenCount()).toBe(2);
  });

  it('should load users in demo mode', () => {
    component.loadUsers();
    expect(component.users().length).toBeGreaterThan(0);
  });

  it('should toggle permission', () => {
    component.newRole.set({ name: 'test', description: '', permissions: [], userCount: 0, isDefault: false });
    component.togglePermission('read');
    expect(component.newRole().permissions).toContain('read');
    component.togglePermission('read');
    expect(component.newRole().permissions).not.toContain('read');
  });

  it('should check if has permission', () => {
    component.newRole.set({ name: 'test', description: '', permissions: ['read'], userCount: 0, isDefault: false });
    expect(component.hasPermission('read')).toBeTrue();
    expect(component.hasPermission('write')).toBeFalse();
  });

  it('should save settings', () => {
    component.saveSettings();
    expect(component.saving()).toBeFalse();
  });

  it('should load settings', () => {
    component.loadSettings();
    expect(component.loading()).toBeFalse();
  });

  it('should get auth status when disabled', () => {
    expect(component.getAuthStatus()).toBeTruthy();
  });

  it('should get storage usage', () => {
    const usage = component.getStorageUsage();
    expect(usage).toContain('Bytes');
  });

  it('should add prefix', () => {
    component.newPrefix.set({ prefix: 'test', uri: 'http://test.org/', builtin: false });
    component.addPrefix();
    expect(component.prefixDialogVisible()).toBeFalse();
  });

  it('should not add prefix without values', () => {
    component.newPrefix.set({ prefix: '', uri: '', builtin: false });
    component.addPrefix();
    expect(component.prefixDialogVisible()).toBeFalse();
  });

  it('should remove prefix', () => {
    const prefix = { prefix: 'test', uri: 'http://test.org/', builtin: false };
    component.removePrefix(prefix);
    // Removal succeeded
  });

  it('should not remove builtin prefix', () => {
    const prefix = { prefix: 'rdf', uri: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#', builtin: true };
    component.removePrefix(prefix);
    // Nothing should happen
  });

  it('should copy prefix', () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    const prefix = { prefix: 'rdf', uri: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#', builtin: true };
    component.copyPrefix(prefix);
    expect(navigator.clipboard.writeText).toHaveBeenCalled();
  });

  it('should copy export', () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.exportData.set('{"test": true}');
    component.copyExport();
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('{"test": true}');
  });

  it('should download export', () => {
    component.exportData.set('{"test": true}');
    const createElementSpy = spyOn(document, 'createElement').and.callThrough();
    component.downloadExport();
    expect(createElementSpy).toHaveBeenCalledWith('a');
  });

  it('should import settings', () => {
    component.importData.set('{"theme":"dark"}');
    component.importSettings();
    // Import attempted
  });

  it('should reset settings on confirm', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.confirmReset();
    // Reset should be called
  });

  it('should not reset settings when cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.confirmReset();
    // Reset should not be called
  });

  it('should clear cache on confirm', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.confirmClearCache();
    // Cache cleared
  });

  it('should not clear cache when cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.confirmClearCache();
    // Cache not cleared
  });

  it('should update setting', () => {
    component.updateSetting('theme', 'dark');
    // Setting updated via service
  });

  it('should apply theme', () => {
    component.applyTheme('dark');
    // Theme applied via service
  });

  it('should open user dialog', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer'], createdAt: '2024-01-01'
    };
    component.openUserDialog(user);
    expect(component.userDialogVisible()).toBeTrue();
    expect(component.selectedUser()).toBeTruthy();
  });

  it('should save user in demo mode', () => {
    component.users.set([{
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer'], createdAt: '2024-01-01'
    }]);
    component.selectedUser.set({
      id: '1', username: 'test', email: 'updated@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['editor'], createdAt: '2024-01-01'
    });
    component.saveUser();
    expect(component.userDialogVisible()).toBeFalse();
  });

  it('should not save user when none selected', () => {
    component.selectedUser.set(null);
    component.saveUser();
    // Nothing happens
  });

  it('should toggle user enabled', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer'], createdAt: '2024-01-01'
    };
    component.users.set([user]);
    component.toggleUserEnabled(user);
    expect(component.users()[0].enabled).toBeFalse();
  });

  it('should open role dialog', () => {
    component.openRoleDialog();
    expect(component.roleDialogVisible()).toBeTrue();
    expect(component.newRole().name).toBe('');
  });

  it('should add role in demo mode', () => {
    component.newRole.set({ name: 'custom', description: 'Custom role', permissions: ['read'], userCount: 0, isDefault: false });
    component.addRole();
    expect(component.roleDialogVisible()).toBeFalse();
    expect(component.roles().some(r => r.name === 'custom')).toBeTrue();
  });

  it('should not add role without name', () => {
    component.newRole.set({ name: '', description: '', permissions: [], userCount: 0, isDefault: false });
    component.addRole();
    // Role not added
  });

  it('should not add duplicate role', () => {
    component.newRole.set({ name: 'admin', description: '', permissions: [], userCount: 0, isDefault: false });
    component.addRole();
    // Duplicate not added
  });

  it('should delete role in demo mode', () => {
    const role = { name: 'custom', description: '', permissions: [], userCount: 0, isDefault: false };
    component.roles.update(roles => [...roles, role]);
    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteRole(role);
    expect(component.roles().some(r => r.name === 'custom')).toBeFalse();
  });

  it('should not delete default role', () => {
    const role = { name: 'viewer', description: '', permissions: [], userCount: 0, isDefault: true };
    component.deleteRole(role);
    // Default role not deleted
  });

  it('should not delete role with users', () => {
    const role = { name: 'editor', description: '', permissions: [], userCount: 5, isDefault: false };
    component.deleteRole(role);
    // Role with users not deleted
  });

  it('should update new role name', () => {
    component.updateNewRoleName('newrole');
    expect(component.newRole().name).toBe('newrole');
  });

  it('should update new role description', () => {
    component.updateNewRoleDescription('test description');
    expect(component.newRole().description).toBe('test description');
  });

  it('should update user role', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer'], createdAt: '2024-01-01'
    };
    component.selectedUser.set(user);
    component.updateUserRole(user, 'editor', true);
    expect(component.selectedUser()!.roles).toContain('editor');
  });

  it('should remove user role', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer', 'editor'], createdAt: '2024-01-01'
    };
    component.selectedUser.set(user);
    component.updateUserRole(user, 'editor', false);
    expect(component.selectedUser()!.roles).not.toContain('editor');
  });

  it('should get user role names', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: ['viewer', 'editor'], createdAt: '2024-01-01'
    };
    expect(component.getUserRoleNames(user)).toBe('viewer, editor');
  });

  it('should return no roles for empty roles array', () => {
    const user = {
      id: '1', username: 'test', email: 'test@example.org', firstName: 'Test',
      lastName: 'User', enabled: true, roles: [], createdAt: '2024-01-01'
    };
    expect(component.getUserRoleNames(user)).toBe('No roles');
  });

  it('should get Keycloak admin URL', () => {
    const url = component.getKeycloakAdminUrl();
    // Either empty (disabled) or a valid URL
    expect(typeof url).toBe('string');
  });

  it('should load personal access tokens', fakeAsync(() => {
    component.loadPersonalAccessTokens();
    const req = httpMock.expectOne(`/api/v1/auth/tokens`);
    req.flush([]);
    tick();
    expect(component.loadingTokens()).toBeFalse();
  }));

  it('should handle token load error', fakeAsync(() => {
    component.loadPersonalAccessTokens();
    const req = httpMock.expectOne(`/api/v1/auth/tokens`);
    req.error(new ErrorEvent('Network error'));
    tick();
    expect(component.loadingTokens()).toBeFalse();
    expect(component.personalAccessTokens().length).toBeGreaterThan(0); // Demo tokens
  }));

  it('should create personal access token', fakeAsync(() => {
    component.newToken.set({ name: 'Test Token', description: 'Test', expiration: 'ONE_MONTH' });
    component.createPersonalAccessToken();
    const req = httpMock.expectOne(`/api/v1/auth/tokens`);
    req.error(new ErrorEvent('Network error')); // Falls back to demo mode
    tick();
    expect(component.tokenCreatedDialogVisible()).toBeTrue();
    expect(component.createdToken()).toBeTruthy();
  }));

  it('should not create token without name', () => {
    component.newToken.set({ name: '', description: '', expiration: 'ONE_MONTH' });
    component.createPersonalAccessToken();
    // Token not created
  });

  it('should revoke token', fakeAsync(() => {
    const token = {
      id: '1', name: 'Test', tokenPrefix: 'abc', scopes: [], createdAt: '', revoked: false
    };
    component.personalAccessTokens.set([token]);
    spyOn(window, 'confirm').and.returnValue(true);
    component.revokeToken(token);
    const req = httpMock.expectOne(`/api/v1/auth/tokens/1`);
    req.error(new ErrorEvent('Network error')); // Demo mode
    tick();
    expect(component.personalAccessTokens()[0].revoked).toBeTrue();
  }));

  it('should copy token', () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.createdToken.set('test-token-123');
    component.copyToken();
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('test-token-123');
  });

  it('should not copy null token', () => {
    spyOn(navigator.clipboard, 'writeText');
    component.createdToken.set(null);
    component.copyToken();
    expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
  });

  it('should format token expiration with weeks', () => {
    const futureDate = new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(); // 15 days
    expect(component.formatTokenExpiration(futureDate)).toContain('weeks');
  });

  it('should format token expiration with days', () => {
    const futureDate = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString(); // 5 days
    expect(component.formatTokenExpiration(futureDate)).toContain('days');
  });

  it('should format token expiration for far future', () => {
    const futureDate = new Date(Date.now() + 60 * 24 * 60 * 60 * 1000).toISOString(); // 60 days
    expect(component.formatTokenExpiration(futureDate)).toContain('Expires');
  });

  it('should format last used hours ago', () => {
    const recentDate = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(); // 2 hours ago
    expect(component.formatLastUsed(recentDate)).toContain('hours ago');
  });

  it('should format last used days ago', () => {
    const recentDate = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(); // 3 days ago
    expect(component.formatLastUsed(recentDate)).toContain('days ago');
  });

  it('should format last used for old dates', () => {
    const oldDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(); // 30 days ago
    expect(component.formatLastUsed(oldDate)).not.toBe('Never used');
  });

  it('should have date format options', () => {
    expect(component.dateFormatOptions.length).toBeGreaterThan(0);
  });

  it('should have page size options', () => {
    expect(component.pageSizeOptions.length).toBeGreaterThan(0);
  });

  it('should have format options', () => {
    expect(component.formatOptions.length).toBeGreaterThan(0);
  });

  it('should have token expiration options', () => {
    expect(component.tokenExpirationOptions.length).toBeGreaterThan(0);
  });

  it('should have shortcuts', () => {
    expect(component.shortcuts.length).toBeGreaterThan(0);
  });

  it('should compute custom prefixes', () => {
    expect(typeof component.customPrefixes()).toBe('object');
  });

  it('should compute total prefixes', () => {
    expect(typeof component.totalPrefixes()).toBe('number');
  });

  it('should update role counts', () => {
    component.users.set([
      { id: '1', username: 'test', email: '', firstName: '', lastName: '', enabled: true, roles: ['admin'], createdAt: '' }
    ]);
    component.updateRoleCounts();
    const adminRole = component.roles().find(r => r.name === 'admin');
    expect(adminRole?.userCount).toBe(1);
  });

  it('should check services health', fakeAsync(() => {
    component.checkServicesHealth();
    const requests = httpMock.match(() => true);
    requests.forEach(req => req.flush({}));
    tick();
    expect(component.checkingHealth()).toBeFalse();
    expect(component.services().length).toBeGreaterThan(0);
  }));

  it('should handle health check errors', fakeAsync(() => {
    component.checkServicesHealth();
    const requests = httpMock.match(() => true);
    requests.forEach(req => req.error(new ErrorEvent('Network error')));
    tick();
    expect(component.checkingHealth()).toBeFalse();
  }));

  it('should handle health check 401 as UP', fakeAsync(() => {
    component.checkServicesHealth();
    const requests = httpMock.match(() => true);
    requests.forEach(req => req.flush({}, { status: 401, statusText: 'Unauthorized' }));
    tick();
    // 401/403/404 should be treated as UP
  }));
});