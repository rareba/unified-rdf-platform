import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-mapping-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="transform"
      title="Universal Mapping Studio"
      phase="Phase 3"
      description="A single place to author mappings from any source (CSV, SQL, JSON, XML) to your ontology. Live preview, schema-aware suggestions, and one-click pipeline generation."
      [features]="[
        'Source-agnostic mapping language',
        'Live preview with sample data',
        'Schema and ontology auto-complete',
        'One-click pipeline generation',
        'R2RML / RML compatibility'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MappingTab {}
