import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-ontology-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="schema"
      title="Ontology Studio"
      phase="Phase 2"
      description="Model, import, and evolve the domain ontology that underpins this project. Visual class diagrams, reasoning hints, and SKOS concept schemes — all versioned alongside your data."
      [features]="[
        'Visual class and property editor',
        'OWL + SKOS import and export',
        'Namespace + prefix management',
        'Version history with diffs',
        'Auto-generated SHACL stubs from classes'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OntologyTab {}
