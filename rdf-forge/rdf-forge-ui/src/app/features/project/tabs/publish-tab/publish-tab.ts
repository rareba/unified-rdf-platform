import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectContextService } from '../../services/project-context.service';
import { ReleaseList } from '../../../release/release-list';

/**
 * Project workspace "Publish" tab. Thin wrapper that scopes the embedded
 * {@link ReleaseList} component to the currently-active project supplied by
 * {@link ProjectContextService}. Replaces the Phase 6 placeholder.
 */
@Component({
  selector: 'app-publish-tab',
  standalone: true,
  imports: [CommonModule, ReleaseList],
  template: `
    @if (projectId()) {
      <app-release-list [projectId]="projectId()!"></app-release-list>
    } @else {
      <p class="hint">No project selected.</p>
    }
  `,
  styles: [`
    .hint { padding: 24px; color: var(--rdf-text-secondary); text-align: center; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublishTab {
  private readonly ctx = inject(ProjectContextService);
  readonly projectId = computed(() => this.ctx.projectId());
}
