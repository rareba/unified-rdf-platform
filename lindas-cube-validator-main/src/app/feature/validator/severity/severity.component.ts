import { Component, computed, input } from '@angular/core';

import { MatChipsModule } from '@angular/material/chips';
import { Severity, ShaclToObliqueSeverityMap } from './severity.map';
import { NgClass } from '@angular/common';

/**
 * SeverityComponent
 * This components maps SHACL severity to oblique severity (Batch Color). 
 */
@Component({
  selector: 'cube-severity',
  standalone: true,
  imports: [NgClass, MatChipsModule],
  templateUrl: './severity.component.html',
  styleUrl: './severity.component.scss'
})
export class SeverityComponent {
  severity = input.required<string | undefined | null>();

  severityInstance = computed<Severity | undefined>(() => {
    const severity = this.severity();

    if (!severity) {
      return undefined;
    }
    return ShaclToObliqueSeverityMap.get(severity);
  }
  );


  stopPropagation(event: Event) {
    event.stopPropagation();
  }
}
