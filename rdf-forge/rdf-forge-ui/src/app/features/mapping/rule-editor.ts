import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import {
  MappingRule,
  RuleType,
  TransformType
} from '../../core/models/mapping.model';

interface RuleEditorData {
  rule: MappingRule;
  availableColumns: string[];
  targetPredicates: string[];
}

/**
 * Dialog editor for a single {@link MappingRule}. Kept simple: form inputs
 * with autocomplete against known source columns and target predicates. The
 * transform group is optional and collapsed by default.
 */
@Component({
  selector: 'app-rule-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatAutocompleteModule,
    MatIconModule,
    MatExpansionModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.rule.id ? 'Edit' : 'New' }} Rule</h2>
    <mat-dialog-content>
      <form class="form">
        <mat-form-field appearance="outline">
          <mat-label>Rule id</mat-label>
          <input matInput [(ngModel)]="rule.id" name="id" required
                 placeholder="e.g. subject-uri">
          <mat-hint>Unique id inside this mapping (used in Explain).</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="rule.type" name="type" required>
            <mat-option value="FIXED_URI">FIXED_URI — set subject from template</mat-option>
            <mat-option value="COLUMN_TO_URI">COLUMN_TO_URI — map column to URI</mat-option>
            <mat-option value="COLUMN_TO_LITERAL">COLUMN_TO_LITERAL — map column to literal</mat-option>
            <mat-option value="CONSTANT">CONSTANT — fixed literal value</mat-option>
            <mat-option value="NESTED">NESTED — step into a related resource</mat-option>
          </mat-select>
        </mat-form-field>

        @if (needsSource()) {
          <mat-form-field appearance="outline">
            <mat-label>{{ rule.type === 'CONSTANT' ? 'Constant value' : 'Source column' }}</mat-label>
            <input matInput [(ngModel)]="rule.source" name="source"
                   [matAutocomplete]="auto" [required]="rule.type !== 'FIXED_URI'">
            <mat-autocomplete #auto="matAutocomplete">
              @for (col of data.availableColumns; track col) {
                <mat-option [value]="col">{{ col }}</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>
        }

        @if (needsTarget()) {
          <mat-form-field appearance="outline">
            <mat-label>Target predicate (URI)</mat-label>
            <input matInput [(ngModel)]="rule.target" name="target"
                   [matAutocomplete]="autoPred"
                   [required]="requiresTarget()">
            <mat-autocomplete #autoPred="matAutocomplete">
              @for (p of data.targetPredicates; track p) {
                <mat-option [value]="p">{{ p }}</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>
        }

        @if (needsTemplate()) {
          <mat-form-field appearance="outline">
            <mat-label>URI template</mat-label>
            <input matInput [(ngModel)]="rule.uriTemplate" name="uriTemplate"
                   [placeholder]="uriTemplatePlaceholder">
            <mat-hint>{{ uriTemplateHint }}</mat-hint>
          </mat-form-field>
        }

        @if (rule.type === 'COLUMN_TO_LITERAL') {
          <div class="row">
            <mat-form-field appearance="outline" class="half">
              <mat-label>Datatype (optional)</mat-label>
              <input matInput [(ngModel)]="rule.datatype" name="datatype"
                     placeholder="xsd:string | xsd:dateTime">
            </mat-form-field>
            <mat-form-field appearance="outline" class="half">
              <mat-label>Language tag (optional)</mat-label>
              <input matInput [(ngModel)]="rule.language" name="language"
                     placeholder="en">
            </mat-form-field>
          </div>
        }

        <mat-expansion-panel class="transform-panel">
          <mat-expansion-panel-header>
            <mat-panel-title>Transform (optional)</mat-panel-title>
          </mat-expansion-panel-header>

          <mat-form-field appearance="outline">
            <mat-label>Transform type</mat-label>
            <mat-select [(ngModel)]="transformType" name="transformType"
                        (selectionChange)="onTransformTypeChange()">
              <mat-option [value]="null">None</mat-option>
              <mat-option value="UPPER">UPPER</mat-option>
              <mat-option value="LOWER">LOWER</mat-option>
              <mat-option value="TRIM">TRIM</mat-option>
              <mat-option value="SUBSTRING">SUBSTRING</mat-option>
              <mat-option value="REGEX_REPLACE">REGEX_REPLACE</mat-option>
            </mat-select>
          </mat-form-field>

          @if (transformType === 'SUBSTRING') {
            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>start</mat-label>
                <input matInput type="number" [(ngModel)]="substringStart" name="substringStart">
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>end (optional)</mat-label>
                <input matInput type="number" [(ngModel)]="substringEnd" name="substringEnd">
              </mat-form-field>
            </div>
          }
          @if (transformType === 'REGEX_REPLACE') {
            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>pattern</mat-label>
                <input matInput [(ngModel)]="regexPattern" name="regexPattern">
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>replacement</mat-label>
                <input matInput [(ngModel)]="regexReplacement" name="regexReplacement">
              </mat-form-field>
            </div>
          }
        </mat-expansion-panel>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-raised-button color="primary" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form { display: flex; flex-direction: column; gap: 12px; min-width: 520px; }
    .row { display: flex; gap: 12px; }
    .half { flex: 1; }
    mat-form-field { width: 100%; }
    .transform-panel { margin-top: 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RuleEditor {
  readonly data = inject<RuleEditorData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RuleEditor>);

  rule: MappingRule;
  // Literal $ signs in backtick-template strings are JS interpolation markers;
  // exposing the placeholder/hint as typed fields sidesteps the Angular parser
  // NG5002 complaint that fires on raw `${...}` inside an inline template.
  readonly uriTemplatePlaceholder = '${baseUri}person/${id}';
  readonly uriTemplateHint = 'Use ${baseUri}, ${row.col} or bare ${col}.';
  transformType: TransformType | null = null;
  substringStart: number | null = null;
  substringEnd: number | null = null;
  regexPattern = '';
  regexReplacement = '';

  constructor() {
    // Deep clone so cancel discards edits cleanly.
    this.rule = { ...this.data.rule };
    if (this.rule.transform) {
      this.transformType = this.rule.transform.type;
      const params = this.rule.transform.params ?? {};
      this.substringStart = (params['start'] as number) ?? null;
      this.substringEnd = (params['end'] as number) ?? null;
      this.regexPattern = (params['pattern'] as string) ?? '';
      this.regexReplacement = (params['replacement'] as string) ?? '';
    }
  }

  onTransformTypeChange(): void {
    if (!this.transformType) {
      this.rule.transform = null;
    }
  }

  needsSource(): boolean {
    return this.rule.type === 'COLUMN_TO_URI'
      || this.rule.type === 'COLUMN_TO_LITERAL'
      || this.rule.type === 'CONSTANT';
  }
  needsTarget(): boolean {
    return this.rule.type !== 'FIXED_URI';
  }
  requiresTarget(): boolean {
    return this.rule.type === 'COLUMN_TO_LITERAL'
      || this.rule.type === 'CONSTANT';
  }
  needsTemplate(): boolean {
    return this.rule.type === 'FIXED_URI'
      || this.rule.type === 'COLUMN_TO_URI'
      || this.rule.type === 'NESTED';
  }

  save(): void {
    if (!this.rule.id || !this.rule.type) return;
    if (this.transformType) {
      const params: Record<string, unknown> = {};
      if (this.transformType === 'SUBSTRING') {
        if (this.substringStart !== null) params['start'] = this.substringStart;
        if (this.substringEnd !== null) params['end'] = this.substringEnd;
      } else if (this.transformType === 'REGEX_REPLACE') {
        params['pattern'] = this.regexPattern;
        params['replacement'] = this.regexReplacement;
      }
      this.rule.transform = { type: this.transformType, params };
    } else {
      this.rule.transform = null;
    }
    this.ref.close(this.rule);
  }

  cancel(): void { this.ref.close(); }
}
