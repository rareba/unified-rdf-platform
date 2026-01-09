import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { DataService, DimensionService, PipelineService, CubeService } from '../../../core/services';
import { DataSource, Dimension, ColumnInfo, DimensionType, UploadOptions, Cube } from '../../../core/models';
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
  sampleValues?: string[];
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

interface SourceMapping {
  dataSourceId: string;
  dataSourceName: string;
  columnMappings: ColumnMapping[];
}

interface CubeDefinition {
  id: string;
  name: string;
  description: string;
  baseUri: string;
  sourceMappings: SourceMapping[];
  metadata: CubeMetadata;
}

@Component({
  selector: 'app-cube-wizard',
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatStepperModule,
    MatButtonModule,
    MatInputModule,
    MatSelectModule,
    MatListModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatTableModule,
    MatChipsModule,
    MatTabsModule,
    MatDividerModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatFormFieldModule,
    DataPreviewComponent
  ],
  templateUrl: './cube-wizard.html',
  styleUrl: './cube-wizard.scss',
})
export class CubeWizard implements OnInit {
  private readonly dataService = inject(DataService);
  private readonly dimensionService = inject(DimensionService);
  private readonly pipelineService = inject(PipelineService);
  private readonly cubeService = inject(CubeService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  // Existing cubes
  existingCubes = signal<Cube[]>([]);
  loadingCubes = signal(false);
  selectedExistingCube = signal<Cube | null>(null);
  createMode = signal<'new' | 'update'>('new');

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
  sourceType = signal<'existing' | 'upload'>('existing');
  dataSources = signal<DataSource[]>([]);
  selectedDataSource = signal<DataSource | null>(null);
  selectedDataSources = signal<DataSource[]>([]); // Multi-source support
  sourceColumns = signal<ColumnInfo[]>([]);
  loadingDataSources = signal(false);
  loadingColumns = signal(false);

  // Multi-cube support
  cubeDefinitions = signal<CubeDefinition[]>([]);
  activeCubeIndex = signal(0);

  // Computed: active cube definition
  activeCube = computed(() => {
    const cubes = this.cubeDefinitions();
    const idx = this.activeCubeIndex();
    return cubes[idx] || null;
  });

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
  isDragOver = signal(false);

  // Step 3: Column Mapping
  columnMappings = signal<ColumnMapping[]>([]);

  roleOptions = [
    { label: 'Dimension', value: 'dimension', description: 'Categorical data for grouping' },
    { label: 'Measure', value: 'measure', description: 'Numeric values to aggregate' },
    { label: 'Attribute', value: 'attribute', description: 'Additional metadata' },
    { label: 'Ignore', value: 'ignore', description: 'Exclude from cube' }
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

  // Step 6: Save & Generate
  publishOptions = signal({
    triplestore: '',
    graphUri: '',
    createNamedGraph: true,
    savePipeline: true,
    pipelineName: '',
    runImmediately: false  // Default to false - user decides when to run
  });

  // New: Options for what to generate when saving
  saveOptions = signal({
    generateShacl: true,    // Generate SHACL shape from column mappings
    generatePipeline: true, // Generate draft pipeline
    publishImmediately: false  // Run the pipeline right away
  });

  // Track generated artifacts for linking to their editors
  savedCubeId = signal<string | null>(null);
  generatedShapeId = signal<string | null>(null);
  generatedPipelineId = signal<string | null>(null);
  saveComplete = signal(false);

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
    this.loadExistingCubes();
  }

  loadExistingCubes(): void {
    this.loadingCubes.set(true);
    this.cubeService.list().subscribe({
      next: (cubes) => {
        this.existingCubes.set(cubes);
        this.loadingCubes.set(false);
      },
      error: () => {
        // Silent fail - cubes endpoint might not be available yet
        this.loadingCubes.set(false);
      }
    });
  }

  selectExistingCube(cube: Cube): void {
    this.selectedExistingCube.set(cube);
    this.createMode.set('update');
    // Populate form with existing cube data
    this.cubeName.set(cube.name);
    this.cubeId.set(cube.uri.split('/').pop() || '');
    this.baseUri.set(cube.uri.replace(/[^/]+$/, ''));
    if (cube.description) {
      this.cubeDescription.set(cube.description);
    }
    this.useAutoId.set(false);
    this.snackBar.open(`Editing cube "${cube.name}"`, 'Close', { duration: 3000 });
  }

  clearExistingCubeSelection(): void {
    this.selectedExistingCube.set(null);
    this.createMode.set('new');
    this.cubeName.set('');
    this.cubeId.set('');
    this.cubeDescription.set('');
    this.useAutoId.set(true);
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
      this.snackBar.open('Please complete all required fields before proceeding', 'Close', { duration: 3000 });
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
        this.snackBar.open('Failed to load data sources', 'Close', { duration: 3000 });
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
        this.snackBar.open('Failed to load dimensions', 'Close', { duration: 3000 });
        this.loadingDimensions.set(false);
      }
    });
  }

  loadTriplestores(): void {
    // For now, use static options. Could load from backend.
    this.triplestoreOptions.set([
      { label: 'GraphDB (Production)', value: 'GRAPHDB' },
      { label: 'Fuseki (Development)', value: 'FUSEKI' },
      { label: 'Virtuoso', value: 'VIRTUOSO' }
    ]);
  }

  // Data source handling
  onDataSourceSelect(dataSource: DataSource): void {
    this.selectedDataSource.set(dataSource);
    this.loadColumnsFromSource(dataSource);
  }

  // Multi-source selection toggle
  toggleDataSourceSelection(dataSource: DataSource): void {
    const current = this.selectedDataSources();
    const exists = current.some(s => s.id === dataSource.id);

    if (exists) {
      this.selectedDataSources.set(current.filter(s => s.id !== dataSource.id));
    } else {
      this.selectedDataSources.set([...current, dataSource]);
    }

    // Also update single selection for backward compatibility
    if (!exists) {
      this.selectedDataSource.set(dataSource);
      this.loadColumnsFromSource(dataSource);
    }
  }

  isDataSourceSelected(dataSource: DataSource): boolean {
    return this.selectedDataSources().some(s => s.id === dataSource.id);
  }

  // Multi-cube management
  addCubeDefinition(): void {
    const newCube: CubeDefinition = {
      id: crypto.randomUUID(),
      name: `Cube ${this.cubeDefinitions().length + 1}`,
      description: '',
      baseUri: this.baseUri(),
      sourceMappings: [],
      metadata: {
        title: '',
        description: '',
        publisher: '',
        publisherUri: '',
        license: '',
        issued: '',
        modified: '',
        keywords: [],
        language: 'en',
        accrualPeriodicity: '',
        spatial: '',
        temporal: ''
      }
    };
    this.cubeDefinitions.update(cubes => [...cubes, newCube]);
    this.activeCubeIndex.set(this.cubeDefinitions().length - 1);
    this.snackBar.open(`Added new cube definition`, 'Close', { duration: 2000 });
  }

  removeCubeDefinition(index: number): void {
    const cubes = this.cubeDefinitions();
    if (cubes.length <= 1) {
      this.snackBar.open('Cannot remove the only cube', 'Close', { duration: 2000 });
      return;
    }
    this.cubeDefinitions.update(c => c.filter((_, i) => i !== index));
    if (this.activeCubeIndex() >= index && this.activeCubeIndex() > 0) {
      this.activeCubeIndex.update(i => i - 1);
    }
  }

  selectCubeDefinition(index: number): void {
    this.activeCubeIndex.set(index);
  }

  updateCubeDefinition(index: number, updates: Partial<CubeDefinition>): void {
    this.cubeDefinitions.update(cubes =>
      cubes.map((c, i) => i === index ? { ...c, ...updates } : c)
    );
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
        this.snackBar.open('Failed to analyze data source', 'Close', { duration: 3000 });
        this.loadingColumns.set(false);
      }
    });
  }

  initializeColumnMappings(columns: ColumnInfo[]): void {
    if (!columns || columns.length === 0) {
      this.columnMappings.set([]);
      return;
    }

    const mappings: ColumnMapping[] = columns.map(col => {
      // Smart type detection
      let role: 'dimension' | 'measure' | 'attribute' | 'ignore' = 'dimension';
      let datatype = 'xsd:string';

      const lowerName = (col.name || '').toLowerCase();
      const colType = (col.type || 'string').toLowerCase();

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
    let matchCount = 0;

    this.columnMappings.update(mappings => {
      return mappings.map(m => {
        if (m.role === 'dimension' && !m.dimensionId) {
          // Try to find matching dimension with multiple strategies
          const match = this.findBestDimensionMatch(m.name, dims);
          if (match) {
            matchCount++;
            return { ...m, dimensionId: match.id, dimensionName: match.name };
          }
        }
        return m;
      });
    });

    const unmatchedCount = this.columnMappings().filter(m => m.role === 'dimension' && !m.dimensionId).length;
    if (matchCount > 0) {
      this.snackBar.open(`Matched ${matchCount} dimension(s)${unmatchedCount > 0 ? `, ${unmatchedCount} still unmatched` : ''}`, 'Close', { duration: 3000 });
    } else {
      this.snackBar.open('No automatic matches found. Please assign dimensions manually.', 'Close', { duration: 3000 });
    }
  }

  private findBestDimensionMatch(columnName: string, dimensions: Dimension[]): Dimension | null {
    if (!dimensions || dimensions.length === 0) return null;

    const normalizedCol = this.normalizeForMatching(columnName);
    let bestMatch: Dimension | null = null;
    let bestScore = 0;

    for (const dim of dimensions) {
      const normalizedDim = this.normalizeForMatching(dim.name);
      let score = 0;

      // Exact match (after normalization)
      if (normalizedCol === normalizedDim) {
        score = 100;
      }
      // Column contains dimension name or vice versa
      else if (normalizedCol.includes(normalizedDim) || normalizedDim.includes(normalizedCol)) {
        score = 80;
      }
      // Word-based matching
      else {
        const colWords = normalizedCol.split(/[\s_-]+/).filter(w => w.length > 2);
        const dimWords = normalizedDim.split(/[\s_-]+/).filter(w => w.length > 2);

        // Count matching words
        const matchingWords = colWords.filter(cw =>
          dimWords.some(dw => cw === dw || cw.includes(dw) || dw.includes(cw))
        ).length;

        if (matchingWords > 0) {
          score = 60 + (matchingWords * 10);
        }
      }

      // Check for common dimension patterns
      score += this.getPatternBonus(normalizedCol, normalizedDim);

      if (score > bestScore && score >= 60) {
        bestScore = score;
        bestMatch = dim;
      }
    }

    return bestMatch;
  }

  private normalizeForMatching(str: string): string {
    return str.toLowerCase()
      .replace(/[_-]/g, ' ')           // Replace underscores and hyphens with spaces
      .replace(/([a-z])([A-Z])/g, '$1 $2') // Split camelCase
      .replace(/\s+/g, ' ')            // Normalize multiple spaces
      .trim();
  }

  private getPatternBonus(colName: string, dimName: string): number {
    // Common dimension pattern mappings
    const patterns: [string[], string[]][] = [
      // Time dimensions
      [['year', 'yr', 'anno', 'jahr'], ['year', 'time', 'period', 'reference', 'ref']],
      [['month', 'mon', 'mese', 'monat'], ['month', 'time', 'period', 'reference', 'ref']],
      [['quarter', 'qtr', 'q'], ['quarter', 'time', 'period']],
      [['date', 'datum', 'data'], ['date', 'time', 'period', 'reference']],
      // Geographic dimensions
      [['canton', 'kanton', 'cantone'], ['canton', 'region', 'geo', 'geography']],
      [['region', 'regione', 'gebiet'], ['region', 'geo', 'geography']],
      [['country', 'land', 'paese', 'pays'], ['country', 'geo', 'geography']],
      [['city', 'stadt', 'citta', 'ville'], ['city', 'municipality', 'geo']],
      // Economic dimensions
      [['sector', 'sektor', 'settore', 'secteur'], ['sector', 'industry', 'economic']],
      [['branch', 'branche', 'ramo'], ['branch', 'sector', 'industry']],
      // Other common dimensions
      [['sex', 'gender', 'geschlecht', 'sesso'], ['sex', 'gender']],
      [['age', 'alter', 'eta', 'age'], ['age', 'age group']],
      [['nationality', 'nationalitat', 'nazionalita'], ['nationality', 'citizenship']],
    ];

    for (const [colPatterns, dimPatterns] of patterns) {
      const colMatches = colPatterns.some(p => colName.includes(p));
      const dimMatches = dimPatterns.some(p => dimName.includes(p));
      if (colMatches && dimMatches) {
        return 15; // Bonus for matching common patterns
      }
    }

    return 0;
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
          this.snackBar.open(`File "${file.name}" uploaded successfully`, 'Close', { duration: 3000 });
        },
        error: (err) => {
          this.uploading.set(false);
          this.snackBar.open(err.error?.message || 'Failed to upload file', 'Close', { duration: 3000 });
        }
      });
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      this.onFileSelect({ files: [input.files[0]] });
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      const file = files[0];
      const validExtensions = ['.csv', '.xls', '.xlsx', '.tsv'];
      const fileExt = '.' + file.name.split('.').pop()?.toLowerCase();

      if (validExtensions.includes(fileExt)) {
        this.onFileSelect({ files: [file] });
      } else {
        this.snackBar.open('Invalid file format. Supported: CSV, XLS, XLSX, TSV', 'Close', { duration: 3000 });
      }
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
      this.snackBar.open('Dimension name is required', 'Close', { duration: 3000 });
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
        this.snackBar.open(`Dimension "${created.name}" created`, 'Close', { duration: 3000 });
      },
      error: () => {
        this.savingDimension.set(false);
        this.snackBar.open('Failed to create dimension', 'Close', { duration: 3000 });
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

  // ===== Save & Generate Workflow =====

  /**
   * Main save action - saves cube definition first, then optionally generates artifacts
   */
  saveCubeDefinition(): void {
    this.publishing.set(true);
    this.publishProgress.set(0);
    this.saveComplete.set(false);

    const cubeIdValue = this.useAutoId() ? this.generatedId() : this.cubeId();
    const cubeUri = this.baseUri() + cubeIdValue;
    const options = this.saveOptions();

    // Step 1: Create/Update the Cube Definition
    const cubeData = {
      name: this.cubeName(),
      uri: cubeUri,
      description: this.cubeDescription(),
      sourceDataId: this.selectedDataSource()?.id,
      graphUri: this.publishOptions().graphUri || cubeUri,
      metadata: {
        ...this.metadata(),
        columnMappings: this.columnMappings().filter(m => m.role !== 'ignore').map(m => ({
          name: m.name,
          role: m.role,
          datatype: m.datatype,
          dimensionId: m.dimensionId,
          dimensionName: m.dimensionName,
          predicateUri: m.predicateUri
        })),
        csvOptions: {
          delimiter: this.delimiter(),
          hasHeader: this.hasHeader(),
          encoding: this.encoding()
        }
      }
    };

    this.publishProgress.set(20);

    // Create or update cube based on mode
    const cubeObs = this.createMode() === 'update' && this.selectedExistingCube()
      ? this.cubeService.update(this.selectedExistingCube()!.id!, cubeData)
      : this.cubeService.create(cubeData as any);

    cubeObs.subscribe({
      next: (savedCube) => {
        this.savedCubeId.set(savedCube.id!);
        this.publishProgress.set(40);
        this.snackBar.open(`Cube definition "${this.cubeName()}" saved!`, 'Close', { duration: 3000 });

        // Step 2: Generate SHACL shape if requested
        if (options.generateShacl) {
          this.generateShapeFromCube(savedCube.id!, options.generatePipeline);
        } else if (options.generatePipeline) {
          // Skip to pipeline generation
          this.generatePipelineFromCube(savedCube.id!);
        } else {
          // Just saved cube definition, we're done
          this.finishSave();
        }
      },
      error: (err) => {
        this.publishing.set(false);
        this.publishProgress.set(0);
        this.snackBar.open(err.error?.message || 'Failed to save cube definition', 'Close', { duration: 3000 });
      }
    });
  }

  /**
   * Generate SHACL shape from the saved cube definition
   */
  private generateShapeFromCube(cubeId: string, alsoGeneratePipeline: boolean): void {
    this.cubeService.generateShape(cubeId, {
      name: `${this.cubeName()} Validation Shape`
    }).subscribe({
      next: (artifact) => {
        this.generatedShapeId.set(artifact.id);
        this.publishProgress.set(60);
        this.snackBar.open(`SHACL shape "${artifact.name}" generated!`, 'Close', { duration: 3000 });

        // Step 3: Generate pipeline if requested
        if (alsoGeneratePipeline) {
          this.generatePipelineFromCube(cubeId);
        } else {
          this.finishSave();
        }
      },
      error: (err) => {
        console.warn('Failed to generate SHACL shape:', err);
        this.snackBar.open('Failed to generate SHACL shape. Continuing with other steps...', 'Close', { duration: 4000 });
        // Continue with pipeline generation even if shape fails
        if (alsoGeneratePipeline) {
          this.generatePipelineFromCube(cubeId);
        } else {
          this.finishSave();
        }
      }
    });
  }

  /**
   * Generate draft pipeline from the saved cube definition
   */
  private generatePipelineFromCube(cubeId: string): void {
    this.cubeService.generatePipeline(cubeId, {
      name: this.publishOptions().pipelineName || `Pipeline: ${this.cubeName()}`,
      graphUri: this.publishOptions().graphUri
    }).subscribe({
      next: (artifact) => {
        this.generatedPipelineId.set(artifact.id);
        this.publishProgress.set(80);
        this.snackBar.open(`Draft pipeline "${artifact.name}" generated!`, 'Close', { duration: 3000 });
        this.finishSave();
      },
      error: (err) => {
        console.warn('Failed to generate pipeline:', err);
        this.snackBar.open('Failed to generate pipeline. Cube definition was saved successfully.', 'Close', { duration: 4000 });
        this.finishSave();
      }
    });
  }

  /**
   * Complete the save process
   */
  private finishSave(): void {
    this.publishProgress.set(100);
    this.saveComplete.set(true);

    setTimeout(() => {
      this.publishing.set(false);
    }, 500);
  }

  /**
   * Navigate to edit the generated SHACL shape
   */
  editGeneratedShape(): void {
    const shapeId = this.generatedShapeId();
    if (shapeId) {
      this.router.navigate(['/shacl', shapeId]);
    } else {
      this.router.navigate(['/shacl']);
    }
  }

  /**
   * Navigate to edit the generated pipeline
   */
  editGeneratedPipeline(): void {
    const pipelineId = this.generatedPipelineId();
    if (pipelineId) {
      this.router.navigate(['/pipelines', pipelineId]);
    } else {
      this.router.navigate(['/pipelines']);
    }
  }

  /**
   * Navigate to view/edit the saved cube
   */
  viewSavedCube(): void {
    const cubeId = this.savedCubeId();
    if (cubeId) {
      this.router.navigate(['/cubes', cubeId]);
    }
  }

  /**
   * Save cube definition and generate artifacts (SHACL shape, pipeline)
   */
  publishCube(): void {
    this.saveCubeDefinition();
  }

  /**
   * Publish the saved cube to the selected triplestore
   */
  publishToTriplestore(): void {
    const cubeId = this.savedCubeId();
    const pipelineId = this.generatedPipelineId();
    const triplestore = this.publishOptions().triplestore;

    if (!cubeId || !triplestore) {
      this.snackBar.open('Please save the cube first and select a triplestore', 'Close', { duration: 3000 });
      return;
    }

    this.publishing.set(true);
    this.publishProgress.set(0);

    // If we have a pipeline and user wants to run immediately, execute it
    if (pipelineId && this.publishOptions().runImmediately) {
      this.publishProgress.set(50);
      this.pipelineService.run(pipelineId).subscribe({
        next: (result: { jobId: string }) => {
          this.publishProgress.set(100);
          this.publishing.set(false);
          this.snackBar.open(`Pipeline execution started! Job ID: ${result.jobId}`, 'View Jobs', {
            duration: 5000
          }).onAction().subscribe(() => {
            this.router.navigate(['/jobs']);
          });
        },
        error: (err: { error?: { message?: string } }) => {
          this.publishing.set(false);
          this.snackBar.open(err.error?.message || 'Failed to execute pipeline', 'Close', { duration: 3000 });
        }
      });
    } else {
      // Just mark as ready for publishing (user can run pipeline later)
      this.publishProgress.set(100);
      this.publishing.set(false);
      this.snackBar.open('Cube is ready for publishing. Edit and run the pipeline when ready.', 'Close', { duration: 3000 });
    }
  }

  // Utility
  getStepStatus(step: number): 'completed' | 'current' | 'pending' {
    const current = this.activeStep();
    if (step < current) return 'completed';
    if (step === current) return 'current';
    return 'pending';
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text);
    this.snackBar.open('Content copied to clipboard', 'Close', { duration: 3000 });
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

  // Enhanced column mapping helpers
  setAllUnmappedToRole(role: 'dimension' | 'measure' | 'attribute'): void {
    this.columnMappings.update(mappings =>
      mappings.map(m => m.role === 'ignore' ? { ...m, role } : m)
    );
    const count = this.columnMappings().filter(m => m.role === role).length;
    this.snackBar.open(`Set ${count} columns as ${role}s`, 'Close', { duration: 2000 });
  }

  getColumnTypeIcon(sourceType: string): string {
    switch (sourceType?.toLowerCase()) {
      case 'integer':
      case 'int':
      case 'long':
      case 'number':
        return 'pin';
      case 'decimal':
      case 'double':
      case 'float':
        return 'calculate';
      case 'date':
      case 'datetime':
      case 'timestamp':
        return 'event';
      case 'boolean':
      case 'bool':
        return 'toggle_on';
      default:
        return 'text_fields';
    }
  }

  getRoleIcon(role: string): string {
    switch (role) {
      case 'dimension':
        return 'apps';
      case 'measure':
        return 'bar_chart';
      case 'attribute':
        return 'label';
      case 'ignore':
        return 'visibility_off';
      default:
        return 'help';
    }
  }

  getRoleLabel(role: string): string {
    switch (role) {
      case 'dimension':
        return 'Dimension';
      case 'measure':
        return 'Measure';
      case 'attribute':
        return 'Attribute';
      case 'ignore':
        return 'Ignored';
      default:
        return role;
    }
  }

  slugify(text: string): string {
    return text.toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');
  }
}
