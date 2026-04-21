import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectContextService } from '../../services/project-context.service';
import { LineageGraphComponent } from '../../../lineage/lineage-graph';

/**
 * Project workspace "Lineage" tab. Thin wrapper that scopes the embedded
 * {@link LineageGraphComponent} to the currently-active project supplied by
 * {@link ProjectContextService}. Replaces the Phase 6 placeholder.
 */
@Component({
  selector: 'app-lineage-tab',
  standalone: true,
  imports: [CommonModule, LineageGraphComponent],
  template: `
    @if (projectId()) {
      <app-lineage-graph [projectId]="projectId()!"></app-lineage-graph>
    } @else {
      <p class="hint">No project selected.</p>
    }
  `,
  styles: [`
    .hint { padding: 24px; color: var(--rdf-text-secondary); text-align: center; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineageTab {
  private readonly ctx = inject(ProjectContextService);
  readonly projectId = computed(() => this.ctx.projectId());
}
