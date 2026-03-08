import { Component, input, output, signal, computed, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';

/**
 * Component interface for cube component (dimension, measure, or attribute)
 */
export interface CubeComponent {
  name: string;
  uri?: string;
  description?: string;
  dataType?: string;
  conceptUri?: string;
  order?: number;
  // Dimension-specific
  keyDimension?: boolean;
  codeListUri?: string;
  // Measure-specific
  unitUri?: string;
  unitLabel?: string;
  minValue?: number;
  maxValue?: number;
}

/**
 * Complete cube definition structure
 */
export interface CubeDefinition {
  name: string;
  description?: string;
  baseUri: string;
  cubeUri?: string;
  dimensions: CubeComponent[];
  measures: CubeComponent[];
  attributes: CubeComponent[];
  metadata?: {
    title?: string;
    description?: string;
    publisher?: string;
    publisherUri?: string;
    license?: string;
    issued?: string;
    modified?: string;
    keywords?: string[];
    language?: string;
  };
  // Column mappings for data transformation
  columnMappings?: ColumnMapping[];
}

/**
 * Column mapping structure linking source data to cube components
 */
export interface ColumnMapping {
  name: string;
  sourceType: string;
  role: 'dimension' | 'measure' | 'attribute' | 'ignore';
  dataType: string;
  predicateUri?: string;
  componentName?: string;
  required: boolean;
}

@Component({
  selector: 'app-cube-definition-editor',
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatIconModule,
    MatDividerModule,
    MatChipsModule,
    MatTooltipModule,
    MatDialogModule,
    MatTabsModule,
    MatExpansionModule
  ],
  templateUrl: './cube-definition-editor.html',
  styleUrl: './cube-definition-editor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CubeDefinitionEditor implements OnInit {
  // Input signal for initial cube definition
  initialDefinition = input<CubeDefinition | null>(null);

  // Output signal for when definition changes
  definitionChange = output<CubeDefinition>();

  // SnackBar for notifications
  private readonly snackBar = input<MatSnackBar>();

  // Active tab index
  activeTab = signal(0);

  // Basic cube info
  cubeName = signal('');
  cubeDescription = signal('');
  baseUri = signal('https://example.org/cube/');

  // Generated cube URI
  cubeUri = computed(() => {
    const base = this.baseUri();
    const name = this.cubeName();
    if (!name) return '';
    const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    return (base.endsWith('/') ? base : base + '/') + slug;
  });

  // Components
  dimensions = signal<CubeComponent[]>([]);
  measures = signal<CubeComponent[]>([]);
  attributes = signal<CubeComponent[]>([]);

  // Column mappings
  columnMappings = signal<ColumnMapping[]>([]);
  availableColumns = input<string[]>([]);

  // Metadata
  metadataTitle = signal('');
  metadataDescription = signal('');
  metadataPublisher = signal('');
  metadataLicense = signal('');

  // Edit state
  editingDimension = signal<CubeComponent | null>(null);
  editingMeasure = signal<CubeComponent | null>(null);
  editingAttribute = signal<CubeComponent | null>(null);
  editingColumnMapping = signal<ColumnMapping | null>(null);

  // Data type options
  dataTypeOptions = [
    { value: 'http://www.w3.org/2001/XMLSchema#string', label: 'String' },
    { value: 'http://www.w3.org/2001/XMLSchema#integer', label: 'Integer' },
    { value: 'http://www.w3.org/2001/XMLSchema#decimal', label: 'Decimal' },
    { value: 'http://www.w3.org/2001/XMLSchema#double', label: 'Double' },
    { value: 'http://www.w3.org/2001/XMLSchema#float', label: 'Float' },
    { value: 'http://www.w3.org/2001/XMLSchema#boolean', label: 'Boolean' },
    { value: 'http://www.w3.org/2001/XMLSchema#date', label: 'Date' },
    { value: 'http://www.w3.org/2001/XMLSchema#dateTime', label: 'DateTime' },
    { value: 'http://www.w3.org/2001/XMLSchema#gYear', label: 'Year' },
    { value: 'http://www.w3.org/2001/XMLSchema#anyURI', label: 'URI' }
  ];

  // License options
  licenseOptions = [
    { value: 'https://creativecommons.org/publicdomain/zero/1.0/', label: 'CC0 1.0' },
    { value: 'https://creativecommons.org/licenses/by/4.0/', label: 'CC BY 4.0' },
    { value: 'https://creativecommons.org/licenses/by-sa/4.0/', label: 'CC BY-SA 4.0' },
    { value: 'https://opensource.org/licenses/MIT', label: 'MIT License' },
    { value: 'https://www.apache.org/licenses/LICENSE-2.0', label: 'Apache 2.0' }
  ];

  // Computed full definition
  fullDefinition = computed<CubeDefinition>(() => ({
    name: this.cubeName(),
    description: this.cubeDescription(),
    baseUri: this.baseUri(),
    cubeUri: this.cubeUri(),
    dimensions: this.dimensions(),
    measures: this.measures(),
    attributes: this.attributes(),
    columnMappings: this.columnMappings(),
    metadata: {
      title: this.metadataTitle(),
      description: this.metadataDescription(),
      publisher: this.metadataPublisher(),
      license: this.metadataLicense()
    }
  }));

  ngOnInit(): void {
    const initial = this.initialDefinition();
    if (initial) {
      this.loadDefinition(initial);
    }
  }

  loadDefinition(def: CubeDefinition): void {
    this.cubeName.set(def.name || '');
    this.cubeDescription.set(def.description || '');
    this.baseUri.set(def.baseUri || 'https://example.org/cube/');
    this.dimensions.set(def.dimensions || []);
    this.measures.set(def.measures || []);
    this.attributes.set(def.attributes || []);
    this.columnMappings.set(def.columnMappings || []);

    if (def.metadata) {
      this.metadataTitle.set(def.metadata.title || '');
      this.metadataDescription.set(def.metadata.description || '');
      this.metadataPublisher.set(def.metadata.publisher || '');
      this.metadataLicense.set(def.metadata.license || '');
    }
  }

  // ===== Dimension Management =====

  addDimension(): void {
    const newDim: CubeComponent = {
      name: `Dimension ${this.dimensions().length + 1}`,
      keyDimension: true,
      dataType: 'http://www.w3.org/2001/XMLSchema#string'
    };
    this.dimensions.update(dims => [...dims, newDim]);
    this.editingDimension.set(newDim);
    this.emitChange();
  }

  removeDimension(index: number): void {
    this.dimensions.update(dims => dims.filter((_, i) => i !== index));
    this.editingDimension.set(null);
    this.emitChange();
  }

  updateDimension(index: number, updates: Partial<CubeComponent>): void {
    this.dimensions.update(dims =>
      dims.map((d, i) => i === index ? { ...d, ...updates } : d)
    );
    this.emitChange();
  }

  startEditDimension(dim: CubeComponent): void {
    this.editingDimension.set(dim);
  }

  // ===== Measure Management =====

  addMeasure(): void {
    const newMeas: CubeComponent = {
      name: `Measure ${this.measures().length + 1}`,
      dataType: 'http://www.w3.org/2001/XMLSchema#decimal'
    };
    this.measures.update(meas => [...meas, newMeas]);
    this.editingMeasure.set(newMeas);
    this.emitChange();
  }

  removeMeasure(index: number): void {
    this.measures.update(meas => meas.filter((_, i) => i !== index));
    this.editingMeasure.set(null);
    this.emitChange();
  }

  updateMeasure(index: number, updates: Partial<CubeComponent>): void {
    this.measures.update(meas =>
      meas.map((m, i) => i === index ? { ...m, ...updates } : m)
    );
    this.emitChange();
  }

  startEditMeasure(meas: CubeComponent): void {
    this.editingMeasure.set(meas);
  }

  // ===== Attribute Management =====

  addAttribute(): void {
    const newAttr: CubeComponent = {
      name: `Attribute ${this.attributes().length + 1}`,
      dataType: 'http://www.w3.org/2001/XMLSchema#string'
    };
    this.attributes.update(attrs => [...attrs, newAttr]);
    this.editingAttribute.set(newAttr);
    this.emitChange();
  }

  removeAttribute(index: number): void {
    this.attributes.update(attrs => attrs.filter((_, i) => i !== index));
    this.editingAttribute.set(null);
    this.emitChange();
  }

  updateAttribute(index: number, updates: Partial<CubeComponent>): void {
    this.attributes.update(attrs =>
      attrs.map((a, i) => i === index ? { ...a, ...updates } : a)
    );
    this.emitChange();
  }

  startEditAttribute(attr: CubeComponent): void {
    this.editingAttribute.set(attr);
  }

  // ===== Column Mapping Management =====

  addColumnMapping(columnName: string): void {
    const existing = this.columnMappings().find(m => m.name === columnName);
    if (existing) return;

    const newMapping: ColumnMapping = {
      name: columnName,
      sourceType: 'string',
      role: 'ignore',
      dataType: 'http://www.w3.org/2001/XMLSchema#string',
      required: true
    };
    this.columnMappings.update(maps => [...maps, newMapping]);
    this.emitChange();
  }

  removeColumnMapping(index: number): void {
    this.columnMappings.update(maps => maps.filter((_, i) => i !== index));
    this.emitChange();
  }

  updateColumnMapping(index: number, updates: Partial<ColumnMapping>): void {
    this.columnMappings.update(maps =>
      maps.map((m, i) => i === index ? { ...m, ...updates } : m)
    );
    this.emitChange();
  }

  autoMapColumns(): void {
    const dims = this.dimensions();
    const meas = this.measures();
    const columns = this.availableColumns();

    // Match columns to components by name similarity
    this.columnMappings.update(maps => {
      return maps.map(mapping => {
        const colName = mapping.name.toLowerCase();

        // Check for dimension match
        const matchingDim = dims.find(d =>
          d.name.toLowerCase() === colName ||
          d.name.toLowerCase().includes(colName) ||
          colName.includes(d.name.toLowerCase())
        );

        if (matchingDim) {
          return {
            ...mapping,
            role: 'dimension' as const,
            dataType: matchingDim.dataType || mapping.dataType,
            predicateUri: matchingDim.uri,
            componentName: matchingDim.name
          };
        }

        // Check for measure match
        const matchingMeas = meas.find(m =>
          m.name.toLowerCase() === colName ||
          m.name.toLowerCase().includes(colName) ||
          colName.includes(m.name.toLowerCase())
        );

        if (matchingMeas) {
          return {
            ...mapping,
            role: 'measure' as const,
            dataType: matchingMeas.dataType || mapping.dataType,
            predicateUri: matchingMeas.uri,
            componentName: matchingMeas.name
          };
        }

        return mapping;
      });
    });

    this.emitChange();
  }

  syncMappingsToComponents(): void {
    // Convert mappings to dimensions/measures
    const mappings = this.columnMappings().filter(m => m.role !== 'ignore');

    const newDims: CubeComponent[] = [];
    const newMeas: CubeComponent[] = [];

    mappings.forEach(m => {
      if (m.role === 'dimension') {
        newDims.push({
          name: m.componentName || m.name,
          uri: m.predicateUri,
          dataType: m.dataType,
          keyDimension: true
        });
      } else if (m.role === 'measure') {
        newMeas.push({
          name: m.componentName || m.name,
          uri: m.predicateUri,
          dataType: m.dataType
        });
      }
    });

    this.dimensions.set(newDims);
    this.measures.set(newMeas);
    this.emitChange();
  }

  // ===== Export =====

  getDefinition(): CubeDefinition {
    return this.fullDefinition();
  }

  getDefinitionJson(): string {
    return JSON.stringify(this.fullDefinition(), null, 2);
  }

  emitChange(): void {
    this.definitionChange.emit(this.fullDefinition());
  }

  // ===== Validation =====

  isValid(): boolean {
    return !!this.cubeName() &&
           this.dimensions().length > 0 &&
           this.measures().length > 0;
  }

  getValidationErrors(): string[] {
    const errors: string[] = [];
    if (!this.cubeName()) errors.push('Cube name is required');
    if (!this.baseUri()) errors.push('Base URI is required');
    if (this.dimensions().length === 0) errors.push('At least one dimension is required');
    if (this.measures().length === 0) errors.push('At least one measure is required');
    return errors;
  }

  // ===== Form Helpers =====

  updateCubeName(value: string): void {
    this.cubeName.set(value);
    this.emitChange();
  }

  updateCubeDescription(value: string): void {
    this.cubeDescription.set(value);
    this.emitChange();
  }

  updateBaseUri(value: string): void {
    this.baseUri.set(value);
    this.emitChange();
  }

  updateMetadataTitle(value: string): void {
    this.metadataTitle.set(value);
    this.emitChange();
  }

  updateMetadataDescription(value: string): void {
    this.metadataDescription.set(value);
    this.emitChange();
  }

  updateMetadataPublisher(value: string): void {
    this.metadataPublisher.set(value);
    this.emitChange();
  }

  updateMetadataLicense(value: string): void {
    this.metadataLicense.set(value);
    this.emitChange();
  }
}
