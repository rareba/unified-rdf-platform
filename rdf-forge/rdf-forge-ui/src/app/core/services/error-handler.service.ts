import { Injectable, ErrorHandler, Injector } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from './notification.service';
import { LoggerService } from './logger.service';
import { environment } from '../../../environments/environment';
import { Observable, throwError } from 'rxjs';

/**
 * Custom error handler service that provides user-friendly error messages
 * and logs errors to backend for tracking
 */
@Injectable({
  providedIn: 'root'
})
export class ErrorHandlerService implements ErrorHandler {
  private notificationService?: NotificationService;
  private loggerService?: LoggerService;

  constructor(private injector: Injector) {}

  /**
   * Main error handling method
   * @param error The error to handle
   */
  handleError(error: Error | HttpErrorResponse): void {
    // Lazy load services to avoid circular dependencies
    this.notificationService = this.notificationService || this.injector.get(NotificationService);
    this.loggerService = this.loggerService || this.injector.get(LoggerService);

    let errorMessage: string;
    let shouldNotify = true;

    if (error instanceof HttpErrorResponse) {
      // Server or connection error
      if (!navigator.onLine) {
        errorMessage = 'You are offline. Please check your internet connection.';
      } else if (error.status === 0) {
        errorMessage = 'Unable to connect to the server. Please try again later.';
      } else if (error.status === 401) {
        errorMessage = 'Your session has expired. Please log in again.';
        shouldNotify = false; // Let auth interceptor handle this
      } else if (error.status === 403) {
        errorMessage = 'You do not have permission to perform this action.';
      } else if (error.status === 404) {
        errorMessage = 'The requested resource was not found.';
      } else if (error.status === 422) {
        errorMessage = this.extractValidationErrors(error);
      } else if (error.status === 500) {
        errorMessage = 'An internal server error occurred. Please try again later.';
      } else if (error.status >= 400 && error.status < 500) {
        errorMessage = error.error?.message || 'An error occurred with your request.';
      } else {
        errorMessage = 'An unexpected error occurred. Please try again later.';
      }
    } else {
      // Client-side error
      errorMessage = error.message || 'An unexpected error occurred.';
    }

    // Log to console in development
    if (!environment.production) {
      console.error('Error:', error);
    }

    // Send to backend for tracking
    this.logErrorToBackend(error, errorMessage);

    // Show notification
    if (shouldNotify && this.notificationService) {
      this.notificationService.showError(errorMessage);
    }

    // Log using logger service
    this.loggerService?.error(errorMessage, error);

    // Re-throw for global error handler
    throw error;
  }

  /**
   * Handles HTTP errors in a user-friendly way for use in catchError operators
   * @param operation Name of the operation that failed
   * @returns Function for catchError operator
   */
  handleHttpError<T>(operation = 'operation'): (error: HttpErrorResponse) => Observable<never> {
    return (error: HttpErrorResponse): Observable<never> => {
      this.handleError(error);
      return throwError(() => error);
    };
  }

  /**
   * Extract validation errors from HTTP 422 response
   */
  private extractValidationErrors(error: HttpErrorResponse): string {
    if (error.error?.errors) {
      const errors = Array.isArray(error.error.errors) 
        ? error.error.errors 
        : [error.error.errors];
      return errors.map((e: string | { field: string; message: string }) => 
        typeof e === 'string' ? e : `${e.field}: ${e.message}`
      ).join('\n');
    }
    return error.error?.message || 'Validation failed. Please check your input.';
  }

  /**
   * Send error to backend for tracking
   */
  private logErrorToBackend(error: Error | HttpErrorResponse, message: string): void {
    // In production, send to backend error tracking service
    if (environment.production) {
      // Implementation would depend on backend API
      // fetch('/api/errors', {
      //   method: 'POST',
      //   headers: { 'Content-Type': 'application/json' },
      //   body: JSON.stringify({
      //     message,
      //     stack: error.stack,
      //     url: window.location.href,
      //     timestamp: new Date().toISOString(),
      //     userAgent: navigator.userAgent
      //   })
      // });
    }
  }
}
