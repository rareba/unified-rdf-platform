import { ErrorHandler, Injectable, inject, NgZone } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';
import { ErrorTrackingService } from './error-tracking.service';
import { NotificationService } from './notification.service';

export interface AppError {
  message: string;
  stack?: string;
  timestamp: Date;
  context?: string;
  httpStatus?: number;
  url?: string;
  userAgent?: string;
  userId?: string;
  correlationId?: string;
}

/**
 * Global Error Handler Service
 * 
 * Catches all unhandled errors in the application:
 * - Sends errors to backend error tracking API
 * - Displays user-friendly messages via snackbar
 * - Logs to console in development
 * - Prevents error flooding with deduplication
 */
@Injectable({
  providedIn: 'root'
})
export class GlobalErrorHandlerService implements ErrorHandler {
  private readonly snackBar = inject(MatSnackBar);
  private readonly zone = inject(NgZone);
  private readonly errorTracking = inject(ErrorTrackingService);
  private readonly notificationService = inject(NotificationService);

  // Store recent errors for debugging
  private readonly errorLog: AppError[] = [];
  private readonly maxErrors = 50;
  
  // Track error counts to prevent flooding
  private errorCounts = new Map<string, number>();
  private readonly FLOOD_THRESHOLD = 5;
  private readonly FLOOD_WINDOW_MS = 60000; // 1 minute

  handleError(error: unknown): void {
    const appError = this.parseError(error);
    this.logError(appError);

    // Track error with error tracking service
    this.trackError(error, appError);

    // Show user-friendly error message in Angular zone
    this.zone.run(() => {
      this.showErrorNotification(appError);
    });

    // Log to console for debugging
    if (!environment.production) {
      console.error('Global Error Handler:', error);
    }
  }

  /**
   * Parse an error into a standardized AppError format.
   */
  private parseError(error: unknown): AppError {
    const baseError: AppError = {
      message: 'An unexpected error occurred',
      timestamp: new Date(),
      userAgent: navigator.userAgent,
      url: window.location.href,
      correlationId: this.getCorrelationId()
    };

    if (error instanceof Error) {
      return {
        ...baseError,
        message: error.message || 'An unexpected error occurred',
        stack: error.stack,
        context: this.extractContext(error)
      };
    }

    if (error instanceof HttpErrorResponse) {
      return {
        ...baseError,
        message: this.getHttpErrorMessage(error),
        httpStatus: error.status,
        url: error.url || baseError.url,
        context: 'HTTP'
      };
    }

    if (typeof error === 'object' && error !== null) {
      const err = error as Record<string, unknown>;
      return {
        ...baseError,
        message: String(err['message'] || err['error'] || 'An unexpected error occurred'),
        httpStatus: typeof err['status'] === 'number' ? err['status'] : undefined,
        url: typeof err['url'] === 'string' ? err['url'] : baseError.url,
        stack: typeof err['stack'] === 'string' ? err['stack'] : undefined,
        context: typeof err['context'] === 'string' ? err['context'] : undefined
      };
    }

    return {
      ...baseError,
      message: String(error) || 'An unexpected error occurred'
    };
  }

  /**
   * Track error with error tracking service.
   */
  private trackError(error: unknown, appError: AppError): void {
    // Check for error flooding
    if (this.isFlooding(appError.message)) {
      console.warn('[GlobalErrorHandler] Error flooding detected, skipping tracking:', appError.message);
      return;
    }

    // Track with appropriate service method
    if (error instanceof HttpErrorResponse) {
      this.errorTracking.trackHttpError(error, appError.context);
    } else if (error instanceof Error) {
      this.errorTracking.trackError(error, appError.context);
    } else {
      this.errorTracking.trackAngularError(error, appError.context);
    }
  }

  /**
   * Check if we're receiving too many similar errors (flooding).
   */
  private isFlooding(message: string): boolean {
    const key = message.substring(0, 100); // Truncate for comparison
    const now = Date.now();
    
    const count = this.errorCounts.get(key) || 0;
    this.errorCounts.set(key, count + 1);

    // Clear counts periodically
    setTimeout(() => {
      this.errorCounts.delete(key);
    }, this.FLOOD_WINDOW_MS);

    return count >= this.FLOOD_THRESHOLD;
  }

  /**
   * Extract context from error stack trace.
   */
  private extractContext(error: Error): string | undefined {
    const stack = error.stack || '';
    
    // Look for component/service names in the stack
    const patterns = [
      /at (\w+Component)\./,
      /at (\w+Service)\./,
      /at (\w+Directive)\./,
      /at (\w+Pipe)\./,
      /at (\w+Guard)\./
    ];

    for (const pattern of patterns) {
      const match = stack.match(pattern);
      if (match) {
        return match[1];
      }
    }

    return undefined;
  }

  /**
   * Get user-friendly HTTP error message.
   */
  private getHttpErrorMessage(error: HttpErrorResponse): string {
    // Check for RFC 7807 Problem Detail format
    if (error.error && typeof error.error === 'object') {
      if (error.error.detail) {
        return error.error.detail;
      }
      if (error.error.message) {
        return error.error.message;
      }
    }

    // Standard status messages
    switch (error.status) {
      case 0:
        return 'Network error: Unable to connect to server';
      case 400:
        return 'Bad request: Please check your input';
      case 401:
        return 'Unauthorized: Please log in again';
      case 403:
        return 'Forbidden: You do not have permission';
      case 404:
        return 'Not found: The requested resource was not found';
      case 409:
        return 'Conflict: There was a conflict with the request';
      case 422:
        return 'Validation error: Please check your input';
      case 429:
        return 'Too many requests: Please slow down';
      case 500:
        return 'Server error: An internal error occurred';
      case 502:
        return 'Bad gateway: The server is temporarily unavailable';
      case 503:
        return 'Service unavailable: Please try again later';
      case 504:
        return 'Gateway timeout: The server took too long to respond';
      default:
        return `HTTP error ${error.status}: ${error.statusText}`;
    }
  }

  /**
   * Log error to local storage for debugging.
   */
  private logError(error: AppError): void {
    this.errorLog.unshift(error);
    if (this.errorLog.length > this.maxErrors) {
      this.errorLog.pop();
    }

    // Also log to console in production for troubleshooting
    if (environment.production) {
      console.error('[Error]', error.message, error);
    }
  }

  /**
   * Show user-friendly error notification.
   */
  private showErrorNotification(error: AppError): void {
    // Don't show notifications for certain error types
    if (this.shouldSuppressNotification(error)) {
      return;
    }

    let message = this.getUserFriendlyMessage(error);
    let action = 'Dismiss';
    let duration = 5000;

    // Handle specific HTTP status codes
    if (error.httpStatus !== undefined) {
      switch (error.httpStatus) {
        case 0:
          message = 'Network error: Unable to connect to server. Please check your connection.';
          duration = 8000;
          break;
        case 401:
          message = 'Your session has expired. Please log in again.';
          action = 'Login';
          duration = 10000;
          break;
        case 403:
          message = 'Access denied. You do not have permission for this action.';
          duration = 6000;
          break;
        case 404:
          message = 'The requested resource was not found.';
          duration = 5000;
          break;
        case 409:
          message = 'There is a conflict with the current state. Please refresh and try again.';
          duration = 6000;
          break;
        case 422:
          message = 'Validation error. Please check your input and try again.';
          duration = 6000;
          break;
        case 429:
          message = 'Too many requests. Please slow down and try again later.';
          duration = 8000;
          break;
        case 500:
          message = error.correlationId 
            ? `Server error. Please try again later. Reference: ${error.correlationId}`
            : 'Server error. Please try again later.';
          duration = 8000;
          break;
        case 502:
        case 503:
        case 504:
          message = 'Service temporarily unavailable. Please try again later.';
          duration = 8000;
          break;
      }
    }

    // Use notification service for consistent styling
    const snackBarRef = this.notificationService.error(message, undefined, {
      duration,
      action: { label: action, callback: () => {} }
    });

    // Handle action click
    snackBarRef.onAction().subscribe(() => {
      if (action === 'Login') {
        window.location.href = '/login';
      }
    });
  }

  /**
   * Check if notification should be suppressed.
   */
  private shouldSuppressNotification(error: AppError): boolean {
    // Suppress 401 errors - let the auth interceptor handle those
    if (error.httpStatus === 401) {
      return true;
    }

    // Suppress during error flooding
    if (this.isFlooding(error.message)) {
      return true;
    }

    return false;
  }

  /**
   * Get user-friendly error message.
   */
  private getUserFriendlyMessage(error: AppError): string {
    // Truncate long messages
    let message = error.message;
    if (message.length > 200) {
      message = message.substring(0, 197) + '...';
    }

    return message;
  }

  /**
   * Get correlation ID from session storage.
   */
  private getCorrelationId(): string | undefined {
    try {
      return sessionStorage.getItem('correlationId') || undefined;
    } catch {
      return undefined;
    }
  }

  // ==================== Public API ====================

  /**
   * Get recent errors for debugging purposes.
   */
  getRecentErrors(): AppError[] {
    return [...this.errorLog];
  }

  /**
   * Clear the error log.
   */
  clearErrors(): void {
    this.errorLog.length = 0;
  }

  /**
   * Get a formatted error message based on HTTP status or error type.
   */
  static getErrorMessage(error: unknown, defaultMessage = 'An error occurred'): string {
    if (!error) return defaultMessage;

    if (typeof error === 'object') {
      const err = error as Record<string, unknown>;

      // Check for HTTP error response
      if (err['status']) {
        const status = err['status'] as number;
        const serverMessage = (err['error'] as Record<string, unknown>)?.['detail'] as string 
          || (err['error'] as Record<string, unknown>)?.['message'] as string;

        switch (status) {
          case 0:
            return 'Network error: Unable to connect to server';
          case 400:
            return serverMessage || 'Invalid request';
          case 401:
            return 'Your session has expired. Please log in again.';
          case 403:
            return serverMessage || 'Access denied';
          case 404:
            return serverMessage || 'Resource not found';
          case 409:
            return serverMessage || 'Conflict with existing resource';
          case 422:
            return serverMessage || 'Validation error';
          case 429:
            return 'Too many requests. Please slow down.';
          case 500:
            return 'Server error. Please try again later.';
          case 502:
          case 503:
          case 504:
            return 'Service temporarily unavailable';
          default:
            return serverMessage || defaultMessage;
        }
      }

      // Check for error message property
      if (err['message']) {
        return String(err['message']);
      }
    }

    if (error instanceof Error) {
      return error.message || defaultMessage;
    }

    return String(error) || defaultMessage;
  }

  /**
   * Check if an error is a network error.
   */
  static isNetworkError(error: unknown): boolean {
    if (typeof error !== 'object' || error === null) return false;
    const err = error as Record<string, unknown>;
    return err['status'] === 0 || 
           err['message']?.toString().includes('Network') ||
           err['message']?.toString().includes('Http failure response');
  }

  /**
   * Check if an error is an authentication error.
   */
  static isAuthError(error: unknown): boolean {
    if (typeof error !== 'object' || error === null) return false;
    const err = error as Record<string, unknown>;
    return err['status'] === 401 || err['status'] === 403;
  }

  /**
   * Check if an error is a server error.
   */
  static isServerError(error: unknown): boolean {
    if (typeof error !== 'object' || error === null) return false;
    const err = error as Record<string, unknown>;
    const status = err['status'] as number;
    return status >= 500;
  }
}
