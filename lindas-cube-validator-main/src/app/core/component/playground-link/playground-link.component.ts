import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ObButtonModule, ObExternalLinkModule, ObIconModule } from '@oblique/oblique';

@Component({
  selector: 'cube-playground-link',
  standalone: true,
  imports: [
    MatTooltipModule,
    MatIconModule,
    MatButtonModule,
    ObButtonModule,
    ObIconModule,
    ObExternalLinkModule
  ],
  templateUrl: './playground-link.component.html',
  styleUrl: './playground-link.component.scss'
})
export class PlaygroundLinkComponent {
  playgroundLink = input.required<string | undefined>();
}
