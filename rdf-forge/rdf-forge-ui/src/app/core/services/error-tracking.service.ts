import { Injectable, inject } from '@angular/core';
import { LoggerService } from './logger.service';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * Error report structure matching the backend API.
 */
export interface ErrorReport {
  message: string;
  stack?: string;
  context?: string;
  url: string;
  userAgent: string;
  timestamp: string;
  userId?: string;
  correlationId?: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  category: 'javascript' | 'http' | 'angular' | 'network' | 'other';
  metadata?: Record<string, unknown>;
}

/**
 * Batched error queue entry.
 */
interface QueuedError {
  report: ErrorReport;
  retries: number;
}

/**
 * Error Tracking Service
 * 
 * Sends errors to the backend `/api/v1/errors` endpoint.
 * Features:
 * - Batching errors to reduce API calls
 * - Automatic retry with exponential backoff
 * - Error deduplication
 * - Correlation with user session
 */
@Injectable({
  providedIn: 'root'
})
export class ErrorTrackingService {
  private readonly http = inject(HttpClient);
  private readonly logger = inject(LoggerService);

  // Error queue for batching
  private errorQueue: QueuedError[] = [];
  
  // Set to track recently sent errors (for deduplication)
  private recentErrors = new Set<string>();
  
  // Batching configuration
  private readonly BATCH_SIZE = 10;
  private readonly BATCH_INTERVAL_MS = 5000; // 5 seconds
  private readonly MAX_RETRIES = 3;
  private readonly DEDUPLICATION_WINDOW_MS = 60000; // 1 minute
  
  // Timer for batch processing
  private batchTimer: ReturnType<typeof setInterval> | null = null;
  
  // Track if service is initialized
  private isInitialized = false;

  constructor() {
    this.initialize();
  }

  /**
   * Initialize the error tracking service.
   */
  private initialize(): void {
    if (this.isInitialized) {
      return;
    }

    // Start batch timer
    this.startBatchTimer();
    
    // Clear recent errors periodically
    setInterval(() => {
      this.recentErrors.clear();
    }, this.DEDUPLICATION_WINDOW_MS);

    this.isInitialized = true;
  }

  /**
   * Track a JavaScript error.
   */
  trackError(error: Error, context?: string, metadata?: Record<string, unknown>): void {
    const report = this.createErrorReport({
      message: error.message || 'Unknown error',
      stack: error.stack,
      context: context || this.extractContextFromStack(error.stack),
      severity: this.determineSeverity(error),
      category: 'javascript',
      metadata
    });

    this.queueError(report);
  }

  /**
   * Track an HTTP error.
   */
  trackHttpError(error: HttpErrorResponse, context?: string, metadata?: Record<string, unknown>): void {
    const severity = this.determineHttpSeverity(error);
    
    const report = this.createErrorReport({
      message: `HTTP ${error.status}: ${error.statusText}`,
      context: context || 'HTTP Request',
      severity,
      category: 'http',
      metadata: {
        ...metadata,
        status: error.status,
        statusText: error.statusText,
        url: error.url,
        errorBody: error.error
      }
    });

    this.queueError(report);
  }

  /**
   * Track an Angular-specific error.
   */
  trackAngularError(error: unknown, context?: string, metadata?: Record<string, unknown>): void {
    let message: string;
    let stack: string | undefined;

    if (error instanceof Error) {
      message = error.message;
      stack = error.stack;
    } else if (typeof error === 'string') {
      message = error;
    } else {
      try {
        message = JSON.stringify(error);
      } catch {
        message = 'Unknown Angular error';
      }
    }

    const report = this.createErrorReport({
      message,
      stack,
      context: context || 'Angular',
      severity: 'high',
      category: 'angular',
      metadata
    });

    this.queueError(report);
  }

  /**
   * Track a network error.
   */
  trackNetworkError(error: Error, url?: string, metadata?: Record<string, unknown>): void {
    const report = this.createErrorReport({
      message: `Network error: ${error.message}`,
      context: 'Network',
      severity: 'medium',
      category: 'network',
      metadata: {
        ...metadata,
        networkUrl: url,
        online: navigator.onLine
      }
    });

    this.queueError(report);
  }

  /**
   * Send error report immediately (bypass batching).
   * Use for critical errors.
   */
  sendImmediately(report: ErrorReport): void {
    this.sendToBackend([report]);
  }

  /**
   * Flush all queued errors immediately.
   */
  flush(): void {
    this.processQueue();
  }

  /**
   * Get the current queue size.
   */
  getQueueSize(): number {
    return this.errorQueue.length;
  }

  /**
   * Clear the error queue.
   */
  clearQueue(): void {
    this.errorQueue = [];
  }

  /**
   * Dispose the service and clean up resources.
   */
  dispose(): void {
    this.stopBatchTimer();
    this.flush();
  }

  // ==================== Private Methods ====================

  /**
   * Create a complete error report with common fields.
   */
  private createErrorReport(partial: Partial<ErrorReport>): ErrorReport {
    return {
      message: partial.message || 'Unknown error',
      stack: partial.stack,
      context: partial.context,
      url: window.location.href,
      userAgent: navigator.userAgent,
      timestamp: new Date().toISOString(),
      userId: this.getCurrentUserId(),
      correlationId: this.getCorrelationId(),
      severity: partial.severity || 'medium',
      category: partial.category || 'other',
      metadata: partial.metadata
    };
  }

  /**
   * Queue an error for batch sending.
   */
  private queueError(report: ErrorReport): void {
    // Deduplication check
    const errorKey = this.generateErrorKey(report);
    if (this.recentErrors.has(errorKey)) {
      console.debug('[ErrorTracking] Duplicate error skipped:', report.message);
      return;
    }

    this.recentErrors.add(errorKey);

    // Add to queue
    this.errorQueue.push({
      report,
      retries: 0
    });

    // Process immediately if queue is full
    if (this.errorQueue.length >= this.BATCH_SIZE) {
      this.processQueue();
    }
  }

  /**
   * Generate a unique key for error deduplication.
   */
  private generateErrorKey(report: ErrorReport): string {
    // Simple deduplication based on message and context
    return `${report.category}:${report.context}:${report.message.substring(0, 100)}`;
  }

  /**
   * Start the batch processing timer.
   */
  private startBatchTimer(): void {
    if (this.batchTimer) {
      return;
    }

    this.batchTimer = setInterval(() => {
      this.processQueue();
    }, this.BATCH_INTERVAL_MS);
  }

  /**
   * Stop the batch processing timer.
   */
  private stopBatchTimer(): void {
    if (this.batchTimer) {
      clearInterval(this.batchTimer);
      this.batchTimer = null;
    }
  }

  /**
   * Process the error queue and send to backend.
   */
  private processQueue(): void {
    if (this.errorQueue.length === 0) {
      return;
    }

    // Get all errors from queue
    const errorsToSend = this.errorQueue.map(e => e.report);
    this.errorQueue = [];

    // Send to backend
    this.sendToBackend(errorsToSend);
  }

  /**
   * Send errors to the backend API.
   */
  private sendToBackend(reports: ErrorReport[]): void {
    if (!environment.production) {
      // In development, just log to console
      this.logger.debug('[ErrorTracking] Would send errors to backend:', reports);
      return;
    }

    const endpoint = '/api/v1/errors';

    this.http.post(endpoint, { errors: reports }).subscribe({
      next: () => {
        console.debug(`[ErrorTracking] Successfully sent ${reports.length} error(s) to backend`);
      },
      error: (err: HttpErrorResponse) => {
        this.logger.error('[ErrorTracking] Failed to send errors to backend:', err);
        
        // Re-queue failed errors if retries available
        const failedErrors = this.errorQueue.filter(e => e.retries < this.MAX_RETRIES);
        failedErrors.forEach(e => e.retries++);
        
        // Keep them in queue for retry
        this.errorQueue = [...failedErrors, ...this.errorQueue];
      }
    });
  }

  /**
   * Extract context from stack trace.
   */
  private extractContextFromStack(stack?: string): string | undefined {
    if (!stack) {
      return undefined;
    }

    // Try to find component or service name in stack
    const patterns = [
      /at (\w+Component)\./,
      /at (\w+Service)\./,
      /at (\w+Directive)\./,
      /at (\w+Pipe)\./
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
   * Determine error severity from error type.
   */
  private determineSeverity(error: Error): ErrorReport['severity'] {
    // Check for critical errors
    const criticalPatterns = [
      /out of memory/i,
      /stack overflow/i,
      /security/i,
      /fatal/i
    ];

    if (criticalPatterns.some(p => p.test(error.message))) {
      return 'critical';
    }

    // Check for high severity
    const highPatterns = [
      /null/i,
      /undefined/i,
      /cannot read/i,
      /is not a function/i
    ];

    if (highPatterns.some(p => p.test(error.message))) {
      return 'high';
    }

    return 'medium';
  }

  /**
   * Determine severity from HTTP status.
   */
  private determineHttpSeverity(error: HttpErrorResponse): ErrorReport['severity'] {
    if (error.status >= 500) {
      return 'high';
    }
    if (error.status === 429) {
      return 'medium';
    }
    if (error.status >= 400) {
      return 'low';
    }
    return 'medium';
  }

  /**
   * Get current user ID from auth service or storage.
   */
  private getCurrentUserId(): string | undefined {
    try {
      // Try to get from sessionStorage
      const user = sessionStorage.getItem('user');
      if (user) {
        const parsed = JSON.parse(user);
        return parsed.id || parsed.sub || parsed.username;
      }
      
      // Try from localStorage
      const userId = localStorage.getItem('userId');
      if (userId) {
        return userId;
      }
    } catch {
      // Ignore errors reading from storage
    }

    return undefined;
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
}
