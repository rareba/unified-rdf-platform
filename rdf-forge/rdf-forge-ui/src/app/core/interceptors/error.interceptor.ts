import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError, Observable } from 'rxjs';
import { NotificationService } from '../services/notification.service';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';
import { LoggerService } from '../services/logger.service';

// Context token to suppress error notification for requests that handle errors locally
export const SUPPRESS_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);

// Correlation ID header for distributed tracing
const CORRELATION_ID_HEADER = 'X-Correlation-Id';
const TRACE_ID_HEADER = 'X-Trace-Id';

// Flag to prevent multiple concurrent redirects
let isRedirecting = false;

/**
 * HTTP Error Interceptor
 * 
 * Handles HTTP errors globally:
 * - 401/403: Redirects to login
 * - 404: Shows not found message
 * - 500: Shows error with retry option
 * - Network errors: Shows connection error
 * - Adds correlation ID to all requests for tracing
 */
export const errorInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const router = inject(Router);
  const notificationService = inject(NotificationService);
  const authService = inject(AuthService);
  const logger = inject(LoggerService);

  // Add correlation ID to request for distributed tracing
  const requestWithCorrelationId = addCorrelationId(req);

  return next(requestWithCorrelationId).pipe(
    catchError((error: HttpErrorResponse) => {
      const correlationId = error.headers?.get(CORRELATION_ID_HEADER) ||
                           error.headers?.get(TRACE_ID_HEADER) ||
                           'unknown';

      // Skip notification and loud logging if the request opts out (component handles it locally)
      if (requestWithCorrelationId.context.get(SUPPRESS_ERROR_NOTIFICATION)) {
        logger.debug(`HTTP Error [${correlationId}]: ${error.status} ${error.statusText} (suppressed)`, error.url);
        return throwError(() => error);
      }

      logger.error(`HTTP Error [${correlationId}]:`, error);

      // Handle different error statuses
      switch (error.status) {
        case 0:
          // Network error
          handleNetworkError(error, notificationService);
          break;

        case 401:
          // Unauthorized - redirect to login
          handleUnauthorized(error, authService, router, notificationService);
          break;

        case 403:
          // Forbidden
          handleForbidden(error, notificationService, router);
          break;

        case 404:
          // Not found
          handleNotFound(error, notificationService);
          break;

        case 409:
          // Conflict
          handleConflict(error, notificationService);
          break;

        case 422:
          // Validation error
          handleValidationError(error, notificationService);
          break;

        case 429:
          // Rate limited
          handleRateLimited(error, notificationService);
          break;

        case 500:
        case 502:
        case 503:
        case 504:
          // Server errors
          handleServerError(error, correlationId, notificationService);
          break;

        default:
          // Other errors
          handleGenericError(error, correlationId, notificationService);
      }

      // Re-throw the error for component-level handling if needed
      return throwError(() => error);
    })
  );
};

/**
 * Add a unique correlation ID to each request for distributed tracing.
 * Each request gets its own ID so that individual requests can be traced
 * independently through the backend services.
 */
function addCorrelationId(req: HttpRequest<unknown>): HttpRequest<unknown> {
  const correlationId = generateCorrelationId();

  // Clone the request with correlation headers
  return req.clone({
    headers: req.headers
      .set(CORRELATION_ID_HEADER, correlationId)
      .set(TRACE_ID_HEADER, correlationId)
  });
}

/**
 * Generate a unique correlation ID per request.
 * Uses crypto.randomUUID() when available, with a Math.random() fallback.
 */
function generateCorrelationId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/**
 * Handle network errors (status 0).
 */
function handleNetworkError(error: HttpErrorResponse, notificationService: NotificationService): void {
  const message = navigator.onLine 
    ? 'Unable to connect to the server. Please check your network connection and try again.'
    : 'You appear to be offline. Please check your internet connection.';

  notificationService.error(message, undefined, {
    duration: 8000,
    panelClass: ['error-snackbar', 'network-error']
  });
}

/**
 * Handle unauthorized errors (401).
 */
function handleUnauthorized(
  error: HttpErrorResponse,
  authService: AuthService,
  router: Router,
  notificationService: NotificationService
): void {
  // Only redirect if auth is enabled and not already redirecting
  if (environment.auth.enabled && !isRedirecting) {
    isRedirecting = true;
    
    notificationService.error(
      'Your session has expired. Please log in again.',
      undefined,
      {
        duration: 10000,
        panelClass: ['error-snackbar', 'auth-error']
      }
    );

    // Small delay to batch multiple 401s before redirecting
    setTimeout(() => {
      authService.login();
      isRedirecting = false;
    }, 100);
  }
}

/**
 * Handle forbidden errors (403).
 */
function handleForbidden(
  error: HttpErrorResponse,
  notificationService: NotificationService,
  router: Router
): void {
  const serverMessage = extractServerMessage(error);
  const message = serverMessage || 'You do not have permission to perform this action.';

  notificationService.error(message, undefined, {
    duration: 6000,
    panelClass: ['error-snackbar', 'forbidden-error']
  });

  // Optionally redirect to access denied page after a delay
  // setTimeout(() => router.navigate(['/access-denied']), 2000);
}

/**
 * Handle not found errors (404).
 */
function handleNotFound(error: HttpErrorResponse, notificationService: NotificationService): void {
  const serverMessage = extractServerMessage(error);
  const message = serverMessage || 'The requested resource was not found.';

  notificationService.error(message, undefined, {
    duration: 5000,
    panelClass: ['error-snackbar', 'not-found-error']
  });
}

/**
 * Handle conflict errors (409).
 */
function handleConflict(error: HttpErrorResponse, notificationService: NotificationService): void {
  const serverMessage = extractServerMessage(error);
  const message = serverMessage || 'There is a conflict with the current state of the resource.';

  notificationService.warning(message, {
    duration: 6000,
    panelClass: ['warning-snackbar']
  });
}

/**
 * Handle validation errors (422).
 */
function handleValidationError(error: HttpErrorResponse, notificationService: NotificationService): void {
  const validationErrors = extractValidationErrors(error);
  const message = validationErrors || extractServerMessage(error) || 'Validation failed. Please check your input.';

  notificationService.error(message, undefined, {
    duration: 8000,
    panelClass: ['error-snackbar', 'validation-error']
  });
}

/**
 * Handle rate limit errors (429).
 */
function handleRateLimited(error: HttpErrorResponse, notificationService: NotificationService): void {
  const retryAfter = error.headers?.get('Retry-After');
  let message = 'Too many requests. Please slow down and try again later.';
  
  if (retryAfter) {
    message += ` (Retry after ${retryAfter}s)`;
  }

  notificationService.warning(message, {
    duration: 10000,
    panelClass: ['warning-snackbar']
  });
}

/**
 * Handle server errors (500+).
 */
function handleServerError(
  error: HttpErrorResponse,
  correlationId: string,
  notificationService: NotificationService
): void {
  const status = error.status;
  let message: string;

  switch (status) {
    case 502:
      message = 'Bad gateway. The server is temporarily unavailable.';
      break;
    case 503:
      message = 'Service temporarily unavailable. Please try again later.';
      break;
    case 504:
      message = 'Gateway timeout. The server took too long to respond.';
      break;
    default:
      message = `A server error occurred. Please try again later. If the problem persists, contact support with trace ID: ${correlationId}`;
  }

  notificationService.error(message, undefined, {
    duration: 8000,
    panelClass: ['error-snackbar', 'server-error']
  });
}

/**
 * Handle generic errors.
 */
function handleGenericError(
  error: HttpErrorResponse,
  correlationId: string,
  notificationService: NotificationService
): void {
  const serverMessage = extractServerMessage(error);
  const message = serverMessage || `An unexpected error occurred. Trace ID: ${correlationId}`;

  notificationService.error(message, undefined, {
    duration: 6000,
    panelClass: ['error-snackbar']
  });
}

/**
 * Extract error message from server response.
 */
function extractServerMessage(error: HttpErrorResponse): string | undefined {
  if (typeof error.error === 'string') {
    return error.error;
  }
  
  if (error.error && typeof error.error === 'object') {
    // RFC 7807 Problem Detail format
    if (error.error.detail) {
      return error.error.detail;
    }
    
    // Standard error message
    if (error.error.message) {
      return error.error.message;
    }
  }
  
  return undefined;
}

/**
 * Extract validation errors from server response.
 */
function extractValidationErrors(error: HttpErrorResponse): string | undefined {
  if (error.error && typeof error.error === 'object') {
    // Check for field errors
    if (error.error.fieldErrors && typeof error.error.fieldErrors === 'object') {
      const fieldErrors = error.error.fieldErrors;
      return Object.entries(fieldErrors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join('\n');
    }
    
    // Check for validation errors array
    if (error.error.errors && Array.isArray(error.error.errors)) {
      return error.error.errors
        .map((e: string | { field?: string; message?: string }) => 
          typeof e === 'string' ? e : `${e.field}: ${e.message}`
        )
        .join('\n');
    }
  }
  
  return undefined;
}
