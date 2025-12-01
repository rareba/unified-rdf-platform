import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let mockRoute: ActivatedRouteSnapshot;
  let mockState: RouterStateSnapshot;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthService', ['init'], {
      isAuthenticated: false
    });
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    
    mockRoute = {} as ActivatedRouteSnapshot;
    mockState = { url: '/test' } as RouterStateSnapshot;
  });

  it('should return true when user is already authenticated', async () => {
    // Set isAuthenticated to true
    Object.defineProperty(authService, 'isAuthenticated', { get: () => true });

    const result = await TestBed.runInInjectionContext(() => 
      authGuard(mockRoute, mockState)
    );

    expect(result).toBeTrue();
    expect(authService.init).not.toHaveBeenCalled();
  });

  it('should call init() when user is not authenticated', async () => {
    Object.defineProperty(authService, 'isAuthenticated', { get: () => false });
    authService.init.and.returnValue(Promise.resolve(true));

    const result = await TestBed.runInInjectionContext(() => 
      authGuard(mockRoute, mockState)
    );

    expect(authService.init).toHaveBeenCalled();
    expect(result).toBeTrue();
  });

  it('should return true when init() succeeds', async () => {
    Object.defineProperty(authService, 'isAuthenticated', { get: () => false });
    authService.init.and.returnValue(Promise.resolve(true));

    const result = await TestBed.runInInjectionContext(() => 
      authGuard(mockRoute, mockState)
    );

    expect(result).toBeTrue();
  });

  it('should return false when init() fails', async () => {
    Object.defineProperty(authService, 'isAuthenticated', { get: () => false });
    authService.init.and.returnValue(Promise.resolve(false));

    const result = await TestBed.runInInjectionContext(() => 
      authGuard(mockRoute, mockState)
    );

    expect(result).toBeFalse();
  });

  it('should return false when init() throws an error', async () => {
    Object.defineProperty(authService, 'isAuthenticated', { get: () => false });
    authService.init.and.returnValue(Promise.reject(new Error('Auth error')));

    try {
      await TestBed.runInInjectionContext(() =>
        authGuard(mockRoute, mockState)
      );
      fail('Expected guard to throw');
    } catch (error) {
      expect(error).toBeTruthy();
    }
  });

  describe('when auth is disabled', () => {
    const originalEnvAuth = { ...environment.auth };

    beforeAll(() => {
      environment.auth.enabled = false;
    });

    afterAll(() => {
      environment.auth = originalEnvAuth;
    });

    it('should return true immediately', async () => {
      const result = await TestBed.runInInjectionContext(() =>
        authGuard(mockRoute, mockState)
      );

      expect(result).toBeTrue();
      expect(authService.init).not.toHaveBeenCalled();
    });
  });
});