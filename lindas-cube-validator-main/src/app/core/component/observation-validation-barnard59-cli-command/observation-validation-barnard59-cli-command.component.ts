import { Component, computed, inject, input } from '@angular/core';

import { Clipboard } from '@angular/cdk/clipboard';

import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ObButtonModule, ObIconModule } from '@oblique/oblique';

@Component({
  selector: 'cube-observation-validation-barnard59-cli-command',
  standalone: true,
  imports: [
    MatTooltipModule,
    MatIconModule,
    MatButtonModule,
    ObButtonModule,
    ObIconModule,

  ], templateUrl: './observation-validation-barnard59-cli-command.component.html',
  styleUrl: './observation-validation-barnard59-cli-command.component.scss'
})
export class ObservationValidationBarnard59CliCommandComponent {
  endpoint = input.required<string>();
  cubeIri = input.required<string>();

  readonly #clipboard = inject(Clipboard);

  command = computed<string | null>(() => {
    const endpoint = this.endpoint();
    const cubeIri = this.cubeIri();
    if (!endpoint || !cubeIri) {
      return null;
    }
    if (endpoint.includes('"') || cubeIri.includes('"')) {
      return null;
    }
    const command = `npx barnard59 cube fetch-metadata --endpoint "${endpoint}" --cube "${cubeIri}" > metadata.ttl 
    npx barnard59 cube fetch-observations --endpoint  "${endpoint}" --cube "${cubeIri}" | npx barnard59 cube check-observations --constraint metadata.ttl | npx barnard59 shacl report-summary
    `;
    return command;
  });

  copyToClipboard() {
    const command = this.command();
    if (command) {
      this.#clipboard.copy(command);
    }
  }
}
