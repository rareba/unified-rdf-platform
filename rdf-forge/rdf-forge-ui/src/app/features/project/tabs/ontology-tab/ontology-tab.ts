import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectContextService } from '../../services/project-context.service';
import { OntologyList } from '../../../ontology/ontology-list';

/**
 * Project workspace tab that hosts the ontology list for the current project.
 * Replaces the Phase 2 placeholder.
 */
@Component({
  selector: 'app-ontology-tab',
  standalone: true,
  imports: [CommonModule, OntologyList],
  template: `
    @if (projectId(); as pid) {
      <app-ontology-list [projectIdInput]="pid"></app-ontology-list>
    } @else {
      <div class="empty">Select a project to view its ontologies.</div>
    }
  `,
  styles: [`
    :host { display: block; padding: 16px; }
    .empty {
      text-align: center;
      color: var(--rdf-text-secondary);
      padding: 48px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OntologyTab {
  private readonly context = inject(ProjectContextService);

  readonly projectId = this.context.projectId;
}
