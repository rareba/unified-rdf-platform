import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-docs-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="description"
      title="Generated API + Ontology Docs"
      phase="Phase 10"
      description="Every project ships with an auto-generated, human-readable documentation site. Classes, shapes, example queries, and change history — all kept in sync with the live data."
      [features]="[
        'Auto-generated ontology docs (Widoco-style)',
        'SHACL shape reference with examples',
        'SPARQL query cookbook',
        'DCAT-AP catalogue entry preview',
        'Publishable as static site'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DocsTab {}
