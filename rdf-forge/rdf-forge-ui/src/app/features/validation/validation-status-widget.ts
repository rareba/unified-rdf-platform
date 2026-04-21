import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { catchError, of } from 'rxjs';

import { ProjectValidationHealth } from '../../core/models/validation.model';
import { ValidationService } from '../../core/services/validation.service';

/**
 * Overview-tab widget that fetches the aggregate validation health for the
 * current project and renders a green/amber/red badge + counts. Clicking
 * navigates to the Validation tab.
 */
@Component({
  selector: 'rdf-validation-status-widget',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, MatButtonModule, MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (health(); as h) {
      @if (h.suiteCount === 0) {
        <p class="summary-empty">
          No validation suites yet. Create one in the Validation tab to monitor
          your data quality.
        </p>
        <a mat-stroked-button color="primary" routerLink="../validation">
          <mat-icon>verified</mat-icon>&nbsp;Open Validation
        </a>
      } @else {
        <div class="status-row">
          <mat-chip [class]="chipClass(h)">{{ statusLabel(h) }}</mat-chip>
          <span class="muted">{{ h.suiteCount }} suite(s)</span>
        </div>
        <div class="counts">
          <span class="pill healthy"> {{ h.passedCount }} passed </span>
          <span class="pill warning"> {{ h.warningCount }} warnings </span>
          <span class="pill danger">  {{ h.failedCount }} failing </span>
        </div>
        <a mat-stroked-button color="primary" routerLink="../validation">
          <mat-icon>open_in_new</mat-icon>&nbsp;Open Validation
        </a>
      }
    } @else {
      <p class="summary-empty">Loading validation status…</p>
    }
  `,
  styles: [`
    .status-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
    .muted { color: rgba(0,0,0,0.6); font-size: 12px; }
    .counts { display: flex; gap: 6px; margin: 8px 0 12px; flex-wrap: wrap; }
    .pill {
      padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500;
    }
    .pill.healthy { background: #c8e6c9; color: #1b5e20; }
    .pill.warning { background: #fff59d; color: #795500; }
    .pill.danger  { background: #ffcdd2; color: #b71c1c; }
    .summary-empty { color: rgba(0,0,0,0.6); font-style: italic; }
    mat-chip.healthy { background: #c8e6c9; color: #1b5e20; }
    mat-chip.warning { background: #fff59d; color: #795500; }
    mat-chip.danger  { background: #ffcdd2; color: #b71c1c; }
    mat-chip.unknown { background: rgba(0,0,0,0.08); }
  `]
})
export class ValidationStatusWidget implements OnChanges {
  private readonly svc = inject(ValidationService);

  @Input() projectId: string | null = null;

  readonly health = signal<ProjectValidationHealth | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectId']) this.refresh();
  }

  refresh(): void {
    if (!this.projectId) { this.health.set(null); return; }
    this.svc.projectHealth(this.projectId).pipe(
      catchError(() => of<ProjectValidationHealth>({
        status: 'unknown', suiteCount: 0, passedCount: 0,
        warningCount: 0, failedCount: 0
      }))
    ).subscribe(h => this.health.set(h));
  }

  chipClass(h: ProjectValidationHealth): string {
    switch (h.status) {
      case 'green': return 'healthy';
      case 'amber': return 'warning';
      case 'red':   return 'danger';
      default:      return 'unknown';
    }
  }

  statusLabel(h: ProjectValidationHealth): string {
    switch (h.status) {
      case 'green': return 'All green';
      case 'amber': return 'Warnings';
      case 'red':   return 'Failing';
      default:      return 'Unknown';
    }
  }
}
