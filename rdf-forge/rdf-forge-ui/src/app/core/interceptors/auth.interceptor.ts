import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  if (!environment.auth.enabled) {
    return next(req);
  }

  // Refresh the token if it is about to expire (within 30 seconds),
  // then attach it to the outgoing request.
  // 401 handling is delegated entirely to the error interceptor.
  return from(authService.getTokenRefreshed(30)).pipe(
    switchMap((token) => {
      if (token) {
        req = req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        });
      }
      return next(req);
    })
  );
};
