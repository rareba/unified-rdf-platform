import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-validation-errors',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (errors && errors.length > 0) {
      <div class="validation-errors">
        @for (error of errors; track $index) {
          <div class="error-item">{{ error.message || 'Validation error' }}</div>
        }
      </div>
    }
  `,
  styles: [`
    .validation-errors { margin: 8px 0; }
    .error-item { padding: 4px 8px; color: #c62828; font-size: 13px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValidationErrorsComponent {
  @Input() errors: any[] = [];
}
