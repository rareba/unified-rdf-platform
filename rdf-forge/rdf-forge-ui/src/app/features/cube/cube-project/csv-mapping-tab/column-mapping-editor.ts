import {
  Component,
  input,
  output,
  OnChanges,
  SimpleChanges,
  ChangeDetectionStrategy,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ColumnMapping } from '../../../../core/models/cube.model';

type MappingRole = 'dimension' | 'measure' | 'attribute' | 'ignore';

interface MappingForm {
  predicateUri: FormControl<string>;
  role: FormControl<MappingRole>;
  datatype: FormControl<string>;
  scaleType: FormControl<string>;
  keyDimension: FormControl<boolean>;
  unitUri: FormControl<string>;
  unitLabel: FormControl<string>;
}

@Component({
  selector: 'app-column-mapping-editor',
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
    MatSlideToggleModule,
    MatDividerModule,
    MatTooltipModule
  ],
  template: `
    @if (mapping()) {
      <div class="mapping-editor-overlay" (click)="onOverlayClick($event)">
        <div class="mapping-editor-panel" role="dialog" aria-label="Edit column mapping">

          <div class="editor-header">
            <h3 class="editor-title">
              <mat-icon>edit</mat-icon>
              Edit Mapping: {{ mapping()!.name }}
            </h3>
            <button mat-icon-button (click)="onCancel()" aria-label="Close">
              <mat-icon>close</mat-icon>
            </button>
          </div>

          <mat-divider></mat-divider>

          <div class="editor-body" [formGroup]="form">

            <!-- Property URI -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Property URI</mat-label>
              <input
                matInput
                formControlName="predicateUri"
                placeholder="https://example.org/property/..."
                autocomplete="off" />
              <mat-hint>Auto-generated from column name — editable</mat-hint>
            </mat-form-field>

            <!-- Role -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Role</mat-label>
              <mat-select formControlName="role">
                <mat-option value="dimension">Key Dimension</mat-option>
                <mat-option value="measure">Measure</mat-option>
                <mat-option value="attribute">Attribute</mat-option>
                <mat-option value="ignore">Ignore</mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Datatype -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Datatype</mat-label>
              <mat-select formControlName="datatype">
                <mat-option value="xsd:string">xsd:string</mat-option>
                <mat-option value="xsd:integer">xsd:integer</mat-option>
                <mat-option value="xsd:decimal">xsd:decimal</mat-option>
                <mat-option value="xsd:date">xsd:date</mat-option>
                <mat-option value="xsd:gYear">xsd:gYear</mat-option>
                <mat-option value="xsd:boolean">xsd:boolean</mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Scale type -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Scale Type</mat-label>
              <mat-select formControlName="scaleType">
                <mat-option value="">— Not set —</mat-option>
                <mat-option value="Nominal">Nominal</mat-option>
                <mat-option value="Ordinal">Ordinal</mat-option>
                <mat-option value="Ratio">Ratio</mat-option>
                <mat-option value="Interval">Interval</mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Key dimension toggle (only for dimension role) -->
            @if (form.controls.role.value === 'dimension') {
              <div class="toggle-row">
                <mat-slide-toggle formControlName="keyDimension">
                  Key Dimension
                </mat-slide-toggle>
                <span class="toggle-hint">
                  Key dimensions uniquely identify an observation
                </span>
              </div>
            }

            <!-- Unit fields (only for measure role) -->
            @if (form.controls.role.value === 'measure') {
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
            @if (form.controls.role.value === 'dimension') {
              <button
                mat-stroked-button
                class="link-dim-btn"
                (click)="onLinkSharedDimension()"
                type="button">
                <mat-icon>link</mat-icon>
                Link to shared dimension
              </button>
            }

          </div>

          <mat-divider></mat-divider>

          <div class="editor-footer">
            <button mat-button (click)="onCancel()">Cancel</button>
            <button
              mat-raised-button
              color="primary"
              (click)="onSave()"
              [disabled]="form.invalid">
              <mat-icon>save</mat-icon>
              Save
            </button>
          </div>

        </div>
      </div>
    }
  `,
  styles: [`
    .mapping-editor-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.32);
      z-index: 100;
      display: flex;
      justify-content: flex-end;
    }

    .mapping-editor-panel {
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

    .editor-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
    }

    .editor-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 1rem;
      font-weight: 500;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .editor-title mat-icon {
      flex-shrink: 0;
    }

    .editor-body {
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

    .toggle-row {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin: 4px 0 8px;
    }

    .toggle-hint {
      font-size: 0.75rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
      padding-left: 4px;
    }

    .link-dim-btn {
      width: 100%;
      margin-top: 4px;
    }

    .editor-footer {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 12px 16px;
    }
  `]
})
export class ColumnMappingEditor implements OnChanges {
  readonly mapping = input<ColumnMapping | null>(null);
  readonly save = output<ColumnMapping>();
  readonly cancel = output<void>();
  readonly linkSharedDimension = output<string>();

  readonly form = new FormGroup<MappingForm>({
    predicateUri:  new FormControl('', { nonNullable: true }),
    role:          new FormControl<MappingRole>('attribute', { nonNullable: true, validators: [Validators.required] }),
    datatype:      new FormControl('xsd:string', { nonNullable: true }),
    scaleType:     new FormControl('', { nonNullable: true }),
    keyDimension:  new FormControl(false, { nonNullable: true }),
    unitUri:       new FormControl('', { nonNullable: true }),
    unitLabel:     new FormControl('', { nonNullable: true })
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mapping']) {
      const m = this.mapping();
      if (m) {
        this.form.reset({
          predicateUri: m.predicateUri ?? this.generatePredicateUri(m.name),
          role:         m.role ?? 'attribute',
          datatype:     m.datatype ?? 'xsd:string',
          scaleType:    m.scaleType ?? '',
          keyDimension: m.keyDimension ?? false,
          unitUri:      m.unitUri ?? '',
          unitLabel:    m.unitLabel ?? ''
        });
      }
    }
  }

  onOverlayClick(event: MouseEvent): void {
    // Only close when clicking the dark backdrop, not the panel itself
    if ((event.target as HTMLElement).classList.contains('mapping-editor-overlay')) {
      this.onCancel();
    }
  }

  onSave(): void {
    if (this.form.invalid || !this.mapping()) return;
    const v = this.form.getRawValue();
    const updated: ColumnMapping = {
      ...this.mapping()!,
      predicateUri:  v.predicateUri || undefined,
      role:          v.role,
      datatype:      v.datatype || undefined,
      scaleType:     v.scaleType || undefined,
      keyDimension:  v.role === 'dimension' ? v.keyDimension : undefined,
      unitUri:       v.role === 'measure' ? (v.unitUri || undefined) : undefined,
      unitLabel:     v.role === 'measure' ? (v.unitLabel || undefined) : undefined
    };
    this.save.emit(updated);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  onLinkSharedDimension(): void {
    this.linkSharedDimension.emit(this.mapping()?.name ?? '');
  }

  private generatePredicateUri(columnName: string): string {
    const slug = columnName
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '');
    return `https://example.org/property/${slug}`;
  }
}
