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

  describe('login()', () => {
    it('should not throw when keycloak is not initialized', () => {
      expect(() => service.login()).not.toThrow();
    });
  });

  describe('logout()', () => {
    it('should not throw when keycloak is not initialized', () => {
      expect(() => service.logout()).not.toThrow();
    });
  });

  describe('getToken()', () => {
    it('should return undefined when keycloak is not initialized', () => {
      const token = service.getToken();
      expect(token).toBeUndefined();
    });
  });

  describe('hasRole()', () => {
    it('should return false when keycloak is not initialized', () => {
      const result = service.hasRole('admin');
      expect(result).toBeFalse();
    });

    it('should return false for any role when not authenticated', () => {
      expect(service.hasRole('admin')).toBeFalse();
      expect(service.hasRole('user')).toBeFalse();
      expect(service.hasRole('')).toBeFalse();
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
});

interface MockKeycloak {
  init: jasmine.Spy;
  login: jasmine.Spy;
  logout: jasmine.Spy;
  loadUserProfile: jasmine.Spy;
  hasRealmRole: jasmine.Spy;
  token: string;
}

// Test with mocked Keycloak instance (injected after creation)
describe('AuthService with mocked Keycloak', () => {
  let service: AuthService;
  let mockKeycloak: MockKeycloak;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);

    // Create mock keycloak instance
    mockKeycloak = {
      init: jasmine.createSpy('init').and.returnValue(Promise.resolve(true)),
      login: jasmine.createSpy('login'),
      logout: jasmine.createSpy('logout'),
      loadUserProfile: jasmine.createSpy('loadUserProfile').and.returnValue(Promise.resolve({ username: 'test' })),
      hasRealmRole: jasmine.createSpy('hasRealmRole'),
      token: 'mock-jwt-token'
    };

    // Access private property to inject mock
    (service as unknown as { keycloak: MockKeycloak }).keycloak = mockKeycloak;
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