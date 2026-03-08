import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Service for logging messages to console with different levels
 */
@Injectable({
  providedIn: 'root'
})
export class LoggerService {
  private readonly isProduction = environment.production;

  /**
   * Log debug message (only in development)
   */
  debug(message: string, ...data: unknown[]): void {
    if (!this.isProduction) {
      console.debug(`[DEBUG] ${message}`, ...data);
    }
  }

  /**
   * Log info message
   */
  info(message: string, ...data: unknown[]): void {
    console.info(`[INFO] ${message}`, ...data);
  }

  /**
   * Log warning message
   */
  warn(message: string, ...data: unknown[]): void {
    console.warn(`[WARN] ${message}`, ...data);
  }

  /**
   * Log error message
   */
  error(message: string, error?: Error | unknown): void {
    console.error(`[ERROR] ${message}`, error);
  }

  /**
   * Log performance timing
   */
  performance(label: string, startTime: number): void {
    const duration = performance.now() - startTime;
    this.debug(`[PERF] ${label}: ${duration.toFixed(2)}ms`);
  }

  /**
   * Group related logs
   */
  group(label: string, fn: () => void): void {
    console.group(label);
    try {
      fn();
    } finally {
      console.groupEnd();
    }
  }
}
