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
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import {
  SuiteRule,
  SuiteRuleType,
  ValidationSeverity
} from '../../core/models/validation.model';
import { ShaclService, ValidationProfile } from '../../core/services/shacl.service';
import { Shape } from '../../core/models';

/**
 * Sub-form for a single {@link SuiteRule}. The parent (suite editor) owns
 * the rule-list form array; this component is purely for editing one row.
 *
 * Rule type decides which secondary input is shown:
 *  - SHACL_SHAPE   → shape picker (UUID)
 *  - SPARQL_ASK    → ASK query textarea
 *  - SPARQL_SELECT → SELECT query textarea
 *  - CUBE_PROFILE  → cube-link profile picker
 */
@Component({
  selector: 'rdf-rule-builder',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" class="rule-builder">
      <div class="row">
        <mat-form-field appearance="outline" class="col-name">
          <mat-label>Rule name</mat-label>
          <input matInput formControlName="name" maxlength="200" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="col-type">
          <mat-label>Type</mat-label>
          <mat-select formControlName="type">
            <mat-option value="SHACL_SHAPE">SHACL shape</mat-option>
            <mat-option value="SPARQL_ASK">SPARQL ASK</mat-option>
            <mat-option value="SPARQL_SELECT">SPARQL SELECT</mat-option>
            <mat-option value="CUBE_PROFILE">Cube profile</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="col-sev">
          <mat-label>Severity</mat-label>
          <mat-select formControlName="severity">
            <mat-option value="INFO">Info</mat-option>
            <mat-option value="WARNING">Warning</mat-option>
            <mat-option value="ERROR">Error</mat-option>
            <mat-option value="FATAL">Fatal</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-icon-button type="button" aria-label="Remove rule"
                (click)="remove.emit()">
          <mat-icon>delete</mat-icon>
        </button>
      </div>

      @switch (form.get('type')?.value) {
        @case ('SHACL_SHAPE') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Shape</mat-label>
            <mat-select formControlName="resourceRef">
              @for (s of shapes(); track s.id) {
                <mat-option [value]="s.id">{{ s.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        @case ('CUBE_PROFILE') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Profile</mat-label>
            <mat-select formControlName="resourceRef">
              @for (p of profiles(); track p.id) {
                <mat-option [value]="p.id">{{ p.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        @default {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Query</mat-label>
            <textarea matInput rows="6" formControlName="resourceRef"
              placeholder="PREFIX ex: <http://example.org/> ASK { ?s a ex:Thing }"></textarea>
          </mat-form-field>
        }
      }
    </form>
  `,
  styles: [`
    .rule-builder { display: flex; flex-direction: column; gap: 8px; }
    .row { display: flex; gap: 8px; align-items: center; }
    .col-name { flex: 2; }
    .col-type { flex: 1; }
    .col-sev  { flex: 1; }
    .full     { width: 100%; }
  `]
})
export class RuleBuilder implements OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly shaclService = inject(ShaclService);

  @Input() rule: SuiteRule | null = null;
  @Input() projectId: string | null = null;

  @Output() readonly ruleChange = new EventEmitter<SuiteRule>();
  @Output() readonly remove = new EventEmitter<void>();

  readonly shapes = signal<Shape[]>([]);
  readonly profiles = signal<ValidationProfile[]>([]);

  readonly form: FormGroup = this.fb.group({
    id: [''],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    type: ['SHACL_SHAPE' as SuiteRuleType, Validators.required],
    resourceRef: ['', Validators.required],
    severity: ['ERROR' as ValidationSeverity, Validators.required]
  });

  constructor() {
    // Eagerly fetch shapes + profiles — cheap, small payloads.
    this.loadShapes().subscribe(s => this.shapes.set(s));
    this.loadProfiles().subscribe(p => this.profiles.set(p));

    this.form.valueChanges.subscribe(v => {
      if (this.form.valid) {
        this.ruleChange.emit(v as SuiteRule);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['rule'] && this.rule) {
      this.form.patchValue(this.rule, { emitEvent: false });
    }
    if (changes['projectId']) {
      this.loadShapes().subscribe(s => this.shapes.set(s));
    }
  }

  private loadShapes(): Observable<Shape[]> {
    const params = this.projectId
      ? { projectId: this.projectId }
      : undefined;
    return this.shaclService.list(params).pipe(
      map(s => s ?? []),
      catchError(() => of([] as Shape[]))
    );
  }

  private loadProfiles(): Observable<ValidationProfile[]> {
    return this.shaclService.getProfiles().pipe(
      map(p => p ?? []),
      catchError(() => of([] as ValidationProfile[]))
    );
  }
}
