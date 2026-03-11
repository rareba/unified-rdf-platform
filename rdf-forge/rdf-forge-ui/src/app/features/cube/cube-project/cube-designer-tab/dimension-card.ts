import {
  Component,
  input,
  output,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ColumnMapping } from '../../../../core/models/cube.model';

@Component({
  selector: 'app-dimension-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule
  ],
  template: `
    <mat-card class="dimension-card">
      <mat-card-content class="card-content">

        <!-- Role badge -->
        <div class="role-badge-row">
          <span class="role-badge role-{{ mapping().role }}">
            {{ getRoleLabel(mapping().role) }}
          </span>
          @if (mapping().keyDimension) {
            <mat-icon
              class="key-icon"
              matTooltip="Key dimension — uniquely identifies an observation"
              aria-label="Key dimension">
              key
            </mat-icon>
          }
          @if (mapping().sharedDimensionUri) {
            <span class="linked-chip" matTooltip="{{ mapping().sharedDimensionUri }}">
              <mat-icon class="linked-icon">link</mat-icon>
              Linked
            </span>
          }
        </div>

        <!-- Name -->
        <div class="dimension-name" [title]="mapping().name">{{ mapping().name }}</div>

        <!-- Datatype -->
        @if (mapping().datatype) {
          <div class="meta-text">{{ mapping().datatype }}</div>
        }

        <!-- Scale type -->
        @if (mapping().scaleType) {
          <div class="meta-text scale-type">{{ mapping().scaleType }}</div>
        }

        <!-- Unit label (measures) -->
        @if (mapping().role === 'measure' && mapping().unitLabel) {
          <div class="meta-text unit-label">{{ mapping().unitLabel }}</div>
        }

      </mat-card-content>

      <mat-card-actions class="card-actions">
        <button mat-button color="primary" (click)="edit.emit(mapping())">
          <mat-icon>edit</mat-icon>
          Edit
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    :host {
      display: block;
      flex-shrink: 0;
    }

    .dimension-card {
      width: 200px;
      min-height: 140px;
      display: flex;
      flex-direction: column;
    }

    .card-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 12px 12px 4px;
    }

    .role-badge-row {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .role-badge {
      padding: 2px 8px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .role-dimension  { background: #ede7f6; color: #4527a0; }
    .role-measure    { background: #e3f2fd; color: #1565c0; }
    .role-attribute  { background: #e8f5e9; color: #2e7d32; }
    .role-ignore     { background: #f5f5f5; color: #757575; }

    .key-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: #f57c00;
    }

    .linked-chip {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      background: #e0f7fa;
      color: #00838f;
      padding: 1px 6px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 500;
      cursor: default;
    }

    .linked-icon {
      font-size: 12px;
      width: 12px;
      height: 12px;
    }

    .dimension-name {
      font-weight: 600;
      font-size: 0.9rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .meta-text {
      font-size: 0.75rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .scale-type::before {
      content: 'Scale: ';
    }

    .unit-label::before {
      content: 'Unit: ';
    }

    .card-actions {
      padding: 0 8px 8px;
      min-height: unset;
    }
  `]
})
export class DimensionCard {
  readonly mapping = input.required<ColumnMapping>();
  readonly edit = output<ColumnMapping>();

  getRoleLabel(role: string): string {
    switch (role) {
      case 'dimension':  return 'Key Dimension';
      case 'measure':    return 'Measure';
      case 'attribute':  return 'Attribute';
      case 'ignore':     return 'Ignored';
      default:           return role;
    }
  }
}
