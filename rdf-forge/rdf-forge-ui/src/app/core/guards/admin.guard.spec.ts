import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

describe('adminGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let mockRoute: ActivatedRouteSnapshot;
  let mockState: RouterStateSnapshot;
  let originalAuthEnabled: boolean;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['hasRole']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    mockRoute = {} as ActivatedRouteSnapshot;
    mockState = { url: '/admin' } as RouterStateSnapshot;

    // Save original value
    originalAuthEnabled = environment.auth.enabled;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    });
  });

  afterEach(() => {
    // Restore original value
    environment.auth.enabled = originalAuthEnabled;
  });

  it('should allow access when auth is disabled (offline mode)', async () => {
    environment.auth.enabled = false;

    const result = await TestBed.runInInjectionContext(() =>
      adminGuard(mockRoute, mockState)
    );

    expect(result).toBeTrue();
    expect(authService.hasRole).not.toHaveBeenCalled();
  });

  it('should allow access when user has admin role', async () => {
    environment.auth.enabled = true;
    authService.hasRole.and.returnValue(true);

    const result = await TestBed.runInInjectionContext(() =>
      adminGuard(mockRoute, mockState)
    );

    expect(result).toBeTrue();
    expect(authService.hasRole).toHaveBeenCalledWith('admin');
  });

  it('should deny access and redirect when user lacks admin role', async () => {
    environment.auth.enabled = true;
    authService.hasRole.and.returnValue(false);

    const result = await TestBed.runInInjectionContext(() =>
      adminGuard(mockRoute, mockState)
    );

    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
