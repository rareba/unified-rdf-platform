import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { TabsModule } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { CheckboxModule } from 'primeng/checkbox';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { PanelModule } from 'primeng/panel';
import { MessageService } from 'primeng/api';
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

@Component({
  selector: 'app-shape-editor',
  imports: [
    CommonModule,
    FormsModule,
    CardModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    ButtonModule,
    TabsModule,
    TableModule,
    TagModule,
    ToastModule,
    CheckboxModule,
    ToggleButtonModule,
    PanelModule
  ],
  providers: [MessageService],
  templateUrl: './shape-editor.html',
  styleUrl: './shape-editor.scss',
})
export class ShapeEditor implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly shaclService = inject(ShaclService);
  private readonly messageService = inject(MessageService);

  loading = signal(false);
  saving = signal(false);
  validating = signal(false);
  isNew = signal(true);
  shapeId = signal<string | null>(null);

  // Mode
  visualMode = signal(true);

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
      this.loadShape(id);
    }
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
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load shape' });
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
    return kind === 'LITERAL' ? 'pi pi-pencil' : 'pi pi-link';
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
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Shape saved successfully' });
        this.saving.set(false);
        if (this.isNew()) {
          this.router.navigate(['/shacl', shape.id], { replaceUrl: true });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to save shape' });
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
          this.messageService.add({ severity: 'success', summary: 'Valid', detail: 'Syntax is valid' });
        } else {
          this.messageService.add({ severity: 'error', summary: 'Invalid', detail: result.errors.join(', ') });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Validation failed' });
      }
    });
  }

  runValidation(): void {
    if (this.isNew()) {
      this.messageService.add({ severity: 'warn', summary: 'Save Required', detail: 'Please save the shape before testing validation.' });
      return;
    }

    this.validating.set(true);
    this.validationResult.set(null);

    this.shaclService.runValidation(this.shapeId()!, this.testData(), this.testDataFormat()).subscribe({
      next: (result) => {
        this.validationResult.set(result);
        this.validating.set(false);
        if (result.conforms) {
          this.messageService.add({ severity: 'success', summary: 'Conforms', detail: 'Data conforms to shape' });
        } else {
          this.messageService.add({ severity: 'warn', summary: 'Violation', detail: 'Data validation failed' });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Validation run failed' });
        this.validating.set(false);
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/shacl']);
  }
}
