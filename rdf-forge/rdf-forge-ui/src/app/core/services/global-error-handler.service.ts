import { ErrorHandler, Injectable, inject, NgZone } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface AppError {
  message: string;
  stack?: string;
  timestamp: Date;
  context?: string;
  httpStatus?: number;
  url?: string;
}

@Injectable({
  providedIn: 'root'
})
export class GlobalErrorHandlerService implements ErrorHandler {
  private readonly snackBar = inject(MatSnackBar);
  private readonly zone = inject(NgZone);

  // Store recent errors for debugging
  private readonly errorLog: AppError[] = [];
  private readonly maxErrors = 50;

  handleError(error: unknown): void {
    const appError = this.parseError(error);
    this.logError(appError);

    // Show user-friendly error message in Angular zone
    this.zone.run(() => {
      this.showErrorNotification(appError);
    });

    // Log to console for debugging
    console.error('Global Error Handler:', error);
  }

  private parseError(error: unknown): AppError {
    if (error instanceof Error) {
      return {
        message: error.message || 'An unexpected error occurred',
        stack: error.stack,
        timestamp: new Date(),
        context: this.extractContext(error)
      };
    }

    if (typeof error === 'object' && error !== null) {
      const err = error as Record<string, unknown>;
      return {
        message: String(err['message'] || err['error'] || 'An unexpected error occurred'),
        timestamp: new Date(),
        httpStatus: typeof err['status'] === 'number' ? err['status'] : undefined,
        url: typeof err['url'] === 'string' ? err['url'] : undefined
      };
    }

    return {
      message: String(error) || 'An unexpected error occurred',
      timestamp: new Date()
    };
  }

  private extractContext(error: Error): string | undefined {
    // Try to extract component/service context from stack trace
    const stack = error.stack || '';
    const match = stack.match(/at (\w+Component|\w+Service)\./);
    return match ? match[1] : undefined;
  }

  private logError(error: AppError): void {
    this.errorLog.unshift(error);
    if (this.errorLog.length > this.maxErrors) {
      this.errorLog.pop();
    }
  }

  private showErrorNotification(error: AppError): void {
    let message = error.message;

    // Provide more context for HTTP errors (use !== undefined to handle status 0)
    if (error.httpStatus !== undefined) {
      switch (error.httpStatus) {
        case 0:
          message = 'Network error: Unable to connect to server';
          break;
        case 401:
          message = 'Authentication required. Please log in again.';
          break;
        case 403:
          message = 'Access denied. You do not have permission for this action.';
          break;
        case 404:
          message = 'Resource not found';
          break;
        case 500:
          message = 'Server error. Please try again later.';
          break;
        case 502:
        case 503:
        case 504:
          message = 'Service temporarily unavailable. Please try again later.';
          break;
      }
    }

    // Truncate long messages
    if (message.length > 150) {
      message = message.substring(0, 147) + '...';
    }

    this.snackBar.open(message, 'Dismiss', {
      duration: 5000,
      panelClass: ['error-snackbar'],
      horizontalPosition: 'end',
      verticalPosition: 'bottom'
    });
  }

  /**
   * Get recent errors for debugging purposes
   */
  getRecentErrors(): AppError[] {
    return [...this.errorLog];
  }

  /**
   * Clear the error log
   */
  clearErrors(): void {
    this.errorLog.length = 0;
  }

  /**
   * Get a formatted error message based on HTTP status or error type
   */
  static getErrorMessage(error: unknown, defaultMessage = 'An error occurred'): string {
    if (!error) return defaultMessage;

    if (typeof error === 'object') {
      const err = error as Record<string, unknown>;

      // Check for HTTP error response
      if (err['status']) {
        const status = err['status'] as number;
        const serverMessage = (err['error'] as Record<string, unknown>)?.['message'] as string;

        switch (status) {
          case 0:
            return 'Network error: Unable to connect to server';
          case 400:
            return serverMessage || 'Invalid request';
          case 401:
            return 'Authentication required';
          case 403:
            return 'Access denied';
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
}
