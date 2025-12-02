import { Component, input, output } from '@angular/core';
import { CubeItem } from '../model/cube-item';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ObLanguageModule } from '@oblique/oblique';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'cube-list-item',
  standalone: true,
  imports: [MatTooltipModule, MatCardModule],
  templateUrl: './list-item.component.html',
  styleUrl: './list-item.component.scss'
})
export class ListItemComponent {
  cube = input.required<CubeItem>();
  selected = output<string>();

  selectItem(cubeIri: string) {
    this.selected.emit(cubeIri);
  }
}
