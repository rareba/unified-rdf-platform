import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-publish-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="cloud_upload"
      title="Publish + Release Factory"
      phase="Phase 6"
      description="Build reproducible releases of your project data. Tag, sign, and publish to any triplestore, object store, or SPARQL endpoint — with full audit trails."
      [features]="[
        'Semantic-versioned releases (X.Y.Z)',
        'Multi-target publishing (GraphDB, Fuseki, S3)',
        'Release notes and changelog',
        'Signed DCAT-AP catalogue entries',
        'Promotion between environments (dev → stage → prod)'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublishTab {}
