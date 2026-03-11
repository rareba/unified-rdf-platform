import {
  Component,
  input,
  inject,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { Cube } from '../../../../core/models/cube.model';

interface PipelineStep {
  label: string;
  borderColor: string;
  show: boolean;
}

@Component({
  selector: 'app-mini-pipeline-preview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="pipeline-preview">
      <div class="preview-title">Pipeline Steps</div>

      @if (cube().pipelineId) {
        <div class="steps-list">
          @for (step of visibleSteps(); track step.label) {
            <div class="step-card" [style.border-left-color]="step.borderColor">
              <span class="step-label">{{ step.label }}</span>
            </div>
          }
        </div>

        <a
          class="open-pipeline-link"
          (click)="openPipeline()"
          (keydown.enter)="openPipeline()"
          role="link"
          tabindex="0">
          <mat-icon class="link-icon">open_in_new</mat-icon>
          Open in Pipeline Designer
        </a>
      } @else {
        <div class="no-pipeline">
          <mat-icon>warning_amber</mat-icon>
          <span>Pipeline not yet generated</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .pipeline-preview {
      width: 220px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
      border-left: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
    }

    .preview-title {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .steps-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .step-card {
      padding: 8px 10px;
      border-left: 3px solid transparent;
      border-radius: 0 4px 4px 0;
      background: var(--mat-sys-surface-variant, #f5f5f5);
      font-size: 0.8125rem;
    }

    .step-label {
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .open-pipeline-link {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.8125rem;
      color: var(--mat-sys-primary, #1976d2);
      cursor: pointer;
      text-decoration: none;
      margin-top: 4px;
    }

    .open-pipeline-link:hover {
      text-decoration: underline;
    }

    .link-icon {
      font-size: 14px;
      width: 14px;
      height: 14px;
    }

    .no-pipeline {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 16px 8px;
      text-align: center;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      font-size: 0.8125rem;
    }

    .no-pipeline mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: #f59e0b;
    }
  `]
})
export class MiniPipelinePreview {
  private readonly router = inject(Router);

  readonly cube = input.required<Cube>();

  visibleSteps(): PipelineStep[] {
    const c = this.cube();
    return [
      { label: 'Load CSV',              borderColor: '#2196f3', show: true },
      { label: 'Create Observations',   borderColor: '#4caf50', show: true },
      { label: 'Validate SHACL',        borderColor: '#ff9800', show: !!c.shapeId },
      { label: 'Publish to Triplestore',borderColor: '#9c27b0', show: true }
    ].filter(s => s.show);
  }

  openPipeline(): void {
    const pipelineId = this.cube().pipelineId;
    if (pipelineId) {
      this.router.navigate(['/pipelines', pipelineId]);
    }
  }
}
