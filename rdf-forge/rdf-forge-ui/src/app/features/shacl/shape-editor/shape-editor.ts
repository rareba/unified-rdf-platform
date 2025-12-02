import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDividerModule } from '@angular/material/divider';
import { ShaclService } from '../../../core/services';
import { ShapeCreateRequest, ContentFormat, ValidationResult } from '../../../core/models';

interface PropertyShape {
  id: string;
  path: string;
  name: string;
  description?: string;
  minCount: number | null;
  maxCount: number | null;
  datatype: string;
  kind: 'LITERAL' | 'RESOURCE';
  pattern?: string;
  minLength?: number;
  maxLength?: number;
  message?: string;
  nodeKind?: string;
}

interface ShapeTemplate {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: string;
  targetClass: string;
  properties: Omit<PropertyShape, 'id'>[];
}

// Common constraint presets for non-experts
interface ConstraintPreset {
  id: string;
  label: string;
  description: string;
  icon: string;
  apply: (prop: PropertyShape) => void;
}

const SHAPE_TEMPLATES: ShapeTemplate[] = [
  {
    id: 'person',
    name: 'Person',
    description: 'Validate personal information like name, email, and birthdate',
    icon: 'person',
    category: 'Common',
    targetClass: 'http://schema.org/Person',
    properties: [
      { path: 'http://schema.org/name', name: 'Name', kind: 'LITERAL', datatype: 'xsd:string', minCount: 1, maxCount: 1 },
      { path: 'http://schema.org/email', name: 'Email', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: null, pattern: '^[^@]+@[^@]+\\.[^@]+$' },
      { path: 'http://schema.org/birthDate', name: 'Birth Date', kind: 'LITERAL', datatype: 'xsd:date', minCount: 0, maxCount: 1 }
    ]
  },
  {
    id: 'organization',
    name: 'Organization',
    description: 'Validate organization data with name, website, and contact info',
    icon: 'business',
    category: 'Common',
    targetClass: 'http://schema.org/Organization',
    properties: [
      { path: 'http://schema.org/name', name: 'Organization Name', kind: 'LITERAL', datatype: 'xsd:string', minCount: 1, maxCount: 1 },
      { path: 'http://schema.org/url', name: 'Website', kind: 'LITERAL', datatype: 'xsd:anyURI', minCount: 0, maxCount: 1 },
      { path: 'http://schema.org/email', name: 'Contact Email', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: null }
    ]
  },
  {
    id: 'datacube-observation',
    name: 'Data Cube Observation',
    description: 'Validate RDF Data Cube observations with dimensions and measures',
    icon: 'bar_chart',
    category: 'Data Cube',
    targetClass: 'http://purl.org/linked-data/cube#Observation',
    properties: [
      { path: 'http://purl.org/linked-data/cube#dataSet', name: 'Dataset', kind: 'RESOURCE', datatype: 'http://purl.org/linked-data/cube#DataSet', minCount: 1, maxCount: 1 },
      { path: 'http://purl.org/linked-data/cube#measureType', name: 'Measure Type', kind: 'RESOURCE', datatype: '', minCount: 0, maxCount: 1 }
    ]
  },
  {
    id: 'skos-concept',
    name: 'SKOS Concept',
    description: 'Validate SKOS vocabulary concepts with labels and relationships',
    icon: 'label',
    category: 'Vocabulary',
    targetClass: 'http://www.w3.org/2004/02/skos/core#Concept',
    properties: [
      { path: 'http://www.w3.org/2004/02/skos/core#prefLabel', name: 'Preferred Label', kind: 'LITERAL', datatype: 'xsd:string', minCount: 1, maxCount: null },
      { path: 'http://www.w3.org/2004/02/skos/core#altLabel', name: 'Alternative Label', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: null },
      { path: 'http://www.w3.org/2004/02/skos/core#definition', name: 'Definition', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: 1 },
      { path: 'http://www.w3.org/2004/02/skos/core#inScheme', name: 'In Scheme', kind: 'RESOURCE', datatype: 'http://www.w3.org/2004/02/skos/core#ConceptScheme', minCount: 1, maxCount: 1 }
    ]
  },
  {
    id: 'dublin-core',
    name: 'Dublin Core Metadata',
    description: 'Validate resources with Dublin Core metadata elements',
    icon: 'book',
    category: 'Metadata',
    targetClass: 'http://purl.org/dc/terms/BibliographicResource',
    properties: [
      { path: 'http://purl.org/dc/terms/title', name: 'Title', kind: 'LITERAL', datatype: 'xsd:string', minCount: 1, maxCount: 1 },
      { path: 'http://purl.org/dc/terms/creator', name: 'Creator', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: null },
      { path: 'http://purl.org/dc/terms/date', name: 'Date', kind: 'LITERAL', datatype: 'xsd:date', minCount: 0, maxCount: 1 },
      { path: 'http://purl.org/dc/terms/description', name: 'Description', kind: 'LITERAL', datatype: 'xsd:string', minCount: 0, maxCount: 1 }
    ]
  },
  {
    id: 'empty',
    name: 'Empty Shape',
    description: 'Start from scratch with a blank shape',
    icon: 'insert_drive_file',
    category: 'Basic',
    targetClass: '',
    properties: []
  }
];

const CONSTRAINT_PRESETS: ConstraintPreset[] = [
  {
    id: 'required',
    label: 'Required Field',
    description: 'This field must have at least one value',
    icon: 'error_outline',
    apply: (prop) => { prop.minCount = 1; }
  },
  {
    id: 'single-value',
    label: 'Single Value Only',
    description: 'This field can have at most one value',
    icon: 'check',
    apply: (prop) => { prop.maxCount = 1; }
  },
  {
    id: 'required-single',
    label: 'Required Single Value',
    description: 'Exactly one value is required',
    icon: 'check_circle',
    apply: (prop) => { prop.minCount = 1; prop.maxCount = 1; }
  },
  {
    id: 'optional-multiple',
    label: 'Optional, Multiple Allowed',
    description: 'Zero or more values allowed',
    icon: 'list',
    apply: (prop) => { prop.minCount = 0; prop.maxCount = null; }
  }
];

@Component({
  selector: 'app-shape-editor',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatTooltipModule,
    MatTabsModule,
    MatIconModule,
    MatChipsModule,
    MatButtonToggleModule,
    MatDividerModule
  ],
  templateUrl: './shape-editor.html',
  styleUrl: './shape-editor.scss',
})
export class ShapeEditor implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly shaclService = inject(ShaclService);
  private readonly snackBar = inject(MatSnackBar);

  loading = signal(false);
  saving = signal(false);
  validating = signal(false);
  isNew = signal(true);
  shapeId = signal<string | null>(null);

  // Mode
  visualMode = signal(true);

  // Wizard mode for new shapes
  showTemplateSelector = signal(true);
  wizardStep = signal(1);

  // Templates and presets
  templates = SHAPE_TEMPLATES;
  constraintPresets = CONSTRAINT_PRESETS;
  selectedTemplate = signal<ShapeTemplate | null>(null);

  // Shape Data
  name = signal('');
  uri = signal('');
  targetClass = signal('');
  description = signal('');

  // Visual Editor Properties
  properties = signal<PropertyShape[]>([]);
  selectedProperty = signal<PropertyShape | null>(null);

  // Raw Content (Synced or Manual)
  content = signal('');
  contentFormat = signal<ContentFormat>('turtle');

  // Test Validation Data
  testData = signal('');
  testDataFormat = signal<'turtle' | 'jsonld' | 'ntriples'>('turtle');
  validationResult = signal<ValidationResult | null>(null);

  formatOptions = [
    { label: 'Turtle', value: 'turtle' },
    { label: 'JSON-LD', value: 'jsonld' },
    { label: 'Trig', value: 'trig' }
  ];

  kindOptions = [
    { label: 'Literal', value: 'LITERAL' },
    { label: 'Resource', value: 'RESOURCE' }
  ];

  datatypeOptions = [
    { label: 'String', value: 'xsd:string' },
    { label: 'Integer', value: 'xsd:integer' },
    { label: 'Decimal', value: 'xsd:decimal' },
    { label: 'Date', value: 'xsd:date' },
    { label: 'DateTime', value: 'xsd:dateTime' },
    { label: 'Boolean', value: 'xsd:boolean' },
    { label: 'Any URI', value: 'xsd:anyURI' }
  ];
  
  nodeKindOptions = [
    { label: 'IRI', value: 'sh:IRI' },
    { label: 'Blank Node', value: 'sh:BlankNode' },
    { label: 'Literal', value: 'sh:Literal' },
    { label: 'Blank Node or IRI', value: 'sh:BlankNodeOrIRI' },
    { label: 'Blank Node or Literal', value: 'sh:BlankNodeOrLiteral' },
    { label: 'IRI or Literal', value: 'sh:IRIOrLiteral' }
  ];

  testFormatOptions = [
    { label: 'Turtle', value: 'turtle' },
    { label: 'JSON-LD', value: 'jsonld' },
    { label: 'N-Triples', value: 'ntriples' }
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isNew.set(false);
      this.shapeId.set(id);
      this.showTemplateSelector.set(false);
      this.loadShape(id);
    }
  }

  // Template selection
  selectTemplate(template: ShapeTemplate): void {
    this.selectedTemplate.set(template);
  }

  applyTemplate(): void {
    const template = this.selectedTemplate();
    if (!template) return;

    this.name.set(template.name + ' Shape');
    this.targetClass.set(template.targetClass);
    this.uri.set('');

    // Convert template properties to PropertyShape with IDs
    const props: PropertyShape[] = template.properties.map(p => ({
      ...p,
      id: crypto.randomUUID()
    }));
    this.properties.set(props);

    this.showTemplateSelector.set(false);
    this.wizardStep.set(2);
    this.snackBar.open(`Started with ${template.name} template`, 'Close', {
      duration: 3000
    });
  }

  skipTemplate(): void {
    this.showTemplateSelector.set(false);
    this.wizardStep.set(2);
  }

  // Constraint presets (user-friendly constraint application)
  applyConstraintPreset(preset: ConstraintPreset): void {
    const prop = this.selectedProperty();
    if (!prop) return;

    preset.apply(prop);
    this.properties.update(props => [...props]); // Trigger update
    this.snackBar.open(`Constraint Applied: ${preset.label}`, 'Close', {
      duration: 3000
    });
  }

  // Get human-readable constraint summary
  getConstraintSummary(prop: PropertyShape): string {
    const parts: string[] = [];

    if (prop.minCount && prop.minCount > 0) {
      if (prop.maxCount === 1 && prop.minCount === 1) {
        parts.push('Required (exactly one)');
      } else if (prop.maxCount === null || prop.maxCount === undefined) {
        parts.push(`Required (at least ${prop.minCount})`);
      } else {
        parts.push(`Required (${prop.minCount}-${prop.maxCount})`);
      }
    } else {
      if (prop.maxCount === 1) {
        parts.push('Optional (at most one)');
      } else {
        parts.push('Optional');
      }
    }

    if (prop.kind === 'LITERAL') {
      const typeLabel = this.getDatatypeLabel(prop.datatype);
      parts.push(typeLabel);

      if (prop.pattern) {
        parts.push('Must match pattern');
      }
      if (prop.minLength || prop.maxLength) {
        parts.push(`Length: ${prop.minLength || 0}-${prop.maxLength || 'unlimited'}`);
      }
    } else {
      parts.push('Link to resource');
    }

    return parts.join(' | ');
  }

  getDatatypeLabel(datatype: string): string {
    const labels: Record<string, string> = {
      'xsd:string': 'Text',
      'xsd:integer': 'Whole number',
      'xsd:decimal': 'Decimal number',
      'xsd:date': 'Date',
      'xsd:dateTime': 'Date and time',
      'xsd:boolean': 'Yes/No',
      'xsd:anyURI': 'URL/URI'
    };
    return labels[datatype] || datatype;
  }

  // Get grouped templates by category
  get templatesByCategory(): { category: string; templates: ShapeTemplate[] }[] {
    const grouped = new Map<string, ShapeTemplate[]>();
    for (const template of this.templates) {
      const list = grouped.get(template.category) || [];
      list.push(template);
      grouped.set(template.category, list);
    }
    return Array.from(grouped.entries()).map(([category, templates]) => ({ category, templates }));
  }

  loadShape(id: string): void {
    this.loading.set(true);
    this.shaclService.get(id).subscribe({
      next: (shape) => {
        this.name.set(shape.name);
        this.uri.set(shape.uri);
        this.targetClass.set(shape.targetClass || '');
        this.content.set(shape.content);
        this.contentFormat.set(shape.contentFormat);
        // Note: Parsing raw SHACL back to visual properties is complex.
        // For now, we keep visual mode empty if loaded, or switch to code mode.
        if (shape.content) {
            this.visualMode.set(false);
        }
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load shape', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  addProperty(): void {
    const newProp: PropertyShape = {
        id: crypto.randomUUID(),
        path: '',
        name: 'New Property',
        minCount: 0,
        maxCount: 1,
        datatype: 'xsd:string',
        kind: 'LITERAL'
    };
    this.properties.update(props => [...props, newProp]);
    this.selectProperty(newProp);
  }

  selectProperty(prop: PropertyShape): void {
    this.selectedProperty.set(prop);
  }

  removeProperty(prop: PropertyShape): void {
    this.properties.update(props => props.filter(p => p.id !== prop.id));
    if (this.selectedProperty()?.id === prop.id) {
      this.selectedProperty.set(null);
    }
  }
  
  getIconForKind(kind: string): string {
    return kind === 'LITERAL' ? 'edit' : 'link';
  }

  generateShacl(): void {
    const shapeUri = this.uri() || 'http://example.org/shape';
    const targetClass = this.targetClass() || 'http://example.org/Class';
    
    let ttl = `@prefix sh: <http://www.w3.org/ns/shacl#> .\n`;
    ttl += `@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n`;
    ttl += `@prefix ex: <http://example.org/> .\n\n`;
    
    ttl += `<${shapeUri}>\n`;
    ttl += `  a sh:NodeShape ;\n`;
    ttl += `  sh:targetClass <${targetClass}> ;\n`;
    
    if (this.properties().length > 0) {
        this.properties().forEach(prop => {
            ttl += `  sh:property [\n`;
            ttl += `    sh:path <${prop.path || 'ex:property'}> ;\n`;
            if (prop.name) ttl += `    sh:name "${prop.name}" ;\n`;
            if (prop.description) ttl += `    sh:description "${prop.description}" ;\n`;
            if (prop.minCount !== null) ttl += `    sh:minCount ${prop.minCount} ;\n`;
            if (prop.maxCount !== null) ttl += `    sh:maxCount ${prop.maxCount} ;\n`;
            
            if (prop.kind === 'LITERAL') {
                ttl += `    sh:datatype ${prop.datatype} ;\n`;
                if (prop.minLength) ttl += `    sh:minLength ${prop.minLength} ;\n`;
                if (prop.maxLength) ttl += `    sh:maxLength ${prop.maxLength} ;\n`;
                if (prop.pattern) ttl += `    sh:pattern "${prop.pattern}" ;\n`;
            } else {
                // Resource link
                if (prop.datatype) {
                    // Check if it's a class or just a node kind
                    ttl += `    sh:class <${prop.datatype}> ;\n`;
                }
                ttl += `    sh:nodeKind sh:IRI ;\n`;
            }
            
            if (prop.nodeKind) ttl += `    sh:nodeKind ${prop.nodeKind} ;\n`;
            if (prop.message) ttl += `    sh:message "${prop.message}" ;\n`;
            
            ttl += `  ] ;\n`;
        });
    }
    
    // Remove trailing semicolon and add dot
    ttl = ttl.replace(/ ;\n$/, ' .\n');
    
    this.content.set(ttl);
  }

  save(): void {
    if (this.visualMode()) {
        this.generateShacl();
    }

    const data: ShapeCreateRequest = {
      name: this.name(),
      uri: this.uri(),
      targetClass: this.targetClass(),
      content: this.content(),
      contentFormat: this.contentFormat()
    };

    this.saving.set(true);
    const request = this.isNew()
      ? this.shaclService.create(data)
      : this.shaclService.update(this.shapeId()!, data);

    request.subscribe({
      next: (shape) => {
        this.snackBar.open('Shape saved successfully', 'Close', { duration: 3000 });
        this.saving.set(false);
        if (this.isNew()) {
          this.router.navigate(['/shacl', shape.id], { replaceUrl: true });
        }
      },
      error: () => {
        this.snackBar.open('Failed to save shape', 'Close', { duration: 3000 });
        this.saving.set(false);
      }
    });
  }

  validateSyntax(): void {
    if (this.visualMode()) {
        this.generateShacl();
    }
    this.shaclService.validateSyntax(this.content(), this.contentFormat()).subscribe({
      next: (result) => {
        if (result.valid) {
          this.snackBar.open('Syntax is valid', 'Close', { duration: 3000 });
        } else {
          this.snackBar.open(`Invalid syntax: ${result.errors.join(', ')}`, 'Close', { duration: 5000 });
        }
      },
      error: () => {
        this.snackBar.open('Validation failed', 'Close', { duration: 3000 });
      }
    });
  }

  runValidation(): void {
    if (this.isNew()) {
      this.snackBar.open('Please save the shape before testing validation', 'Close', { duration: 3000 });
      return;
    }

    this.validating.set(true);
    this.validationResult.set(null);

    this.shaclService.runValidation(this.shapeId()!, this.testData(), this.testDataFormat()).subscribe({
      next: (result) => {
        this.validationResult.set(result);
        this.validating.set(false);
        if (result.conforms) {
          this.snackBar.open('Data conforms to shape', 'Close', { duration: 3000 });
        } else {
          this.snackBar.open('Data validation failed', 'Close', { duration: 3000 });
        }
      },
      error: () => {
        this.snackBar.open('Validation run failed', 'Close', { duration: 3000 });
        this.validating.set(false);
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/shacl']);
  }
}
