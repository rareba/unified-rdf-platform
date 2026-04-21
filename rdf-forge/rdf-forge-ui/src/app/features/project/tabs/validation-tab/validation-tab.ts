import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Cockpit } from '../../../validation/cockpit';
import { ProjectContextService } from '../../services/project-context.service';

/**
 * Project-workspace tab that hosts the Phase 5 Validation Cockpit,
 * scoped to the current project.
 */
@Component({
  selector: 'app-validation-tab',
  standalone: true,
  imports: [CommonModule, Cockpit],
  template: `
    @if (projectId(); as pid) {
      <rdf-validation-cockpit [projectId]="pid"></rdf-validation-cockpit>
    } @else {
      <div class="empty">No project selected.</div>
    }
  `,
  styles: [`
    .empty { padding: 24px; text-align: center; color: rgba(0,0,0,0.6); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValidationTab {
  private readonly context = inject(ProjectContextService);
  readonly projectId = computed(() => this.context.projectId());
}
