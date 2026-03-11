import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-shape-visualizer',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (data) {
      <div class="shape-visualizer">
        <p>Shape visualization ({{ data.nodes?.length || 0 }} nodes, {{ data.edges?.length || 0 }} edges)</p>
      </div>
    }
  `,
  styles: [`
    .shape-visualizer { padding: 16px; border: 1px solid #e0e0e0; border-radius: 4px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShapeVisualizerComponent {
  @Input() data: any = null;
}
