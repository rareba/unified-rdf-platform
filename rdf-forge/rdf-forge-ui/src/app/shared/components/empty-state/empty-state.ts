import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export type EmptyStateType = 'no-data' | 'error' | 'search' | 'filter' | 'custom';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <div class="empty-state" [class]="containerClass">
      <div class="empty-state-icon">
        @if (icon) {
          <mat-icon [class]="iconClass">{{ icon }}</mat-icon>
        } @else {
          @switch (type) {
            @case ('no-data') {
              <mat-icon class="icon-muted">inbox</mat-icon>
            }
            @case ('error') {
              <mat-icon class="icon-error">error_outline</mat-icon>
            }
            @case ('search') {
              <mat-icon class="icon-muted">search_off</mat-icon>
            }
            @case ('filter') {
              <mat-icon class="icon-muted">filter_list_off</mat-icon>
            }
            @default {
              <mat-icon class="icon-muted">help_outline</mat-icon>
            }
          }
        }
      </div>

      <h3 class="empty-state-title">{{ title || getDefaultTitle() }}</h3>

      @if (description) {
        <p class="empty-state-description">{{ description }}</p>
      }

      <div class="empty-state-actions">
        @if (primaryAction) {
          <button
            mat-flat-button
            color="primary"
            (click)="primaryActionClick.emit()">
            @if (primaryActionIcon) {
              <mat-icon>{{ primaryActionIcon }}</mat-icon>
            }
            {{ primaryAction }}
          </button>
        }

        @if (secondaryAction) {
          <button
            mat-stroked-button
            (click)="secondaryActionClick.emit()">
            @if (secondaryActionIcon) {
              <mat-icon>{{ secondaryActionIcon }}</mat-icon>
            }
            {{ secondaryAction }}
          </button>
        }

        @if (showRetry && type === 'error') {
          <button
            mat-stroked-button
            color="warn"
            (click)="retryClick.emit()">
            <mat-icon>refresh</mat-icon>
            Retry
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      text-align: center;
      min-height: 200px;
    }

    .empty-state-icon {
      margin-bottom: 16px;
    }

    .empty-state-icon mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
    }

    .empty-state-icon .icon-muted {
      color: #9e9e9e;
    }

    .empty-state-icon .icon-error {
      color: #f44336;
    }

    .empty-state-icon .icon-success {
      color: #4caf50;
    }

    .empty-state-icon .icon-warning {
      color: #ff9800;
    }

    .empty-state-icon .icon-primary {
      color: #1976d2;
    }

    .empty-state-title {
      margin: 0 0 8px 0;
      font-size: 18px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
    }

    .empty-state-description {
      margin: 0 0 24px 0;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.6);
      max-width: 400px;
      line-height: 1.5;
    }

    .empty-state-actions {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      justify-content: center;
    }

    .empty-state-actions button mat-icon {
      margin-right: 4px;
    }

    /* Compact variant */
    .empty-state.compact {
      padding: 24px 16px;
      min-height: 120px;
    }

    .empty-state.compact .empty-state-icon mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    .empty-state.compact .empty-state-title {
      font-size: 16px;
    }

    /* Dark theme support */
    :host-context(.dark-theme) .empty-state-title {
      color: rgba(255, 255, 255, 0.87);
    }

    :host-context(.dark-theme) .empty-state-description {
      color: rgba(255, 255, 255, 0.6);
    }

    :host-context(.dark-theme) .empty-state-icon .icon-muted {
      color: #757575;
    }
  `]
})
export class EmptyStateComponent {
  @Input() type: EmptyStateType = 'no-data';
  @Input() title = '';
  @Input() description = '';
  @Input() icon = '';
  @Input() iconClass = 'icon-muted';
  @Input() containerClass = '';

  // Action buttons
  @Input() primaryAction = '';
  @Input() primaryActionIcon = '';
  @Input() secondaryAction = '';
  @Input() secondaryActionIcon = '';
  @Input() showRetry = false;

  @Output() primaryActionClick = new EventEmitter<void>();
  @Output() secondaryActionClick = new EventEmitter<void>();
  @Output() retryClick = new EventEmitter<void>();

  getDefaultTitle(): string {
    switch (this.type) {
      case 'no-data':
        return 'No data found';
      case 'error':
        return 'Something went wrong';
      case 'search':
        return 'No results found';
      case 'filter':
        return 'No matching items';
      default:
        return 'Nothing here';
    }
  }
}
