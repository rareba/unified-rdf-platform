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
import { MessageService } from 'primeng/api';
import { ShaclService } from '../../../core/services';
import { ShapeCreateRequest, ContentFormat, ValidationResult } from '../../../core/models';

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
    ToastModule
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

  // Shape Data
  name = signal('');
  uri = signal('');
  targetClass = signal('');
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
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load shape' });
        this.loading.set(false);
      }
    });
  }

  save(): void {
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
