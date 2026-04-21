import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have isAuthenticated as false initially', () => {
    expect(service.isAuthenticated).toBeFalse();
  });

  it('should have userProfile as undefined initially', () => {
    expect(service.userProfile).toBeUndefined();
  });

  describe('init()', () => {
    it('should return cached result if already initialized', async () => {
      const originalEnv = { ...environment.auth };
      environment.auth.enabled = false;

      await service.init();
      expect(service.isAuthenticated).toBeTrue();

      const result = await service.init();
      expect(result).toBeTrue();

      environment.auth = originalEnv;
    });

    it('should return existing promise if initialization is in progress (offline mode)', async () => {
      const originalEnv = { ...environment.auth };
      environment.auth.enabled = false;

      const promise1 = service.init();
      const promise2 = service.init();

      // After first init completes, second should also resolve
      const [result1, result2] = await Promise.all([promise1, promise2]);
      expect(result1).toBeTrue();
      expect(result2).toBeTrue();

      environment.auth = originalEnv;
    });
  });

  describe('login()', () => {
    it('should not throw when keycloak is not initialized', () => {
      expect(() => service.login()).not.toThrow();
    });

    it('should call keycloak login when initialized', () => {
      const mockLogin = jasmine.createSpy('login');
      (service as any).keycloak = { login: mockLogin };

      service.login();
      expect(mockLogin).toHaveBeenCalled();
    });
  });

  describe('logout()', () => {
    it('should not throw when keycloak is not initialized', () => {
      expect(() => service.logout()).not.toThrow();
    });

    it('should call keycloak logout when initialized', () => {
      const mockLogout = jasmine.createSpy('logout');
      (service as any).keycloak = { logout: mockLogout };

      service.logout();
      expect(mockLogout).toHaveBeenCalled();
    });
  });

  describe('getToken()', () => {
    it('should return undefined when keycloak is not initialized', () => {
      const token = service.getToken();
      expect(token).toBeUndefined();
    });

    it('should return token when keycloak is initialized', () => {
      (service as any).keycloak = { token: 'mock-jwt-token' };

      const token = service.getToken();
      expect(token).toBe('mock-jwt-token');
    });
  });

  describe('hasRole()', () => {
    it('should return false when keycloak is not initialized', () => {
      expect(service.hasRole('admin')).toBeFalse();
    });

    it('should return true when user has the specified role', () => {
      (service as any).keycloak = {
        hasRealmRole: jasmine.createSpy('hasRealmRole').and.returnValue(true)
      };

      expect(service.hasRole('admin')).toBeTrue();
    });

    it('should return false when user does not have the specified role', () => {
      (service as any).keycloak = {
        hasRealmRole: jasmine.createSpy('hasRealmRole').and.returnValue(false)
      };

      expect(service.hasRole('superadmin')).toBeFalse();
    });

    it('should pass correct role to hasRealmRole', () => {
      const mockHasRealmRole = jasmine.createSpy('hasRealmRole').and.returnValue(true);
      (service as any).keycloak = { hasRealmRole: mockHasRealmRole };

      service.hasRole('admin');
      expect(mockHasRealmRole).toHaveBeenCalledWith('admin');
    });
  });

  describe('isAdmin()', () => {
    it('should return true in offline mode', () => {
      const originalEnv = { ...environment.auth };
      environment.auth.enabled = false;

      expect(service.isAdmin()).toBeTrue();

      environment.auth = originalEnv;
    });

    it('should check admin role when auth is enabled', () => {
      const originalEnv = { ...environment.auth };
      environment.auth.enabled = true;

      (service as any).keycloak = {
        hasRealmRole: jasmine.createSpy('hasRealmRole').and.returnValue(true)
      };

      expect(service.isAdmin()).toBeTrue();

      environment.auth = originalEnv;
    });
  });
});

describe('AuthService (Offline Mode)', () => {
  let service: AuthService;
  const originalEnvAuth = { ...environment.auth };

  beforeAll(() => {
    environment.auth.enabled = false;
  });

  afterAll(() => {
    environment.auth = originalEnvAuth;
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
  });

  it('should initialize as authenticated in offline mode', async () => {
    const result = await service.init();
    expect(result).toBeTrue();
    expect(service.isAuthenticated).toBeTrue();
    expect(service.userProfile).toBeDefined();
    expect(service.userProfile?.username).toBe('offline-user');
  });

  it('should set correct user profile in offline mode', async () => {
    await service.init();
    const profile = service.userProfile;
    expect(profile?.firstName).toBe('Offline');
    expect(profile?.lastName).toBe('User');
    expect(profile?.email).toBe('offline@local');
  });
});

describe('AuthService with mocked Keycloak', () => {
  let service: AuthService;
  let mockKeycloak: {
    init: jasmine.Spy;
    login: jasmine.Spy;
    logout: jasmine.Spy;
    loadUserProfile: jasmine.Spy;
    hasRealmRole: jasmine.Spy;
    token: string;
    tokenParsed: Record<string, unknown> | null;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);

    mockKeycloak = {
      init: jasmine.createSpy('init').and.resolveTo(true),
      login: jasmine.createSpy('login'),
      logout: jasmine.createSpy('logout'),
      loadUserProfile: jasmine.createSpy('loadUserProfile').and.resolveTo({ username: 'test' }),
      hasRealmRole: jasmine.createSpy('hasRealmRole'),
      token: 'mock-jwt-token',
      tokenParsed: null
    };

    (service as any).keycloak = mockKeycloak;
    (service as any)._initialized = true;
    (service as any)._isAuthenticated = true;
  });

  describe('login()', () => {
    it('should call keycloak login when keycloak is initialized', () => {
      service.login();
      expect(mockKeycloak.login).toHaveBeenCalled();
    });
  });

  describe('logout()', () => {
    it('should call keycloak logout when keycloak is initialized', () => {
      service.logout();
      expect(mockKeycloak.logout).toHaveBeenCalled();
    });
  });

  describe('getToken()', () => {
    it('should return token when keycloak is initialized', () => {
      const token = service.getToken();
      expect(token).toBe('mock-jwt-token');
    });
  });

  describe('hasRole()', () => {
    it('should return true when user has the specified role', () => {
      mockKeycloak.hasRealmRole.and.returnValue(true);

      const result = service.hasRole('admin');

      expect(result).toBeTrue();
      expect(mockKeycloak.hasRealmRole).toHaveBeenCalledWith('admin');
    });

    it('should return false when user does not have the specified role', () => {
      mockKeycloak.hasRealmRole.and.returnValue(false);

      const result = service.hasRole('superadmin');

      expect(result).toBeFalse();
      expect(mockKeycloak.hasRealmRole).toHaveBeenCalledWith('superadmin');
    });
  });
});

describe('AuthService URL hash handling', () => {
  it('should be able to clear URL hash', () => {
    const originalHash = window.location.hash;
    window.history.replaceState(null, '', '#/some-auth-callback');

    expect(window.location.hash).toBe('#/some-auth-callback');

    window.history.replaceState(null, '', originalHash || '/');
    expect(window.location.hash).toBe('');
  });
});
