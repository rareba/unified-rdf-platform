import {
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  inject,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  switchMap,
  takeUntil,
  tap
} from 'rxjs/operators';
import { Subject, Observable, of } from 'rxjs';

import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ShaclService } from '../../../core/services/shacl.service';
import { ErrorHandlerService } from '../../../core/services/error-handler.service';

import {
  ValidationResult,
  ValidationError,
  ShapeProfile,
  ValidationOptions
} from '../../../core/models/shacl.model';
import { ApiResponse } from '../../../core/models/api.model';
import {
  trigger,
  state,
  style,
  transition,
  animate
} from '@angular/animations';
import { ShapeVisualizerComponent } from '../../../shared/components/shape-visualizer/shape-visualizer.component';

// CodeMirror removed - using textarea fallback

interface ShapeFile {
  name: string;
  content: string;
}

@Component({
  selector: 'app-shacl-studio',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    ShapeVisualizerComponent
  ],
  templateUrl: './shacl-studio.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('slideInOut', [
      state('void', style({ opacity: 0, transform: 'translateY(-10px)' })),
      state('*', style({ opacity: 1, transform: 'translateY(0)' })),
      transition('void => *', animate('200ms ease-in')),
      transition('* => void', animate('200ms ease-out'))
    ])
  ]
})
export class ShaclStudioComponent implements OnInit, OnDestroy {
  private shaclService = inject(ShaclService);
  private errorHandler = inject(ErrorHandlerService);
  private formBuilder = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  private destroy$ = new Subject<void>();
  private contentSubject = new Subject<string>();

  @ViewChild('shapeEditor') shapeEditor!: ElementRef<HTMLTextAreaElement>;

  shapeForm!: FormGroup;
  shapeContent = '';
  validationResult: ValidationResult | null = null;
  validationErrors: ValidationError[] = [];
  suggestedFixes: { errorId: string; suggestion: string }[] = [];
  isValidating = false;
  isSaving = false;
  availableProfiles: ShapeProfile[] = [];
  selectedProfile: string | null = null;
  editorOptions: any;
  showVisualization = false;
  visualizationData: any = null;

  readonly severityConfig = {
    violation: { icon: 'fa-exclamation-circle', class: 'alert-danger' },
    warning: { icon: 'fa-exclamation-triangle', class: 'alert-warning' },
    info: { icon: 'fa-info-circle', class: 'alert-info' },
    suggestion: { icon: 'fa-lightbulb', class: 'alert-success' }
  };

  ngOnInit(): void {
    this.setupEditor();
    this.initializeForm();
    this.loadAvailableProfiles();
    this.setupDebouncedValidation();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupEditor(): void {
    this.editorOptions = {
      mode: 'text/turtle',
      theme: 'default',
      lineNumbers: true,
      lineWrapping: true,
      foldGutter: true,
      gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
      matchBrackets: true,
      autoCloseBrackets: true,
      extraKeys: {
        'Ctrl-Space': 'autocomplete',
        'Ctrl-/': 'toggleComment',
        'Cmd-/': 'toggleComment'
      },
      hintOptions: {
        schemaInfo: this.getTurtleSchema()
      }
    };
  }

  private initializeForm(): void {
    this.shapeForm = this.formBuilder.group({
      name: ['', [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(100),
        Validators.pattern(/^[a-zA-Z][a-zA-Z0-9_-]*$/)
      ]],
      description: ['', [Validators.maxLength(500)]],
      profile: [null],
      content: ['', [Validators.required]],
      autoValidate: [true]
    });

    this.shapeForm.get('content')?.valueChanges
      .pipe(
        takeUntil(this.destroy$),
        debounceTime(500),
        distinctUntilChanged()
      )
      .subscribe(content => {
        if (this.shapeForm.get('autoValidate')?.value && content) {
          this.contentSubject.next(content);
        }
      });

    this.shapeForm.get('name')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.clearValidationErrors());
  }

  private loadAvailableProfiles(): void {
    this.shaclService.getProfiles()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error: unknown) => {
          if (error instanceof Error) this.errorHandler.handleError(error);
          return of([]);
        })
      )
      .subscribe((profiles: any) => {
        this.availableProfiles = Array.isArray(profiles) ? profiles : (profiles?.data || []);
      });
  }

  private setupDebouncedValidation(): void {
    this.contentSubject
      .pipe(
        debounceTime(1000),
        distinctUntilChanged(),
        filter(content => content.length > 0),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.validateContent(false);
      });
  }

  validateContent(showSuccess = true): void {
    const content = this.shapeForm.get('content')?.value;
    if (!content || content.trim().length === 0) {
      this.validationErrors = [];
      return;
    }

    this.isValidating = true;
    const options: ValidationOptions = {
      content,
      profile: this.selectedProfile || undefined
    };

    this.shaclService.validateContent(options)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.isValidating = false),
        catchError((error: unknown) => {
          if (error instanceof Error) this.errorHandler.handleError(error);
          return of(null);
        })
      )
      .subscribe((response: any) => {
        if (response) {
          const data = response.data || response;
          this.validationResult = data;
          this.validationErrors = data.results || [];
          this.suggestedFixes = this.generateSuggestedFixes(this.validationErrors);
          this.generateVisualizationData();

          if (this.validationErrors.length === 0 && showSuccess) {
            this.snackBar.open('Shapes are valid!', 'Close', { duration: 3000 });
          }
        }
      });
  }

  private generateSuggestedFixes(errors: ValidationError[]): { errorId: string; suggestion: string }[] {
    return errors.slice(0, 3).map((error, index) => ({
      errorId: `error-${index}`,
      suggestion: this.getSuggestionForError(error)
    }));
  }

  private getSuggestionForError(error: ValidationError): string {
    if (error.message?.includes('sh:path')) {
      return 'Check that the property path is correctly defined using prefix notation.';
    }
    if (error.message?.includes('sh:datatype')) {
      return 'Verify the datatype is valid (e.g., xsd:string, xsd:integer, xsd:date).';
    }
    if (error.message?.includes('sh:minCount')) {
      return 'Ensure minCount value is a non-negative integer.';
    }
    return 'Review the shape syntax and refer to SHACL specification.';
  }

  applyFix(fix: { errorId: string; suggestion: string }): void {
    const content = this.shapeForm.get('content')?.value || '';
    this.shapeForm.get('content')?.setValue(content);
    this.validateContent(false);
  }

  saveShape(): void {
    if (this.shapeForm.invalid) {
      this.markFormGroupTouched(this.shapeForm);
      this.errorHandler.handleError(new Error('Form has validation errors'));
      return;
    }

    this.isSaving = true;
    const shapeData = this.shapeForm.value;

    this.shaclService.saveShape(shapeData)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.isSaving = false),
        catchError((error: unknown) => {
          if (error instanceof Error) this.errorHandler.handleError(error);
          return of(null);
        })
      )
      .subscribe((response: any) => {
        if (response) {
          this.snackBar.open('Shape saved successfully!', 'Close', { duration: 3000 });
          this.shapeForm.reset({ autoValidate: true });
          this.validationErrors = [];
          this.validationResult = null;
        }
      });
  }

  loadExample(): void {
    this.shapeForm.patchValue({
      name: 'example-shape',
      description: 'Example SHACL shape demonstrating common patterns',
      content: this.getExampleShape()
    });
    this.validateContent(false);
  }

  private getExampleShape(): string {
    return `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <http://example.org/> .

ex:PersonShape
  a sh:NodeShape ;
  sh:targetClass ex:Person ;
  sh:property [
    sh:path ex:name ;
    sh:datatype xsd:string ;
    sh:minCount 1 ;
    sh:maxCount 1 ;
  ] ;
  sh:property [
    sh:path ex:email ;
    sh:datatype xsd:string ;
    sh:pattern "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$" ;
    sh:minCount 1 ;
  ] ;
  sh:property [
    sh:path ex:age ;
    sh:datatype xsd:integer ;
    sh:minInclusive 0 ;
    sh:maxInclusive 150 ;
  ] .`;
  }

  private getTurtleSchema(): any {
    const prefixes = {
      'sh': 'http://www.w3.org/ns/shacl#',
      'xsd': 'http://www.w3.org/2001/XMLSchema#',
      'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
      'rdfs': 'http://www.w3.org/2000/01/rdf-schema#'
    };

    const shaclTerms = [
      'NodeShape', 'PropertyShape', 'targetClass', 'targetNode', 'targetSubjectsOf',
      'targetObjectsOf', 'path', 'class', 'datatype', 'nodeKind', 'minCount', 'maxCount',
      'minExclusive', 'minInclusive', 'maxExclusive', 'maxInclusive', 'minLength', 'maxLength',
      'pattern', 'flags', 'languageIn', 'uniqueLang', 'equals', 'disjoint', 'lessThan',
      'lessThanOrEquals', 'not', 'and', 'or', 'xone', 'node', 'property', 'qualifiedValueShape',
      'qualifiedMinCount', 'qualifiedMaxCount', 'qualifiedValueShapesDisjoint', 'closed',
      'ignoredProperties', 'hasValue', 'in', 'name', 'description', 'group', 'order',
      'defaultValue', 'severity', 'message', 'deactivated'
    ];

    return {
      ...prefixes,
      terms: shaclTerms.reduce((acc, term) => {
        acc[`sh:${term}`] = { type: 'property' };
        return acc;
      }, {} as Record<string, any>)
    };
  }

  clearValidationErrors(): void {
    this.validationErrors = [];
    this.validationResult = null;
    this.suggestedFixes = [];
  }

  onContentChange(content: string): void {
    this.shapeForm.get('content')?.setValue(content);
  }

  getShapeContent(): string {
    return this.shapeForm.get('content')?.value || '';
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.values(formGroup.controls).forEach(control => {
      control.markAsTouched();
      control.markAsDirty();
      if ((control as FormGroup).controls) {
        this.markFormGroupTouched(control as FormGroup);
      }
    });
  }

  hasErrors(controlName: string): boolean {
    const control = this.shapeForm.get(controlName);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }

  getErrorMessage(controlName: string): string {
    const control = this.shapeForm.get(controlName);
    if (!control) return '';

    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('minlength')) {
      return `Minimum length is ${control.getError('minlength').requiredLength}.`;
    }
    if (control.hasError('maxlength')) {
      return `Maximum length is ${control.getError('maxlength').requiredLength}.`;
    }
    if (control.hasError('pattern')) {
      return 'Only letters, numbers, hyphens and underscores allowed. Must start with a letter.';
    }

    return 'Invalid value.';
  }

  toggleVisualization(): void {
    this.showVisualization = !this.showVisualization;
    if (this.showVisualization && this.validationResult) {
      this.generateVisualizationData();
    }
  }

  private generateVisualizationData(): void {
    const content = this.shapeForm.get('content')?.value;
    if (!content) return;

    const lines = content.split('\n');
    const nodes: any[] = [];
    const edges: any[] = [];
    let currentShape: any = null;
    let currentProperty: any = null;
    let lineNum = 0;

    for (const line of lines) {
      lineNum++;
      const trimmed = line.trim();

      const shapeMatch = trimmed.match(/^(ex:)?(\w+)\s+a\s+sh:NodeShape/);
      if (shapeMatch) {
        const shapeId = shapeMatch[2];
        currentShape = {
          id: shapeId,
          label: shapeId,
          type: 'shape',
          group: 'shapes'
        };
        nodes.push(currentShape);
        continue;
      }

      const propertyStart = trimmed.match(/sh:property\s*\[/);
      if (propertyStart && currentShape) {
        currentProperty = {
          id: `${currentShape.id}_prop_${nodes.filter(n => n.type === 'property').length}`,
          label: 'Property',
          type: 'property',
          group: 'properties',
          parentShape: currentShape.id
        };
        nodes.push(currentProperty);
        edges.push({
          from: currentShape.id,
          to: currentProperty.id,
          label: 'has property',
          arrows: 'to'
        });
        continue;
      }

      const pathMatch = trimmed.match(/sh:path\s+(ex:)?(\w+)/);
      if (pathMatch && currentProperty) {
        currentProperty.label = pathMatch[2];
      }

      const classMatch = trimmed.match(/sh:class\s+(ex:)?(\w+)/);
      if (classMatch && currentProperty) {
        nodes.push({
          id: `class_${classMatch[2]}`,
          label: classMatch[2],
          type: 'class',
          group: 'classes'
        });
        edges.push({
          from: currentProperty.id,
          to: `class_${classMatch[2]}`,
          label: 'class constraint',
          style: 'dashed'
        });
      }
    }

    this.visualizationData = { nodes, edges };
  }
}

/**
 * SHACL Studio Component
 * 
 * Features:
 * - Turtle syntax highlighting via CodeMirror
 * - Real-time validation with debounce
 * - Visual representation of SHACL shapes
 * - Profile-based validation
 * - Suggested fixes for validation errors
 * - Keyboard shortcuts (Ctrl-Space for autocomplete, Ctrl-/ for comment)
 * - ARIA labels for accessibility
 */
