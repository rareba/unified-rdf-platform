import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ProjectContextService } from '../../services/project-context.service';

@Component({
  selector: 'app-data-tab',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatIconModule, MatButtonModule],
  template: `
    <mat-card class="data-card">
      <mat-card-content>
        <div class="data-header">
          <div class="icon-wrapper">
            <mat-icon>storage</mat-icon>
          </div>
          <div>
            <h2>Project Data Sources</h2>
            <p class="subtitle">Data sources scoped to this project.</p>
          </div>
        </div>

        <p class="body">
          The data manager is being embedded into the project workspace in a
          future iteration. In the meantime, open the global Data Manager with
          this project pre-selected.
        </p>

        @if (projectId(); as pid) {
          <div class="actions">
            <a mat-raised-button color="primary"
               [routerLink]="['/data']"
               [queryParams]="{ projectId: pid }">
              <mat-icon>open_in_new</mat-icon>
              Open Data Manager
            </a>
            <button mat-button (click)="openInNewTab(pid)">
              <mat-icon>launch</mat-icon>
              Open in new tab
            </button>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .data-card {
      max-width: 720px;
      margin: 0 auto;
    }

    .data-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 16px;

      h2 {
        margin: 0 0 4px;
        font-size: 1.25rem;
        font-weight: 600;
      }

      .subtitle {
        margin: 0;
        color: var(--rdf-text-secondary);
        font-size: 0.9rem;
      }
    }

    .icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 48px;
      height: 48px;
      border-radius: 10px;
      background: var(--rdf-surface-variant, rgba(59, 130, 246, 0.12));
      color: var(--rdf-primary, #3b82f6);

      mat-icon {
        font-size: 24px;
        width: 24px;
        height: 24px;
      }
    }

    .body {
      margin: 0 0 16px;
      color: var(--rdf-text-secondary);
      font-size: 0.95rem;
      line-height: 1.5;
    }

    .actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataTab {
  private readonly context = inject(ProjectContextService);
  private readonly router = inject(Router);

  readonly projectId = this.context.projectId;

  openInNewTab(pid: string): void {
    const url = this.router.serializeUrl(
      this.router.createUrlTree(['/data'], { queryParams: { projectId: pid } })
    );
    window.open(url, '_blank', 'noopener');
  }
}
