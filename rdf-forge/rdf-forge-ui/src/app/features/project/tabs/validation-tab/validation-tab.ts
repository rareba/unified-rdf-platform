import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PhasePlaceholder } from '../../shared/phase-placeholder/phase-placeholder';

@Component({
  selector: 'app-validation-tab',
  standalone: true,
  imports: [PhasePlaceholder],
  template: `
    <app-phase-placeholder
      icon="verified"
      title="Validation Cockpit"
      phase="Phase 5"
      description="Continuously validate every dataset in the project against its SHACL shapes. Failures, waivers, severity trends — all in one actionable dashboard."
      [features]="[
        'Aggregated SHACL report across datasets',
        'Severity filtering and grouping',
        'Waiver + exemption workflow',
        'Trend charts and regression alerts',
        'Per-shape drill-down'
      ]">
    </app-phase-placeholder>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValidationTab {}
