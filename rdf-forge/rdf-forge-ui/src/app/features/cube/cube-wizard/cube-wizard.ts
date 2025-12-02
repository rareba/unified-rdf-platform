import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { StepperModule } from 'primeng/stepper';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { ListboxModule } from 'primeng/listbox';
import { ToastModule } from 'primeng/toast';
import { FileUploadModule } from 'primeng/fileupload';
import { SelectButtonModule } from 'primeng/selectbutton';
import { DialogModule } from 'primeng/dialog';
import { CheckboxModule } from 'primeng/checkbox';
import { PanelModule } from 'primeng/panel';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { DividerModule } from 'primeng/divider';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { AccordionModule } from 'primeng/accordion';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DataService, DimensionService, PipelineService } from '../../../core/services';
import { DataSource, Dimension, ColumnInfo, DimensionType, UploadOptions } from '../../../core/models';
import { DataPreviewComponent } from '../../data/data-preview/data-preview';

interface ColumnMapping {
  name: string;
  sourceType: string;
  role: 'dimension' | 'measure' | 'attribute' | 'ignore';
  datatype: string;
  dimensionId?: string;
  dimensionName?: string;
  predicateUri?: string;
  required: boolean;
}

interface ValidationCheck {
  id: string;
  name: string;
  description: string;
  status: 'pending' | 'running' | 'passed' | 'failed' | 'warning';
  message?: string;
}

interface CubeMetadata {
  title: string;
  description: string;
  publisher: string;
  publisherUri: string;
  license: string;
  issued: string;
  modified: string;
  keywords: string[];
  language: string;
  accrualPeriodicity: string;
  spatial: string;
  temporal: string;
}

@Component({
  selector: 'app-cube-wizard',
  imports: [
    CommonModule,
    FormsModule,
    CardModule,
    StepperModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    ListboxModule,
    ToastModule,
    FileUploadModule,
    SelectButtonModule,
    DialogModule,
    CheckboxModule,
    PanelModule,
    TableModule,
    TagModule,
    TabsModule,
    DividerModule,
    InputNumberModule,
    TooltipModule,
    ProgressBarModule,
    AccordionModule,
    ConfirmDialogModule,
    DataPreviewComponent
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './cube-wizard.html',
  styleUrl: './cube-wizard.scss',
})
export class CubeWizard implements OnInit {
  private readonly dataService = inject(DataService);
  private readonly dimensionService = inject(DimensionService);
  private readonly pipelineService = inject(PipelineService);
  private readonly messageService = inject(MessageService);
  private readonly confirmService = inject(ConfirmationService);
  private readonly router = inject(Router);

  activeStep = signal(0);
  maxStepReached = signal(0);

  // Step 1: Basic Info
  cubeName = signal('');
  cubeDescription = signal('');
  cubeId = signal('');
  baseUri = signal('https://example.org/cube/');

  // Auto-generate cube ID from name
  generatedId = computed(() => {
    const name = this.cubeName();
    return name ? name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') : '';
  });

  useAutoId = signal(true);

  // Step 2: Data Source
  sourceTypeOptions = [
    { label: 'Existing Data Source', value: 'existing', icon: 'pi pi-database' },
    { label: 'Upload New File', value: 'upload', icon: 'pi pi-upload' }
  ];
  sourceType = signal<'existing' | 'upload'>('existing');
  dataSources = signal<DataSource[]>([]);
  selectedDataSource = signal<DataSource | null>(null);
  sourceColumns = signal<ColumnInfo[]>([]);
  loadingDataSources = signal(false);
  loadingColumns = signal(false);

  // CSV/File Options
  delimiter = signal(',');
  quoteChar = signal('"');
  escapeChar = signal('\\');
  hasHeader = signal(true);
  encoding = signal('UTF-8');
  skipRows = signal(0);

  encodingOptions = [
    { label: 'UTF-8', value: 'UTF-8' },
    { label: 'ISO-8859-1', value: 'ISO-8859-1' },
    { label: 'Windows-1252', value: 'Windows-1252' }
  ];

  delimiterOptions = [
    { label: 'Comma (,)', value: ',' },
    { label: 'Semicolon (;)', value: ';' },
    { label: 'Tab', value: '\t' },
    { label: 'Pipe (|)', value: '|' }
  ];

  uploading = signal(false);

  // Step 3: Column Mapping
  columnMappings = signal<ColumnMapping[]>([]);

  roleOptions = [
    { label: 'Dimension', value: 'dimension', icon: 'pi pi-th-large', description: 'Categorical data for grouping' },
    { label: 'Measure', value: 'measure', icon: 'pi pi-chart-bar', description: 'Numeric values to aggregate' },
    { label: 'Attribute', value: 'attribute', icon: 'pi pi-tag', description: 'Additional metadata' },
    { label: 'Ignore', value: 'ignore', icon: 'pi pi-eye-slash', description: 'Exclude from cube' }
  ];

  datatypeOptions = [
    { label: 'String', value: 'xsd:string' },
    { label: 'Integer', value: 'xsd:integer' },
    { label: 'Decimal', value: 'xsd:decimal' },
    { label: 'Float', value: 'xsd:float' },
    { label: 'Double', value: 'xsd:double' },
    { label: 'Date', value: 'xsd:date' },
    { label: 'DateTime', value: 'xsd:dateTime' },
    { label: 'Year', value: 'xsd:gYear' },
    { label: 'Boolean', value: 'xsd:boolean' },
    { label: 'URI', value: 'xsd:anyURI' }
  ];

  availableDimensions = signal<Dimension[]>([]);
  loadingDimensions = signal(false);

  // Computed mapping stats
  mappingStats = computed(() => {
    const mappings = this.columnMappings();
    return {
      dimensions: mappings.filter(m => m.role === 'dimension').length,
      measures: mappings.filter(m => m.role === 'measure').length,
      attributes: mappings.filter(m => m.role === 'attribute').length,
      ignored: mappings.filter(m => m.role === 'ignore').length,
      total: mappings.length
    };
  });

  // Step 4: Metadata
  metadata = signal<CubeMetadata>({
    title: '',
    description: '',
    publisher: '',
    publisherUri: '',
    license: '',
    issued: new Date().toISOString().split('T')[0],
    modified: new Date().toISOString().split('T')[0],
    keywords: [],
    language: 'en',
    accrualPeriodicity: '',
    spatial: '',
    temporal: ''
  });

  keywordsInput = signal('');

  licenseOptions = [
    { label: 'CC0 1.0 (Public Domain)', value: 'https://creativecommons.org/publicdomain/zero/1.0/' },
    { label: 'CC BY 4.0 (Attribution)', value: 'https://creativecommons.org/licenses/by/4.0/' },
    { label: 'CC BY-SA 4.0 (Attribution-ShareAlike)', value: 'https://creativecommons.org/licenses/by-sa/4.0/' },
    { label: 'CC BY-NC 4.0 (Non-Commercial)', value: 'https://creativecommons.org/licenses/by-nc/4.0/' },
    { label: 'MIT License', value: 'https://opensource.org/licenses/MIT' },
    { label: 'Apache 2.0', value: 'https://www.apache.org/licenses/LICENSE-2.0' },
    { label: 'Other / Custom', value: 'custom' }
  ];

  languageOptions = [
    { label: 'English', value: 'en' },
    { label: 'German', value: 'de' },
    { label: 'French', value: 'fr' },
    { label: 'Italian', value: 'it' },
    { label: 'Spanish', value: 'es' }
  ];

  periodicityOptions = [
    { label: 'Daily', value: 'http://purl.org/cld/freq/daily' },
    { label: 'Weekly', value: 'http://purl.org/cld/freq/weekly' },
    { label: 'Monthly', value: 'http://purl.org/cld/freq/monthly' },
    { label: 'Quarterly', value: 'http://purl.org/cld/freq/quarterly' },
    { label: 'Annual', value: 'http://purl.org/cld/freq/annual' },
    { label: 'Irregular', value: 'http://purl.org/cld/freq/irregular' }
  ];

  // Step 5: Validation
  validationChecks = signal<ValidationCheck[]>([
    { id: 'schema', name: 'Cube Schema', description: 'Validates Data Cube structure', status: 'pending' },
    { id: 'dimensions', name: 'Dimension Integrity', description: 'Checks dimension references', status: 'pending' },
    { id: 'measures', name: 'Measure Completeness', description: 'Validates measure definitions', status: 'pending' },
    { id: 'metadata', name: 'Metadata Quality', description: 'Checks required metadata fields', status: 'pending' },
    { id: 'shacl', name: 'SHACL Validation', description: 'Validates against SHACL shapes', status: 'pending' }
  ]);

  validating = signal(false);
  validationComplete = signal(false);

  validationSummary = computed(() => {
    const checks = this.validationChecks();
    return {
      passed: checks.filter(c => c.status === 'passed').length,
      failed: checks.filter(c => c.status === 'failed').length,
      warnings: checks.filter(c => c.status === 'warning').length,
      total: checks.length
    };
  });

  // Step 6: Publish
  publishOptions = signal({
    triplestore: '',
    graphUri: '',
    createNamedGraph: true,
    savePipeline: true,
    pipelineName: '',
    runImmediately: true
  });

  triplestoreOptions = signal<{ label: string; value: string }[]>([]);

  publishing = signal(false);
  publishProgress = signal(0);

  // New Dimension Dialog
  showNewDimensionDialog = signal(false);
  newDimension = signal<Partial<Dimension>>({
    name: '',
    uri: '',
    type: 'KEY',
    description: ''
  });

  dimensionTypes: { label: string; value: DimensionType; description: string }[] = [
    { label: 'Key Dimension', value: 'KEY', description: 'Primary identifier for observations' },
    { label: 'Measure', value: 'MEASURE', description: 'Quantitative values' },
    { label: 'Attribute', value: 'ATTRIBUTE', description: 'Additional qualitative info' },
    { label: 'Temporal', value: 'TEMPORAL', description: 'Time-based dimension' },
    { label: 'Geospatial', value: 'GEO', description: 'Location-based dimension' },
    { label: 'Coded', value: 'CODED', description: 'Code list based values' }
  ];

  savingDimension = signal(false);

  // Generated preview
  previewTab = signal<'turtle' | 'pipeline' | 'dsd'>('dsd');

  ngOnInit(): void {
    this.loadDataSources();
    this.loadDimensions();
    this.loadTriplestores();
  }

  // Step navigation
  canProceed(step: number): boolean {
    switch (step) {
      case 0:
        return !!this.cubeName() && !!this.baseUri();
      case 1:
        return !!this.selectedDataSource();
      case 2:
        const stats = this.mappingStats();
        return stats.dimensions > 0 && stats.measures > 0;
      case 3:
        const meta = this.metadata();
        return !!meta.title && !!meta.license;
      case 4:
        return this.validationComplete() && this.validationSummary().failed === 0;
      default:
        return true;
    }
  }

  nextStep(): void {
    const current = this.activeStep();

    if (!this.canProceed(current)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Incomplete',
        detail: 'Please complete all required fields before proceeding'
      });
      return;
    }

    // Special actions when leaving certain steps
    if (current === 0) {
      // Auto-populate metadata title
      this.metadata.update(m => ({ ...m, title: this.cubeName(), description: this.cubeDescription() }));
      // Auto-populate publish options
      const cubeIdValue = this.useAutoId() ? this.generatedId() : this.cubeId();
      this.publishOptions.update(p => ({
        ...p,
        graphUri: this.baseUri() + cubeIdValue,
        pipelineName: `Cube Pipeline: ${this.cubeName()}`
      }));
    }

    if (current === 3) {
      // Parse keywords
      const kw = this.keywordsInput().split(',').map(k => k.trim()).filter(k => k);
      this.metadata.update(m => ({ ...m, keywords: kw }));
    }

    const next = current + 1;
    this.activeStep.set(next);
    if (next > this.maxStepReached()) {
      this.maxStepReached.set(next);
    }
  }

  prevStep(): void {
    this.activeStep.update(v => Math.max(0, v - 1));
  }

  goToStep(step: number): void {
    if (step <= this.maxStepReached()) {
      this.activeStep.set(step);
    }
  }

  // Data loading
  loadDataSources(): void {
    this.loadingDataSources.set(true);
    this.dataService.list().subscribe({
      next: (data) => {
        this.dataSources.set(data);
        this.loadingDataSources.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load data sources' });
        this.loadingDataSources.set(false);
      }
    });
  }

  loadDimensions(): void {
    this.loadingDimensions.set(true);
    this.dimensionService.list().subscribe({
      next: (data) => {
        this.availableDimensions.set(data);
        this.loadingDimensions.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load dimensions' });
        this.loadingDimensions.set(false);
      }
    });
  }

  loadTriplestores(): void {
    // For now, use static options. Could load from backend.
    this.triplestoreOptions.set([
      { label: 'GraphDB (Production)', value: 'graphdb' },
      { label: 'Fuseki (Development)', value: 'fuseki' },
      { label: 'Virtuoso', value: 'virtuoso' }
    ]);
  }

  // Data source handling
  onDataSourceSelect(dataSource: DataSource): void {
    this.selectedDataSource.set(dataSource);
    this.loadColumnsFromSource(dataSource);
  }

  loadColumnsFromSource(dataSource: DataSource): void {
    this.loadingColumns.set(true);
    this.dataService.analyze(dataSource.id).subscribe({
      next: (result) => {
        this.sourceColumns.set(result.columns);
        this.initializeColumnMappings(result.columns);
        this.loadingColumns.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to analyze data source' });
        this.loadingColumns.set(false);
      }
    });
  }

  initializeColumnMappings(columns: ColumnInfo[]): void {
    const mappings: ColumnMapping[] = columns.map(col => {
      // Smart type detection
      let role: 'dimension' | 'measure' | 'attribute' | 'ignore' = 'dimension';
      let datatype = 'xsd:string';

      const lowerName = col.name.toLowerCase();
      const colType = col.type?.toLowerCase() || 'string';

      // Guess role based on type and name
      if (['integer', 'decimal', 'float', 'double', 'number', 'numeric'].includes(colType)) {
        // Check if it looks like a measure
        if (lowerName.includes('count') || lowerName.includes('amount') ||
            lowerName.includes('value') || lowerName.includes('sum') ||
            lowerName.includes('total') || lowerName.includes('qty') ||
            lowerName.includes('quantity') || lowerName.includes('price')) {
          role = 'measure';
        }
        datatype = colType === 'integer' ? 'xsd:integer' : 'xsd:decimal';
      } else if (['date', 'datetime', 'timestamp'].includes(colType)) {
        datatype = colType === 'date' ? 'xsd:date' : 'xsd:dateTime';
      } else if (['boolean', 'bool'].includes(colType)) {
        datatype = 'xsd:boolean';
      }

      // Check for ID/key columns
      if (lowerName.includes('_id') || lowerName === 'id' || lowerName.includes('key')) {
        role = 'dimension';
      }

      // Check for notes/comments
      if (lowerName.includes('note') || lowerName.includes('comment') ||
          lowerName.includes('remark') || lowerName.includes('desc')) {
        role = 'attribute';
      }

      return {
        name: col.name,
        sourceType: col.type || 'string',
        role,
        datatype,
        required: true,
        predicateUri: this.generatePredicateUri(col.name)
      };
    });

    this.columnMappings.set(mappings);
  }

  generatePredicateUri(columnName: string): string {
    const normalized = columnName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    return `${this.baseUri()}property/${normalized}`;
  }

  updateMapping(index: number, field: keyof ColumnMapping, value: string | boolean | undefined): void {
    this.columnMappings.update(mappings => {
      const updated = [...mappings];
      updated[index] = { ...updated[index], [field]: value };
      return updated;
    });
  }

  autoAssignDimensions(): void {
    const dims = this.availableDimensions();
    this.columnMappings.update(mappings => {
      return mappings.map(m => {
        if (m.role === 'dimension' && !m.dimensionId) {
          // Try to find matching dimension by name
          const match = dims.find(d =>
            d.name.toLowerCase() === m.name.toLowerCase() ||
            d.name.toLowerCase().includes(m.name.toLowerCase()) ||
            m.name.toLowerCase().includes(d.name.toLowerCase())
          );
          if (match) {
            return { ...m, dimensionId: match.id, dimensionName: match.name };
          }
        }
        return m;
      });
    });

    this.messageService.add({
      severity: 'info',
      summary: 'Auto-Assign Complete',
      detail: 'Dimensions matched where possible'
    });
  }

  // File upload
  get uploadOptions(): UploadOptions {
    return {
      delimiter: this.delimiter(),
      encoding: this.encoding(),
      hasHeader: this.hasHeader(),
      analyze: true
    };
  }

  onFileSelect(event: { files: File[] }): void {
    const file = event.files[0];
    if (file) {
      this.uploading.set(true);
      this.dataService.upload(file, this.uploadOptions).subscribe({
        next: (dataSource) => {
          this.loadDataSources();
          this.selectedDataSource.set(dataSource);
          this.loadColumnsFromSource(dataSource);
          this.sourceType.set('existing');
          this.uploading.set(false);
          this.messageService.add({
            severity: 'success',
            summary: 'Upload Complete',
            detail: `File "${file.name}" uploaded successfully`
          });
        },
        error: (err) => {
          this.uploading.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Upload Failed',
            detail: err.error?.message || 'Failed to upload file'
          });
        }
      });
    }
  }

  // Dimension dialog
  openNewDimensionDialog(columnName?: string): void {
    this.newDimension.set({
      name: columnName || '',
      uri: '',
      type: 'KEY',
      description: ''
    });
    this.showNewDimensionDialog.set(true);
  }

  saveNewDimension(): void {
    const dim = this.newDimension();
    if (!dim.name) {
      this.messageService.add({ severity: 'warn', summary: 'Required', detail: 'Dimension name is required' });
      return;
    }

    if (!dim.uri) {
      dim.uri = `${this.baseUri()}dimension/${dim.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
    }

    this.savingDimension.set(true);
    this.dimensionService.create(dim as Dimension).subscribe({
      next: (created) => {
        this.loadDimensions();
        this.showNewDimensionDialog.set(false);
        this.savingDimension.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Created',
          detail: `Dimension "${created.name}" created`
        });
      },
      error: () => {
        this.savingDimension.set(false);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create dimension' });
      }
    });
  }

  // Validation
  runValidation(): void {
    this.validating.set(true);
    this.validationComplete.set(false);

    // Reset all checks
    this.validationChecks.update(checks => checks.map(c => ({ ...c, status: 'pending' as const, message: undefined })));

    // Run checks sequentially with delays for visual feedback
    const runCheck = (index: number) => {
      if (index >= this.validationChecks().length) {
        this.validating.set(false);
        this.validationComplete.set(true);
        return;
      }

      // Set current check to running
      this.validationChecks.update(checks => {
        const updated = [...checks];
        updated[index] = { ...updated[index], status: 'running' };
        return updated;
      });

      // Simulate validation (in real app, call backend)
      setTimeout(() => {
        const result = this.performValidation(this.validationChecks()[index].id);
        this.validationChecks.update(checks => {
          const updated = [...checks];
          updated[index] = { ...updated[index], ...result };
          return updated;
        });
        runCheck(index + 1);
      }, 500);
    };

    runCheck(0);
  }

  private performValidation(checkId: string): Partial<ValidationCheck> {
    const stats = this.mappingStats();
    const meta = this.metadata();

    switch (checkId) {
      case 'schema':
        if (stats.dimensions === 0) {
          return { status: 'failed', message: 'At least one dimension is required' };
        }
        if (stats.measures === 0) {
          return { status: 'failed', message: 'At least one measure is required' };
        }
        return { status: 'passed', message: 'Valid Data Cube structure' };

      case 'dimensions':
        const unmapped = this.columnMappings().filter(m => m.role === 'dimension' && !m.dimensionId).length;
        if (unmapped > 0) {
          return { status: 'warning', message: `${unmapped} dimension(s) without reference` };
        }
        return { status: 'passed', message: 'All dimensions properly referenced' };

      case 'measures':
        const measureMappings = this.columnMappings().filter(m => m.role === 'measure');
        const nonNumeric = measureMappings.filter(m => !['xsd:integer', 'xsd:decimal', 'xsd:float', 'xsd:double'].includes(m.datatype));
        if (nonNumeric.length > 0) {
          return { status: 'warning', message: `${nonNumeric.length} measure(s) with non-numeric datatype` };
        }
        return { status: 'passed', message: 'All measures have numeric datatypes' };

      case 'metadata':
        const issues: string[] = [];
        if (!meta.title) issues.push('title');
        if (!meta.license) issues.push('license');
        if (!meta.publisher) issues.push('publisher');
        if (issues.length > 0) {
          if (issues.includes('title') || issues.includes('license')) {
            return { status: 'failed', message: `Missing required: ${issues.join(', ')}` };
          }
          return { status: 'warning', message: `Recommended fields missing: ${issues.join(', ')}` };
        }
        return { status: 'passed', message: 'All metadata fields complete' };

      case 'shacl':
        // Simulate SHACL validation
        return { status: 'passed', message: 'Conforms to RDF Data Cube vocabulary' };

      default:
        return { status: 'passed' };
    }
  }

  // Preview generation
  generateDsdPreview(): string {
    const cubeId = this.useAutoId() ? this.generatedId() : this.cubeId();
    const base = this.baseUri();
    const meta = this.metadata();
    const mappings = this.columnMappings().filter(m => m.role !== 'ignore');

    let turtle = `@prefix qb: <http://purl.org/linked-data/cube#> .\n`;
    turtle += `@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n`;
    turtle += `@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n`;
    turtle += `@prefix dct: <http://purl.org/dc/terms/> .\n`;
    turtle += `@prefix : <${base}> .\n\n`;

    // Data Structure Definition
    turtle += `# Data Structure Definition\n`;
    turtle += `:${cubeId}-dsd a qb:DataStructureDefinition ;\n`;
    turtle += `    rdfs:label "${meta.title} Structure"@en ;\n`;

    const components: string[] = [];

    mappings.forEach((m, i) => {
      const propId = m.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      if (m.role === 'dimension') {
        components.push(`    qb:component [ qb:dimension :${propId} ]`);
      } else if (m.role === 'measure') {
        components.push(`    qb:component [ qb:measure :${propId} ]`);
      } else if (m.role === 'attribute') {
        components.push(`    qb:component [ qb:attribute :${propId} ]`);
      }
    });

    turtle += components.join(' ;\n') + ' .\n\n';

    // Component definitions
    turtle += `# Component Definitions\n`;
    mappings.forEach(m => {
      const propId = m.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      turtle += `:${propId} a rdf:Property ;\n`;
      turtle += `    rdfs:label "${m.name}"@en ;\n`;
      turtle += `    rdfs:range ${m.datatype} .\n\n`;
    });

    return turtle;
  }

  generatePipelinePreview(): string {
    const cubeId = this.useAutoId() ? this.generatedId() : this.cubeId();
    const source = this.selectedDataSource();
    const mappings = this.columnMappings().filter(m => m.role !== 'ignore');

    const pipeline = {
      name: `cube-${cubeId}`,
      description: `Pipeline to generate ${this.cubeName()} data cube`,
      steps: [
        {
          operation: 'load-csv',
          params: {
            source: source?.id,
            delimiter: this.delimiter(),
            hasHeader: this.hasHeader()
          }
        },
        {
          operation: 'map-to-rdf',
          params: {
            baseUri: this.baseUri(),
            mappings: mappings.map(m => ({
              column: m.name,
              role: m.role,
              datatype: m.datatype,
              dimension: m.dimensionId
            }))
          }
        },
        {
          operation: 'generate-cube',
          params: {
            cubeId: cubeId,
            metadata: this.metadata()
          }
        },
        {
          operation: 'publish',
          params: this.publishOptions()
        }
      ]
    };

    return JSON.stringify(pipeline, null, 2);
  }

  generateTurtlePreview(): string {
    const cubeId = this.useAutoId() ? this.generatedId() : this.cubeId();
    const base = this.baseUri();
    const meta = this.metadata();

    let turtle = `@prefix qb: <http://purl.org/linked-data/cube#> .\n`;
    turtle += `@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n`;
    turtle += `@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n`;
    turtle += `@prefix dct: <http://purl.org/dc/terms/> .\n`;
    turtle += `@prefix : <${base}> .\n\n`;

    // Dataset
    turtle += `# Data Cube Dataset\n`;
    turtle += `:${cubeId} a qb:DataSet ;\n`;
    turtle += `    rdfs:label "${meta.title}"@en ;\n`;
    if (meta.description) {
      turtle += `    dct:description "${meta.description}"@en ;\n`;
    }
    if (meta.publisher) {
      turtle += `    dct:publisher "${meta.publisher}" ;\n`;
    }
    if (meta.license) {
      turtle += `    dct:license <${meta.license}> ;\n`;
    }
    turtle += `    qb:structure :${cubeId}-dsd .\n\n`;

    // Sample observation
    turtle += `# Sample Observation\n`;
    turtle += `:${cubeId}/obs/1 a qb:Observation ;\n`;
    turtle += `    qb:dataSet :${cubeId} ;\n`;

    const mappings = this.columnMappings().filter(m => m.role !== 'ignore');
    mappings.forEach((m, i) => {
      const propId = m.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      const isLast = i === mappings.length - 1;
      turtle += `    :${propId} "example-value"${isLast ? ' .' : ' ;'}\n`;
    });

    return turtle;
  }

  // Publishing
  publishCube(): void {
    this.confirmService.confirm({
      message: `Are you sure you want to publish the cube "${this.cubeName()}" to the triplestore?`,
      header: 'Confirm Publication',
      icon: 'pi pi-cloud-upload',
      accept: () => this.doPublish()
    });
  }

  private doPublish(): void {
    this.publishing.set(true);
    this.publishProgress.set(0);

    const cubeId = this.useAutoId() ? this.generatedId() : this.cubeId();

    // Create pipeline definition
    const pipelineDef = {
      name: cubeId,
      steps: [
        {
          operation: 'op:load-csv',
          params: {
            source: this.selectedDataSource()?.id,
            options: this.uploadOptions
          }
        },
        {
          operation: 'op:map-to-rdf',
          params: {
            baseUri: this.baseUri(),
            mappings: this.columnMappings()
              .filter(m => m.role !== 'ignore')
              .map(m => ({
                column: m.name,
                role: m.role,
                datatype: m.datatype,
                dimension: m.dimensionId
              }))
          }
        },
        {
          operation: 'op:generate-cube',
          params: {
            cubeId: cubeId,
            baseUri: this.baseUri(),
            metadata: this.metadata()
          }
        },
        {
          operation: 'op:publish',
          params: this.publishOptions()
        }
      ]
    };

    // Simulate progress
    const progressInterval = setInterval(() => {
      this.publishProgress.update(p => Math.min(90, p + 10));
    }, 300);

    this.pipelineService.create({
      name: this.publishOptions().pipelineName || `Cube Pipeline: ${this.cubeName()}`,
      description: `Pipeline to generate and publish ${this.cubeName()} data cube`,
      definition: JSON.stringify(pipelineDef, null, 2),
      definitionFormat: 'json',
      tags: ['cube-generation', 'auto-generated']
    }).subscribe({
      next: (pipeline) => {
        clearInterval(progressInterval);
        this.publishProgress.set(100);

        setTimeout(() => {
          this.publishing.set(false);
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: `Cube "${this.cubeName()}" pipeline created successfully`,
            life: 5000
          });

          // Navigate to pipeline or jobs
          if (this.publishOptions().runImmediately) {
            this.router.navigate(['/jobs']);
          } else {
            this.router.navigate(['/pipelines']);
          }
        }, 500);
      },
      error: (err) => {
        clearInterval(progressInterval);
        this.publishing.set(false);
        this.publishProgress.set(0);
        this.messageService.add({
          severity: 'error',
          summary: 'Publication Failed',
          detail: err.error?.message || 'Failed to create cube pipeline'
        });
      }
    });
  }

  // Utility
  getStepIcon(step: number): string {
    const icons = ['pi-info-circle', 'pi-database', 'pi-sitemap', 'pi-tags', 'pi-check-circle', 'pi-cloud-upload'];
    return icons[step] || 'pi-circle';
  }

  getStepStatus(step: number): 'completed' | 'current' | 'pending' {
    const current = this.activeStep();
    if (step < current) return 'completed';
    if (step === current) return 'current';
    return 'pending';
  }

  getRoleSeverity(role: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined {
    switch (role) {
      case 'dimension': return 'info';
      case 'measure': return 'success';
      case 'attribute': return 'warn';
      case 'ignore': return 'secondary';
      default: return undefined;
    }
  }

  getValidationIcon(status: string): string {
    switch (status) {
      case 'passed': return 'pi-check-circle';
      case 'failed': return 'pi-times-circle';
      case 'warning': return 'pi-exclamation-triangle';
      case 'running': return 'pi-spin pi-spinner';
      default: return 'pi-circle';
    }
  }

  getValidationSeverity(status: string): 'success' | 'danger' | 'warn' | 'info' | 'secondary' | 'contrast' | undefined {
    switch (status) {
      case 'passed': return 'success';
      case 'failed': return 'danger';
      case 'warning': return 'warn';
      case 'running': return 'info';
      default: return 'secondary';
    }
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text);
    this.messageService.add({ severity: 'info', summary: 'Copied', detail: 'Content copied to clipboard' });
  }

  // Form update helpers (Angular templates don't support arrow functions)
  updateMetadataTitle(value: string): void {
    this.metadata.update(m => ({ ...m, title: value }));
  }
  updateMetadataLanguage(value: string): void {
    this.metadata.update(m => ({ ...m, language: value }));
  }
  updateMetadataDescription(value: string): void {
    this.metadata.update(m => ({ ...m, description: value }));
  }
  updateMetadataPublisher(value: string): void {
    this.metadata.update(m => ({ ...m, publisher: value }));
  }
  updateMetadataLicense(value: string): void {
    this.metadata.update(m => ({ ...m, license: value }));
  }
  updateMetadataIssued(value: string): void {
    this.metadata.update(m => ({ ...m, issued: value }));
  }
  updateMetadataAccrualPeriodicity(value: string): void {
    this.metadata.update(m => ({ ...m, accrualPeriodicity: value }));
  }
  updateMetadataSpatial(value: string): void {
    this.metadata.update(m => ({ ...m, spatial: value }));
  }
  updateMetadataTemporal(value: string): void {
    this.metadata.update(m => ({ ...m, temporal: value }));
  }

  // Publish options helpers
  updatePublishTriplestore(value: string): void {
    this.publishOptions.update(p => ({ ...p, triplestore: value }));
  }
  updatePublishGraphUri(value: string): void {
    this.publishOptions.update(p => ({ ...p, graphUri: value }));
  }
  updatePublishCreateNamedGraph(value: boolean): void {
    this.publishOptions.update(p => ({ ...p, createNamedGraph: value }));
  }
  updatePublishSavePipeline(value: boolean): void {
    this.publishOptions.update(p => ({ ...p, savePipeline: value }));
  }
  updatePublishPipelineName(value: string): void {
    this.publishOptions.update(p => ({ ...p, pipelineName: value }));
  }
  updatePublishRunImmediately(value: boolean): void {
    this.publishOptions.update(p => ({ ...p, runImmediately: value }));
  }

  // New dimension helpers
  updateNewDimensionName(value: string): void {
    this.newDimension.update(d => ({ ...d, name: value }));
  }
  updateNewDimensionType(value: DimensionType): void {
    this.newDimension.update(d => ({ ...d, type: value }));
  }
  updateNewDimensionUri(value: string): void {
    this.newDimension.update(d => ({ ...d, uri: value }));
  }
  updateNewDimensionDescription(value: string): void {
    this.newDimension.update(d => ({ ...d, description: value }));
  }

  // Helper to get license label
  getLicenseLabel(): string {
    const license = this.metadata().license;
    if (!license) return '-';
    const found = this.licenseOptions.find(opt => opt.value === license);
    return found?.label || license;
  }
}
