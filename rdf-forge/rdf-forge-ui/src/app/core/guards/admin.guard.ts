import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Guard that restricts access to admin-only routes.
 * In offline mode, always allows access (for development).
 * In online mode, checks for 'admin' role from Keycloak.
 */
export const adminGuard: CanActivateFn = async (_route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // In offline/standalone mode, allow all access for development
  if (!environment.auth.enabled) {
    return true;
  }

  // Check if user has admin role
  if (authService.hasRole('admin')) {
    return true;
  }

  // Redirect to dashboard if not admin
  router.navigate(['/']);
  return false;
};
