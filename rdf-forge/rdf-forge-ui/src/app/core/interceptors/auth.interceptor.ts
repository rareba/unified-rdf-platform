import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

// Flag to prevent multiple concurrent login redirects
let isRedirecting = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  if (environment.auth.enabled) {
    const token = authService.getToken();

    if (token) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
  }

  return next(req).pipe(
    catchError((error) => {
      // Only redirect to login on 401 if:
      // 1. Auth is enabled
      // 2. We're not already redirecting
      // 3. User is not authenticated (prevents loop when token is just expired)
      if (environment.auth.enabled &&
          error.status === 401 &&
          !isRedirecting &&
          !authService.isAuthenticated) {
        isRedirecting = true;
        // Small delay to batch multiple 401s before redirecting
        setTimeout(() => {
          authService.login();
        }, 100);
      }
      return throwError(() => error);
    })
  );
};
