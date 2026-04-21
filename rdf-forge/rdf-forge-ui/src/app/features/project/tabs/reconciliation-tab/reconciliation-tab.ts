import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReconciliationDashboard } from '../../../reconciliation/reconciliation-dashboard';
import { ProjectContextService } from '../../services/project-context.service';

/**
 * Host the Phase 8 Reconciliation Dashboard inside the Project workspace tabs.
 * The dashboard reads the projectId from the ActivatedRoute hierarchy — this
 * wrapper just ensures the project context is loaded before rendering.
 */
@Component({
  selector: 'app-reconciliation-tab',
  standalone: true,
  imports: [CommonModule, ReconciliationDashboard],
  template: `
    @if (projectId(); as pid) {
      <rdf-reconciliation-dashboard></rdf-reconciliation-dashboard>
    } @else {
      <div class="empty">No project selected.</div>
    }
  `,
  styles: [`
    .empty { padding: 24px; text-align: center; color: rgba(0,0,0,0.6); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReconciliationTab {
  private readonly context = inject(ProjectContextService);
  readonly projectId = computed(() => this.context.projectId());
}
