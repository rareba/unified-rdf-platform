import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectContextService } from '../../services/project-context.service';
import { MappingList } from '../../../mapping/mapping-list';

/**
 * Project workspace "Mapping" tab. Thin wrapper that scopes the embedded
 * {@link MappingList} component to the currently-active project supplied by
 * {@link ProjectContextService}. Selecting a mapping navigates to the
 * full-page Studio at {@code /mappings/:id}.
 */
@Component({
  selector: 'app-mapping-tab',
  standalone: true,
  imports: [CommonModule, MappingList],
  template: `
    @if (projectId()) {
      <app-mapping-list [projectId]="projectId()!"></app-mapping-list>
    } @else {
      <p class="hint">No project selected.</p>
    }
  `,
  styles: [`
    .hint { padding: 24px; color: var(--rdf-text-secondary); text-align: center; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MappingTab {
  private readonly ctx = inject(ProjectContextService);
  readonly projectId = computed(() => this.ctx.projectId());
}
