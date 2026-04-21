import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-lineage-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="account_tree"
      title="Provenance / Lineage"
      phase="Phase 6"
      description="Trace every triple back to its source. PROV-O graphs link raw inputs, transformations, validation outcomes, and released cubes in one navigable view."
      [features]="[
        'End-to-end PROV-O lineage graph',
        'Drill from cube back to source row',
        'Activity + agent annotations',
        'Exportable provenance bundles',
        'SPARQL-queryable provenance store'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineageTab {}
