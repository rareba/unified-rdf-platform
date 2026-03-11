import {
  Component,
  input,
  output,
  OnChanges,
  SimpleChanges,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDividerModule } from '@angular/material/divider';
import { ColumnMapping } from '../../../../core/models/cube.model';

interface EditForm {
  name:        FormControl<string>;
  description: FormControl<string>;
  scaleType:   FormControl<string>;
  unitUri:     FormControl<string>;
  unitLabel:   FormControl<string>;
}

const SCALE_TYPES = ['Nominal', 'Ordinal', 'Ratio', 'Interval'] as const;

/** Derive a human-readable "data kind" from an XSD datatype string. */
function deriveDataKind(datatype: string | undefined): string {
  if (!datatype) return '—';
  if (datatype.includes('integer') || datatype.includes('decimal') || datatype.includes('float') || datatype.includes('double')) {
    return 'Numeric';
  }
  if (datatype.includes('date') || datatype.includes('Year') || datatype.includes('dateTime')) {
    return 'Temporal';
  }
  if (datatype.includes('boolean')) {
    return 'Boolean';
  }
  return 'Textual';
}

@Component({
  selector: 'app-dimension-edit-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    MatDividerModule
  ],
  template: `
    @if (mapping()) {
      <div class="edit-panel-overlay" (click)="onOverlayClick($event)">
        <div class="edit-panel" role="dialog" aria-label="Edit dimension">

          <div class="panel-header">
            <h3 class="panel-title">
              <mat-icon>tune</mat-icon>
              Edit Dimension
            </h3>
            <button mat-icon-button (click)="onCancel()" aria-label="Close">
              <mat-icon>close</mat-icon>
            </button>
          </div>

          <mat-divider></mat-divider>

          <div class="panel-body" [formGroup]="form">

            <!-- Name -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" autocomplete="off" />
            </mat-form-field>

            <!-- Description -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description</mat-label>
              <textarea
                matInput
                formControlName="description"
                rows="3"
                autocomplete="off">
              </textarea>
            </mat-form-field>

            <!-- Scale type selector -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Scale Type</mat-label>
              <mat-select formControlName="scaleType">
                <mat-option value="">— Not set —</mat-option>
                @for (st of scaleTypes; track st) {
                  <mat-option [value]="st">{{ st }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <!-- Data kind (readonly) -->
            <div class="readonly-field">
              <span class="readonly-label">Data Kind</span>
              <span class="readonly-value">{{ dataKind }}</span>
            </div>

            <!-- Unit fields (measures only) -->
            @if (mapping()!.role === 'measure') {
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Unit URI</mat-label>
                <input
                  matInput
                  formControlName="unitUri"
                  placeholder="https://qudt.org/vocab/unit/M"
                  autocomplete="off" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Unit Label</mat-label>
                <input
                  matInput
                  formControlName="unitLabel"
                  placeholder="metre"
                  autocomplete="off" />
              </mat-form-field>
            }

            <!-- Link to shared dimension -->
            @if (mapping()!.role === 'dimension') {
              <button
                mat-stroked-button
                class="link-btn"
                type="button"
                (click)="onLinkSharedDimension()">
                <mat-icon>link</mat-icon>
                Link to shared dimension
              </button>
            }

          </div>

          <mat-divider></mat-divider>

          <div class="panel-footer">
            <button mat-button (click)="onCancel()">Cancel</button>
            <button mat-raised-button color="primary" (click)="onSave()">
              <mat-icon>save</mat-icon>
              Save
            </button>
          </div>

        </div>
      </div>
    }
  `,
  styles: [`
    .edit-panel-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.32);
      z-index: 100;
      display: flex;
      justify-content: flex-end;
    }

    .edit-panel {
      width: 400px;
      max-width: 100vw;
      height: 100%;
      background: var(--mat-sys-surface, #fff);
      display: flex;
      flex-direction: column;
      box-shadow: -4px 0 16px rgba(0,0,0,.18);
      animation: slideIn 0.2s ease-out;
    }

    @keyframes slideIn {
      from { transform: translateX(100%); }
      to   { transform: translateX(0); }
    }

    .panel-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
    }

    .panel-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 1rem;
      font-weight: 500;
      flex: 1;
    }

    .panel-body {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .full-width {
      width: 100%;
    }

    .readonly-field {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
      margin-bottom: 4px;
    }

    .readonly-label {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      min-width: 80px;
    }

    .readonly-value {
      font-size: 0.85rem;
      font-weight: 500;
      color: var(--mat-sys-on-surface, rgba(0,0,0,.87));
    }

    .link-btn {
      width: 100%;
      margin-top: 4px;
    }

    .panel-footer {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 12px 16px;
    }
  `]
})
export class DimensionEditPanel implements OnChanges {
  readonly mapping = input<ColumnMapping | null>(null);
  readonly save   = output<ColumnMapping>();
  readonly cancel = output<void>();

  readonly scaleTypes = SCALE_TYPES;
  dataKind = '—';

  readonly form = new FormGroup<EditForm>({
    name:        new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    scaleType:   new FormControl('', { nonNullable: true }),
    unitUri:     new FormControl('', { nonNullable: true }),
    unitLabel:   new FormControl('', { nonNullable: true })
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mapping']) {
      const m = this.mapping();
      if (m) {
        this.dataKind = deriveDataKind(m.datatype);
        this.form.reset({
          name:        m.name,
          description: (m.metadata?.['description'] as string | undefined) ?? '',
          scaleType:   m.scaleType ?? '',
          unitUri:     m.unitUri ?? '',
          unitLabel:   m.unitLabel ?? ''
        });
      }
    }
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('edit-panel-overlay')) {
      this.onCancel();
    }
  }

  onSave(): void {
    if (!this.mapping()) return;
    const v = this.form.getRawValue();
    const updated: ColumnMapping = {
      ...this.mapping()!,
      name:      v.name || this.mapping()!.name,
      scaleType: v.scaleType || undefined,
      unitUri:   this.mapping()!.role === 'measure' ? (v.unitUri || undefined) : undefined,
      unitLabel: this.mapping()!.role === 'measure' ? (v.unitLabel || undefined) : undefined,
      metadata: {
        ...this.mapping()!.metadata,
        description: v.description || undefined
      }
    };
    this.save.emit(updated);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  onLinkSharedDimension(): void {
    // Placeholder — shared dimension picker dialog to be wired in Task 4.x
  }
}
