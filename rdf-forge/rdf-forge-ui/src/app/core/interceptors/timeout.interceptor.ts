import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, timeout, TimeoutError, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SettingsService } from '../services/settings.service';

/**
 * HTTP context token to specify operation type for timeout calculation
 */
export const OPERATION_TYPE = new HttpContextToken<'pipeline' | 'sparql' | 'default'>(() => 'default');

/**
 * HTTP context token to specify custom timeout in milliseconds
 */
export const CUSTOM_TIMEOUT = new HttpContextToken<number | null>(() => null);

/**
 * HTTP context token to disable timeout for specific requests
 */
export const DISABLE_TIMEOUT = new HttpContextToken<boolean>(() => false);

/**
 * Timeout interceptor that applies configurable timeouts based on operation type
 * Uses settings from SettingsService for pipeline and SPARQL timeouts
 */
export const timeoutInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const settingsService = inject(SettingsService);

  // Check if timeout is disabled for this request
  if (req.context.get(DISABLE_TIMEOUT)) {
    return next(req);
  }

  // Determine timeout value
  let timeoutMs: number;

  // Check for custom timeout first
  const customTimeout = req.context.get(CUSTOM_TIMEOUT);
  if (customTimeout !== null) {
    timeoutMs = customTimeout;
  } else {
    // Use operation type to determine timeout
    const operationType = req.context.get(OPERATION_TYPE);
    timeoutMs = settingsService.getTimeoutMs(operationType);
  }

  // Apply timeout
  return next(req).pipe(
    timeout(timeoutMs),
    catchError(error => {
      if (error instanceof TimeoutError) {
        // Transform timeout error to a more descriptive error
        return throwError(() => new Error(
          `Request timed out after ${timeoutMs / 1000} seconds. ` +
          `You can adjust timeout settings in Settings > Pipeline Defaults.`
        ));
      }
      return throwError(() => error);
    })
  );
};

/**
 * Helper function to create request context with pipeline timeout
 */
export function withPipelineTimeout(): { context: any } {
  const context = new (HttpRequest as any).HttpContext();
  context.set(OPERATION_TYPE, 'pipeline');
  return { context };
}

/**
 * Helper function to create request context with SPARQL timeout
 */
export function withSparqlTimeout(): { context: any } {
  const context = new (HttpRequest as any).HttpContext();
  context.set(OPERATION_TYPE, 'sparql');
  return { context };
}

/**
 * Helper function to create request context with custom timeout
 */
export function withCustomTimeout(timeoutMs: number): { context: any } {
  const context = new (HttpRequest as any).HttpContext();
  context.set(CUSTOM_TIMEOUT, timeoutMs);
  return { context };
}
