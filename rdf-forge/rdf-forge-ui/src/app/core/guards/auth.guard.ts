import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

export const authGuard: CanActivateFn = async (_route, _state) => {
  if (!environment.auth.enabled) {
    return true;
  }

  const authService = inject(AuthService);
  inject(Router); // Router injected for potential future redirects

  if (authService.isAuthenticated) {
    return true;
  }

  // Try to initialize if not already authenticated (e.g. page refresh)
  const authenticated = await authService.init();
  
  if (authenticated) {
    return true;
  }

  // If still not authenticated, login will be triggered by init() or we can force it here
  // authService.login(); 
  return false;
};