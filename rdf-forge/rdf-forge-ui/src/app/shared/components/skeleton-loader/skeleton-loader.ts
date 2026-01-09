import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type SkeletonType = 'text' | 'circle' | 'rectangle' | 'card' | 'table-row' | 'list-item';

@Component({
  selector: 'app-skeleton-loader',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-container" [class]="containerClass">
      @switch (type) {
        @case ('text') {
          @for (line of lines; track $index) {
            <div
              class="skeleton skeleton-text"
              [style.width]="getLineWidth($index)"
              [style.height]="height"
              [class.animate]="animate">
            </div>
          }
        }
        @case ('circle') {
          <div
            class="skeleton skeleton-circle"
            [style.width]="width"
            [style.height]="width"
            [class.animate]="animate">
          </div>
        }
        @case ('rectangle') {
          <div
            class="skeleton skeleton-rectangle"
            [style.width]="width"
            [style.height]="height"
            [class.animate]="animate">
          </div>
        }
        @case ('card') {
          <div class="skeleton-card" [class.animate]="animate">
            <div class="skeleton skeleton-rectangle card-image"></div>
            <div class="card-content">
              <div class="skeleton skeleton-text title"></div>
              <div class="skeleton skeleton-text subtitle"></div>
              <div class="skeleton skeleton-text body"></div>
            </div>
          </div>
        }
        @case ('table-row') {
          <div class="skeleton-table-row" [class.animate]="animate">
            @for (col of columns; track $index) {
              <div
                class="skeleton skeleton-rectangle table-cell"
                [style.flex]="col.flex || 1">
              </div>
            }
          </div>
        }
        @case ('list-item') {
          <div class="skeleton-list-item" [class.animate]="animate">
            @if (showAvatar) {
              <div class="skeleton skeleton-circle avatar"></div>
            }
            <div class="list-content">
              <div class="skeleton skeleton-text primary"></div>
              <div class="skeleton skeleton-text secondary"></div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .skeleton-container {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .skeleton {
      background: linear-gradient(90deg, #e0e0e0 25%, #f0f0f0 50%, #e0e0e0 75%);
      background-size: 200% 100%;
      border-radius: 4px;
    }

    .skeleton.animate {
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .skeleton-text {
      height: 16px;
    }

    .skeleton-circle {
      border-radius: 50%;
    }

    .skeleton-rectangle {
      border-radius: 4px;
    }

    /* Card skeleton */
    .skeleton-card {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      overflow: hidden;
    }

    .skeleton-card .card-image {
      height: 140px;
      width: 100%;
      border-radius: 0;
    }

    .skeleton-card .card-content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .skeleton-card .title {
      width: 70%;
      height: 20px;
    }

    .skeleton-card .subtitle {
      width: 50%;
      height: 16px;
    }

    .skeleton-card .body {
      width: 90%;
      height: 14px;
    }

    /* Table row skeleton */
    .skeleton-table-row {
      display: flex;
      gap: 16px;
      padding: 12px 16px;
      border-bottom: 1px solid #e0e0e0;
    }

    .skeleton-table-row .table-cell {
      height: 20px;
    }

    /* List item skeleton */
    .skeleton-list-item {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 12px 16px;
    }

    .skeleton-list-item .avatar {
      width: 40px;
      height: 40px;
      flex-shrink: 0;
    }

    .skeleton-list-item .list-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .skeleton-list-item .primary {
      width: 60%;
      height: 18px;
    }

    .skeleton-list-item .secondary {
      width: 40%;
      height: 14px;
    }

    /* Dark theme support */
    :host-context(.dark-theme) .skeleton {
      background: linear-gradient(90deg, #424242 25%, #616161 50%, #424242 75%);
      background-size: 200% 100%;
    }

    :host-context(.dark-theme) .skeleton-card {
      border-color: #424242;
    }

    :host-context(.dark-theme) .skeleton-table-row {
      border-color: #424242;
    }
  `]
})
export class SkeletonLoaderComponent {
  @Input() type: SkeletonType = 'text';
  @Input() width = '100%';
  @Input() height = '16px';
  @Input() count = 1;
  @Input() animate = true;
  @Input() containerClass = '';
  @Input() showAvatar = true;

  // For table rows
  @Input() columns: { flex?: number }[] = [
    { flex: 1 },
    { flex: 2 },
    { flex: 1 },
    { flex: 1 }
  ];

  get lines(): number[] {
    return Array(this.count).fill(0);
  }

  getLineWidth(index: number): string {
    // Make last line shorter for natural look
    if (index === this.count - 1 && this.count > 1) {
      return '70%';
    }
    // Vary line lengths slightly
    const widths = ['100%', '95%', '90%', '85%'];
    return widths[index % widths.length];
  }
}
