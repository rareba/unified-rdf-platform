import { Component, computed, inject, input } from '@angular/core';

import { Clipboard } from '@angular/cdk/clipboard';

import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ObButtonModule, ObIconModule } from '@oblique/oblique';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'cube-validation-barnard59-cli-command',
  standalone: true,
  imports: [
    MatTooltipModule,
    MatIconModule,
    MatButtonModule,
    ObButtonModule,
    ObIconModule,

  ],
  templateUrl: './cub-validation-barnard59-cli-command.component.html',
  styleUrl: './cub-validation-barnard59-cli-command.component.scss'
})
export class CubeValidationBarnard59CliCommandComponent {

  endpoint = input.required<string>();
  cubeIri = input.required<string>();
  validationProfileUrl = input<string | null>(null);

  readonly #clipboard = inject(Clipboard);

  command = computed<string | null>(() => {
    const endpoint = this.endpoint();
    const cubeIri = this.cubeIri();
    const validationProfileUrl = this.validationProfileUrl();
    if (!endpoint || !cubeIri || !validationProfileUrl) {
      return null;
    }
    if (endpoint.includes('"') || cubeIri.includes('"') || validationProfileUrl.includes('"')) {
      return null;
    }
    const command = `npx barnard59 cube fetch-metadata --endpoint "${endpoint}" --cube "${cubeIri}" | npx barnard59 cube check-metadata --profile "${validationProfileUrl}" | npx barnard59 shacl report-summary`;
    return command;
  });

  copyToClipboard() {
    const command = this.command();
    if (command) {
      this.#clipboard.copy(command);
    }
  }
}
