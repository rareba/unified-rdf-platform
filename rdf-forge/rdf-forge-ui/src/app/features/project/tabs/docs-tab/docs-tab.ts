import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectContextService } from '../../services/project-context.service';
import { DocsViewer } from '../../../docs/docs-viewer';

/**
 * Project workspace "Docs" tab.
 * Reads {@code projectId} from {@link ProjectContextService} and mounts
 * {@link DocsViewer}, which requests the Semantic API HTML from
 * shacl-service ({@code GET /api/v1/docs/project/{id}?format=HTML}).
 */
@Component({
  selector: 'app-docs-tab',
  standalone: true,
  imports: [CommonModule, DocsViewer],
  template: `
    @if (context.projectId(); as id) {
      <app-docs-viewer [projectId]="id"></app-docs-viewer>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DocsTab {
  readonly context = inject(ProjectContextService);
}
