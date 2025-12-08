import { ApplicationConfig, provideBrowserGlobalErrorListeners, APP_INITIALIZER, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { timeoutInterceptor } from './core/interceptors/timeout.interceptor';
import { SettingsService } from './core/services/settings.service';
import { AuthService } from './core/services/auth.service';

/**
 * Initialize settings before the app starts
 */
function initializeSettings(): () => void {
  const settingsService = inject(SettingsService);
  return () => {
    settingsService.initialize();
  };
}

/**
 * Initialize authentication before the app starts.
 * This runs Keycloak init() once at startup, before any route guards run,
 * preventing race conditions where multiple guards try to initialize concurrently.
 */
function initializeAuth(): () => Promise<boolean> {
  const authService = inject(AuthService);
  return () => authService.init();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, timeoutInterceptor])),
    provideAnimations(),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeSettings,
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      multi: true
    }
  ]
};
