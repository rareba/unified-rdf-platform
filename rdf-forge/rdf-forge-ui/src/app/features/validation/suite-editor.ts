import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

import {
  ReleaseGate,
  SuiteRule,
  ValidationSuite
} from '../../core/models/validation.model';
import { RuleBuilder } from './rule-builder';

/**
 * Editor form for a single {@link ValidationSuite}. Emits the full
 * editable shape (name/description/rules/gate) back to the parent on
 * every valid change via {@link save}.
 */
@Component({
  selector: 'rdf-suite-editor',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    RuleBuilder
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" class="suite-editor">
      <div class="top">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="255" required />
        </mat-form-field>
        <mat-form-field appearance="outline" class="gate">
          <mat-label>Release gate</mat-label>
          <mat-select formControlName="gate">
            <mat-option value="DISABLED">Disabled</mat-option>
            <mat-option value="WARN_ONLY">Warn only</mat-option>
            <mat-option value="FAIL_ON_WARNING">Fail on warning</mat-option>
            <mat-option value="FAIL_ON_ERROR">Fail on error</mat-option>
            <mat-option value="FAIL_ON_FATAL">Fail on fatal</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Description</mat-label>
        <textarea matInput rows="2" formControlName="description" maxlength="2000"></textarea>
      </mat-form-field>

      <mat-divider></mat-divider>

      <div class="rules-header">
        <h3>Rules</h3>
        <button mat-stroked-button type="button" (click)="addRule()">
          <mat-icon>add</mat-icon>&nbsp;Add rule
        </button>
      </div>

      @if (rules.length === 0) {
        <div class="empty">No rules yet — add SHACL shapes, SPARQL ASK/SELECT queries or cube profiles.</div>
      }

      <div class="rule-list" formArrayName="rules">
        @for (ctrl of rules.controls; track ctrl; let i = $index) {
          <div class="rule-card">
            <rdf-rule-builder
              [rule]="ctrl.value"
              [projectId]="projectId"
              (ruleChange)="updateRule(i, $event)"
              (remove)="removeRule(i)">
            </rdf-rule-builder>
          </div>
        }
      </div>

      <div class="actions">
        <button mat-button type="button" (click)="cancel.emit()">Cancel</button>
        <button mat-raised-button color="primary" type="button"
                [disabled]="form.invalid" (click)="emitSave()">
          Save
        </button>
      </div>
    </form>
  `,
  styles: [`
    .suite-editor { display: flex; flex-direction: column; gap: 12px; }
    .top { display: flex; gap: 12px; align-items: flex-start; }
    .grow { flex: 2; }
    .gate { flex: 1; }
    .full { width: 100%; }
    .rules-header { display: flex; justify-content: space-between; align-items: center; }
    .rule-card { border: 1px solid rgba(0,0,0,0.08); border-radius: 6px; padding: 12px; margin: 8px 0; }
    .empty { padding: 12px; color: rgba(0,0,0,0.6); font-style: italic; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
  `]
})
export class SuiteEditor implements OnChanges {
  private readonly fb = inject(FormBuilder);

  @Input() suite: ValidationSuite | null = null;
  @Input() projectId: string | null = null;
  @Output() readonly save = new EventEmitter<Partial<ValidationSuite>>();
  @Output() readonly cancel = new EventEmitter<void>();

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    gate: ['FAIL_ON_ERROR' as ReleaseGate, Validators.required],
    rules: this.fb.array([])
  });

  get rules(): FormArray { return this.form.get('rules') as FormArray; }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['suite'] && this.suite) {
      this.form.patchValue({
        name: this.suite.name,
        description: this.suite.description ?? '',
        gate: this.suite.gate
      }, { emitEvent: false });
      this.rules.clear({ emitEvent: false });
      for (const r of this.suite.rules ?? []) {
        this.rules.push(this.fb.group({
          id: [r.id],
          name: [r.name],
          type: [r.type],
          resourceRef: [r.resourceRef],
          severity: [r.severity]
        }), { emitEvent: false });
      }
    }
  }

  addRule(): void {
    this.rules.push(this.fb.group({
      id: [''],
      name: ['New rule', Validators.required],
      type: ['SHACL_SHAPE'],
      resourceRef: [''],
      severity: ['ERROR']
    }));
  }

  updateRule(index: number, rule: SuiteRule): void {
    this.rules.at(index).patchValue(rule, { emitEvent: false });
  }

  removeRule(index: number): void {
    this.rules.removeAt(index);
  }

  emitSave(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.save.emit({
      name: value.name,
      description: value.description,
      gate: value.gate,
      rules: (value.rules ?? []) as SuiteRule[]
    });
  }
}
