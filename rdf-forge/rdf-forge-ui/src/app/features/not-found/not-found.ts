import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-not-found',
  imports: [
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="not-found-container">
      <mat-icon class="not-found-icon">explore_off</mat-icon>
      <h1>404</h1>
      <h2>Page Not Found</h2>
      <p class="not-found-message">
        The page <code>{{ currentUrl }}</code> does not exist or has been moved.
      </p>
      <button mat-flat-button color="primary" (click)="goToDashboard()">
        <mat-icon>home</mat-icon>
        Go to Dashboard
      </button>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
    }

    .not-found-container {
      text-align: center;
      padding: 48px 24px;
      max-width: 480px;
    }

    .not-found-icon {
      font-size: 80px;
      width: 80px;
      height: 80px;
      color: var(--mat-sys-outline, rgba(0, 0, 0, 0.38));
      margin-bottom: 16px;
    }

    h1 {
      font-size: 72px;
      font-weight: 700;
      margin: 0;
      color: var(--mat-sys-on-surface, rgba(0, 0, 0, 0.87));
      line-height: 1;
    }

    h2 {
      font-size: 24px;
      font-weight: 400;
      margin: 8px 0 24px;
      color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
    }

    .not-found-message {
      font-size: 14px;
      color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      margin-bottom: 32px;
      line-height: 1.5;
    }

    code {
      background: var(--mat-sys-surface-variant, rgba(0, 0, 0, 0.04));
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 13px;
      word-break: break-all;
    }

    button mat-icon {
      margin-right: 8px;
    }
  `
})
export class NotFound {
  private readonly router = inject(Router);

  readonly currentUrl = this.router.url;

  goToDashboard(): void {
    this.router.navigate(['/']);
  }
}
