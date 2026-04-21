import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-phase-placeholder',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    <mat-card class="phase-card">
      <mat-card-content>
        <div class="phase-header">
          <div class="icon-wrapper">
            <mat-icon>{{ icon() }}</mat-icon>
          </div>
          <div class="phase-title">
            <h2>{{ title() }}</h2>
            <span class="phase-badge">{{ phase() }}</span>
          </div>
        </div>

        <p class="phase-description">{{ description() }}</p>

        @if (features().length > 0) {
          <div class="phase-features">
            <h3>Planned capabilities</h3>
            <ul>
              @for (feature of features(); track feature) {
                <li>
                  <mat-icon class="feature-check">check_circle_outline</mat-icon>
                  <span>{{ feature }}</span>
                </li>
              }
            </ul>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .phase-card {
      max-width: 720px;
      margin: 0 auto;
    }

    .phase-header {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 12px;
    }

    .icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 12px;
      background: var(--rdf-surface-variant, rgba(59, 130, 246, 0.12));
      color: var(--rdf-primary, #3b82f6);
      flex-shrink: 0;

      mat-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
      }
    }

    .phase-title {
      display: flex;
      flex-direction: column;
      gap: 6px;

      h2 {
        margin: 0;
        font-size: 1.25rem;
        font-weight: 600;
      }
    }

    .phase-badge {
      display: inline-block;
      width: fit-content;
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      background: var(--rdf-status-warning-bg, #fff4e5);
      color: var(--rdf-status-warning-fg, #b45309);
    }

    .phase-description {
      margin: 0 0 20px;
      color: var(--rdf-text-secondary);
      font-size: 0.95rem;
      line-height: 1.5;
    }

    .phase-features {
      h3 {
        margin: 0 0 12px;
        font-size: 0.875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--rdf-text-secondary);
      }

      ul {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }

      li {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        font-size: 0.9rem;
      }

      .feature-check {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: var(--rdf-status-success-fg, #2e7d32);
        flex-shrink: 0;
        margin-top: 1px;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PhasePlaceholder {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
  readonly phase = input<string>('Coming soon');
  readonly features = input<string[]>([]);
}
