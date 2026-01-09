import { Injectable, inject, TemplateRef, Component } from '@angular/core';
import { MatSnackBar, MatSnackBarRef, MatSnackBarConfig } from '@angular/material/snack-bar';
import { ComponentType } from '@angular/cdk/portal';

export interface NotificationAction {
  label: string;
  callback: () => void;
}

export interface NotificationOptions {
  duration?: number;
  action?: NotificationAction;
  panelClass?: string | string[];
  horizontalPosition?: 'start' | 'center' | 'end' | 'left' | 'right';
  verticalPosition?: 'top' | 'bottom';
}

export type NotificationType = 'success' | 'error' | 'warning' | 'info';

/**
 * Enhanced notification service with retry support and styled notifications
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  /**
   * Show a success notification
   */
  success(message: string, options?: NotificationOptions): MatSnackBarRef<unknown> {
    return this.show(message, 'success', options);
  }

  /**
   * Show an error notification with optional retry
   */
  error(
    message: string,
    retryCallback?: () => void,
    options?: NotificationOptions
  ): MatSnackBarRef<unknown> {
    const effectiveOptions: NotificationOptions = {
      duration: 5000,
      ...options
    };

    if (retryCallback) {
      effectiveOptions.action = {
        label: 'Retry',
        callback: retryCallback
      };
      effectiveOptions.duration = 8000; // Longer duration for retry
    }

    return this.show(message, 'error', effectiveOptions);
  }

  /**
   * Show a warning notification
   */
  warning(message: string, options?: NotificationOptions): MatSnackBarRef<unknown> {
    return this.show(message, 'warning', options);
  }

  /**
   * Show an info notification
   */
  info(message: string, options?: NotificationOptions): MatSnackBarRef<unknown> {
    return this.show(message, 'info', options);
  }

  /**
   * Show a notification with optional action button
   */
  show(
    message: string,
    type: NotificationType = 'info',
    options: NotificationOptions = {}
  ): MatSnackBarRef<unknown> {
    const {
      duration = 3000,
      action,
      panelClass,
      horizontalPosition = 'end',
      verticalPosition = 'bottom'
    } = options;

    // Build panel classes based on type
    const classes: string[] = Array.isArray(panelClass) ? panelClass : panelClass ? [panelClass] : [];
    classes.push(`notification-${type}`);

    const config: MatSnackBarConfig = {
      duration: action ? undefined : duration, // No auto-dismiss if action is present
      panelClass: classes,
      horizontalPosition,
      verticalPosition
    };

    const actionLabel = action?.label || 'Close';

    const snackBarRef = this.snackBar.open(message, actionLabel, config);

    // Handle action callback
    snackBarRef.onAction().subscribe(() => {
      if (action?.callback) {
        action.callback();
      }
    });

    // Auto-dismiss after duration if action is present
    if (action && duration) {
      setTimeout(() => {
        snackBarRef.dismiss();
      }, duration);
    }

    return snackBarRef;
  }

  /**
   * Show a loading notification that can be dismissed programmatically
   */
  showLoading(message: string): MatSnackBarRef<unknown> {
    return this.snackBar.open(message, undefined, {
      duration: undefined, // No auto-dismiss
      panelClass: ['notification-loading'],
      horizontalPosition: 'end',
      verticalPosition: 'bottom'
    });
  }

  /**
   * Dismiss all notifications
   */
  dismissAll(): void {
    this.snackBar.dismiss();
  }
}
