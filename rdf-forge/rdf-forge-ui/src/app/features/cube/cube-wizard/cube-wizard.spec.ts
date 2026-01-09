import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CubeWizard } from './cube-wizard';
import { DataService, DimensionService, PipelineService, CubeService } from '../../../core/services';
import { DataSource, Dimension, Cube, ColumnInfo } from '../../../core/models';

describe('CubeWizard', () => {
  let component: CubeWizard;
  let fixture: ComponentFixture<CubeWizard>;
  let dataServiceSpy: jasmine.SpyObj<DataService>;
  let dimensionServiceSpy: jasmine.SpyObj<DimensionService>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;
  let cubeServiceSpy: jasmine.SpyObj<CubeService>;

  const mockDataSources: DataSource[] = [
    { id: '1', name: 'test-data.csv', originalFilename: 'test-data.csv', format: 'csv', sizeBytes: 1024, rowCount: 100, columnCount: 3, storagePath: '/data/test.csv', uploadedAt: new Date(), uploadedBy: 'user' }
  ];

  const mockDimensions: Dimension[] = [
    { id: 'dim1', name: 'Year', uri: 'http://example.org/dimension/year', type: 'TEMPORAL' }
  ];

  const mockCubes: Cube[] = [
    { id: 'cube1', name: 'Test Cube', uri: 'http://example.org/cube/test', createdAt: new Date() }
  ];

  const mockColumns: ColumnInfo[] = [
    { name: 'year', type: 'integer', nullable: false, nullCount: 0, uniqueCount: 50, sampleValues: ['2020', '2021'] },
    { name: 'value', type: 'decimal', nullable: false, nullCount: 0, uniqueCount: 100, sampleValues: ['123.45', '67.89'] },
    { name: 'region', type: 'string', nullable: true, nullCount: 5, uniqueCount: 10, sampleValues: ['North', 'South'] }
  ];

  beforeEach(async () => {
    dataServiceSpy = jasmine.createSpyObj('DataService', ['list', 'analyze', 'upload']);
    dimensionServiceSpy = jasmine.createSpyObj('DimensionService', ['list', 'create']);
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', ['run']);
    cubeServiceSpy = jasmine.createSpyObj('CubeService', ['list', 'create', 'update', 'generateShape', 'generatePipeline']);

    dataServiceSpy.list.and.returnValue(of(mockDataSources));
    dataServiceSpy.analyze.and.returnValue(of({ columns: mockColumns, rowCount: 100 }));
    dimensionServiceSpy.list.and.returnValue(of(mockDimensions));
    dimensionServiceSpy.create.and.returnValue(of(mockDimensions[0]));
    cubeServiceSpy.list.and.returnValue(of(mockCubes));
    cubeServiceSpy.create.and.returnValue(of(mockCubes[0]));

    await TestBed.configureTestingModule({
      imports: [CubeWizard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: DataService, useValue: dataServiceSpy },
        { provide: DimensionService, useValue: dimensionServiceSpy },
        { provide: PipelineService, useValue: pipelineServiceSpy },
        { provide: CubeService, useValue: cubeServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CubeWizard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });

  it('should load data sources on init', fakeAsync(() => {
    tick();
    expect(dataServiceSpy.list).toHaveBeenCalled();
    expect(component.dataSources().length).toBe(1);
  }));

  it('should load dimensions on init', fakeAsync(() => {
    tick();
    expect(dimensionServiceSpy.list).toHaveBeenCalled();
    expect(component.availableDimensions().length).toBe(1);
  }));

  it('should generate cube ID from name', () => {
    component.cubeName.set('My Test Cube');
    expect(component.generatedId()).toBe('my-test-cube');
  });

  it('should handle special characters in cube ID generation', () => {
    component.cubeName.set('Test & Special (Characters)!');
    expect(component.generatedId()).toBe('test-special-characters');
  });

  it('should check if can proceed from step 0', () => {
    expect(component.canProceed(0)).toBeFalse();
    component.cubeName.set('Test Cube');
    component.baseUri.set('http://example.org/');
    expect(component.canProceed(0)).toBeTrue();
  });

  it('should check if can proceed from step 1', () => {
    expect(component.canProceed(1)).toBeFalse();
    component.selectedDataSource.set(mockDataSources[0]);
    expect(component.canProceed(1)).toBeTrue();
  });

  it('should calculate mapping stats', () => {
    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true },
      { name: 'value', sourceType: 'decimal', role: 'measure', datatype: 'xsd:decimal', required: true },
      { name: 'note', sourceType: 'string', role: 'attribute', datatype: 'xsd:string', required: false },
      { name: 'ignore_col', sourceType: 'string', role: 'ignore', datatype: 'xsd:string', required: false }
    ]);
    const stats = component.mappingStats();
    expect(stats.dimensions).toBe(1);
    expect(stats.measures).toBe(1);
    expect(stats.attributes).toBe(1);
    expect(stats.ignored).toBe(1);
    expect(stats.total).toBe(4);
  });

  it('should navigate to next step', () => {
    component.cubeName.set('Test Cube');
    component.baseUri.set('http://example.org/');
    component.nextStep();
    expect(component.activeStep()).toBe(1);
    expect(component.maxStepReached()).toBe(1);
  });

  it('should navigate to previous step', () => {
    component.activeStep.set(2);
    component.prevStep();
    expect(component.activeStep()).toBe(1);
    component.prevStep();
    expect(component.activeStep()).toBe(0);
    component.prevStep();
    expect(component.activeStep()).toBe(0); // Should not go below 0
  });

  it('should get step status', () => {
    component.activeStep.set(2);
    expect(component.getStepStatus(0)).toBe('completed');
    expect(component.getStepStatus(1)).toBe('completed');
    expect(component.getStepStatus(2)).toBe('current');
    expect(component.getStepStatus(3)).toBe('pending');
  });

  it('should generate predicate URI from column name', () => {
    component.baseUri.set('http://example.org/cube/');
    const uri = component.generatePredicateUri('Test Column Name');
    expect(uri).toBe('http://example.org/cube/property/test-column-name');
  });

  it('should initialize column mappings from columns', () => {
    component.initializeColumnMappings(mockColumns);
    const mappings = component.columnMappings();
    expect(mappings.length).toBe(3);
    expect(mappings.find(m => m.name === 'year')?.role).toBe('dimension');
    expect(mappings.find(m => m.name === 'value')?.role).toBe('measure');
  });

  it('should update column mapping', () => {
    component.columnMappings.set([
      { name: 'test', sourceType: 'string', role: 'dimension', datatype: 'xsd:string', required: true }
    ]);
    component.updateMapping(0, 'role', 'measure');
    expect(component.columnMappings()[0].role).toBe('measure');
  });

  it('should get column type icon', () => {
    expect(component.getColumnTypeIcon('integer')).toBe('pin');
    expect(component.getColumnTypeIcon('decimal')).toBe('calculate');
    expect(component.getColumnTypeIcon('date')).toBe('event');
    expect(component.getColumnTypeIcon('boolean')).toBe('toggle_on');
    expect(component.getColumnTypeIcon('string')).toBe('text_fields');
  });

  it('should get role icon', () => {
    expect(component.getRoleIcon('dimension')).toBe('apps');
    expect(component.getRoleIcon('measure')).toBe('bar_chart');
    expect(component.getRoleIcon('attribute')).toBe('label');
    expect(component.getRoleIcon('ignore')).toBe('visibility_off');
  });

  it('should slugify text', () => {
    expect(component.slugify('Hello World')).toBe('hello-world');
    expect(component.slugify('Test & Example!')).toBe('test-example');
    expect(component.slugify('--Leading--')).toBe('leading');
  });

  it('should handle drag over event', () => {
    const mockEvent = { preventDefault: jasmine.createSpy(), stopPropagation: jasmine.createSpy() };
    component.onDragOver(mockEvent as any);
    expect(component.isDragOver()).toBeTrue();
    expect(mockEvent.preventDefault).toHaveBeenCalled();
  });

  it('should handle drag leave event', () => {
    component.isDragOver.set(true);
    const mockEvent = { preventDefault: jasmine.createSpy(), stopPropagation: jasmine.createSpy() };
    component.onDragLeave(mockEvent as any);
    expect(component.isDragOver()).toBeFalse();
  });

  it('should update metadata helpers', () => {
    component.updateMetadataTitle('New Title');
    expect(component.metadata().title).toBe('New Title');

    component.updateMetadataDescription('New Description');
    expect(component.metadata().description).toBe('New Description');

    component.updateMetadataPublisher('New Publisher');
    expect(component.metadata().publisher).toBe('New Publisher');

    component.updateMetadataLanguage('de');
    expect(component.metadata().language).toBe('de');
  });

  it('should select existing cube and populate form', fakeAsync(() => {
    tick();
    const cube = mockCubes[0];
    component.selectExistingCube(cube);
    expect(component.selectedExistingCube()).toBe(cube);
    expect(component.createMode()).toBe('update');
    expect(component.cubeName()).toBe('Test Cube');
  }));

  it('should clear existing cube selection', () => {
    component.selectedExistingCube.set(mockCubes[0]);
    component.createMode.set('update');
    component.cubeName.set('Test');
    component.clearExistingCubeSelection();
    expect(component.selectedExistingCube()).toBeNull();
    expect(component.createMode()).toBe('new');
    expect(component.cubeName()).toBe('');
  });

  it('should go to step only if reached', () => {
    component.maxStepReached.set(2);
    component.activeStep.set(0);
    component.goToStep(2);
    expect(component.activeStep()).toBe(2);
    component.goToStep(5);
    expect(component.activeStep()).toBe(2); // Should not go beyond maxStepReached
  });

  it('should select data source', fakeAsync(() => {
    tick();
    component.onDataSourceSelect(mockDataSources[0]);
    tick();
    expect(component.selectedDataSource()).toBe(mockDataSources[0]);
    expect(dataServiceSpy.analyze).toHaveBeenCalledWith('1');
  }));

  it('should toggle data source selection', fakeAsync(() => {
    tick();
    component.toggleDataSourceSelection(mockDataSources[0]);
    expect(component.isDataSourceSelected(mockDataSources[0])).toBeTrue();
    component.toggleDataSourceSelection(mockDataSources[0]);
    expect(component.isDataSourceSelected(mockDataSources[0])).toBeFalse();
  }));

  it('should add cube definition', () => {
    const initialCount = component.cubeDefinitions().length;
    component.addCubeDefinition();
    expect(component.cubeDefinitions().length).toBe(initialCount + 1);
  });

  it('should select cube definition', () => {
    component.addCubeDefinition();
    component.addCubeDefinition();
    component.selectCubeDefinition(0);
    expect(component.activeCubeIndex()).toBe(0);
  });

  it('should update cube definition', () => {
    component.addCubeDefinition();
    component.updateCubeDefinition(0, { name: 'Updated Cube' });
    expect(component.cubeDefinitions()[0].name).toBe('Updated Cube');
  });

  it('should get role label', () => {
    expect(component.getRoleLabel('dimension')).toBe('Dimension');
    expect(component.getRoleLabel('measure')).toBe('Measure');
    expect(component.getRoleLabel('attribute')).toBe('Attribute');
    expect(component.getRoleLabel('ignore')).toBe('Ignored');
    expect(component.getRoleLabel('unknown')).toBe('unknown');
  });

  it('should get license label', () => {
    component.metadata.update(m => ({ ...m, license: 'https://creativecommons.org/publicdomain/zero/1.0/' }));
    expect(component.getLicenseLabel()).toContain('Public Domain');
  });

  it('should open new dimension dialog', () => {
    component.openNewDimensionDialog('testColumn');
    expect(component.showNewDimensionDialog()).toBeTrue();
    expect(component.newDimension().name).toBe('testColumn');
  });

  it('should update new dimension helpers', () => {
    component.updateNewDimensionName('TestDim');
    expect(component.newDimension().name).toBe('TestDim');

    component.updateNewDimensionType('TEMPORAL');
    expect(component.newDimension().type).toBe('TEMPORAL');

    component.updateNewDimensionUri('http://example.org/dim');
    expect(component.newDimension().uri).toBe('http://example.org/dim');

    component.updateNewDimensionDescription('Test description');
    expect(component.newDimension().description).toBe('Test description');
  });

  it('should update publish options helpers', () => {
    component.updatePublishTriplestore('GRAPHDB');
    expect(component.publishOptions().triplestore).toBe('GRAPHDB');

    component.updatePublishGraphUri('http://example.org/graph');
    expect(component.publishOptions().graphUri).toBe('http://example.org/graph');

    component.updatePublishCreateNamedGraph(false);
    expect(component.publishOptions().createNamedGraph).toBeFalse();

    component.updatePublishSavePipeline(false);
    expect(component.publishOptions().savePipeline).toBeFalse();

    component.updatePublishPipelineName('Test Pipeline');
    expect(component.publishOptions().pipelineName).toBe('Test Pipeline');

    component.updatePublishRunImmediately(true);
    expect(component.publishOptions().runImmediately).toBeTrue();
  });

  it('should update more metadata helpers', () => {
    component.updateMetadataLicense('https://opensource.org/licenses/MIT');
    expect(component.metadata().license).toBe('https://opensource.org/licenses/MIT');

    component.updateMetadataIssued('2024-01-01');
    expect(component.metadata().issued).toBe('2024-01-01');

    component.updateMetadataAccrualPeriodicity('http://purl.org/cld/freq/monthly');
    expect(component.metadata().accrualPeriodicity).toBe('http://purl.org/cld/freq/monthly');

    component.updateMetadataSpatial('Switzerland');
    expect(component.metadata().spatial).toBe('Switzerland');

    component.updateMetadataTemporal('2020-2024');
    expect(component.metadata().temporal).toBe('2020-2024');
  });

  it('should generate DSD preview', () => {
    component.cubeName.set('Test Cube');
    component.baseUri.set('http://example.org/');
    component.metadata.update(m => ({ ...m, title: 'Test Cube' }));
    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true },
      { name: 'value', sourceType: 'decimal', role: 'measure', datatype: 'xsd:decimal', required: true }
    ]);
    const dsd = component.generateDsdPreview();
    expect(dsd).toContain('qb:DataStructureDefinition');
    expect(dsd).toContain('qb:dimension');
    expect(dsd).toContain('qb:measure');
  });

  it('should generate Turtle preview', () => {
    component.cubeName.set('Test Cube');
    component.baseUri.set('http://example.org/');
    component.metadata.update(m => ({ ...m, title: 'Test Cube' }));
    const turtle = component.generateTurtlePreview();
    expect(turtle).toContain('qb:DataSet');
    expect(turtle).toContain('qb:Observation');
  });

  it('should generate pipeline preview', () => {
    component.cubeName.set('Test Cube');
    component.baseUri.set('http://example.org/');
    component.selectedDataSource.set(mockDataSources[0]);
    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true }
    ]);
    const pipeline = component.generatePipelinePreview();
    expect(pipeline).toContain('load-csv');
    expect(pipeline).toContain('map-to-rdf');
  });

  it('should run validation', fakeAsync(() => {
    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true },
      { name: 'value', sourceType: 'decimal', role: 'measure', datatype: 'xsd:decimal', required: true }
    ]);
    component.metadata.update(m => ({
      ...m,
      title: 'Test',
      license: 'https://example.org/license',
      publisher: 'Test Publisher'
    }));

    component.runValidation();
    expect(component.validating()).toBeTrue();

    // Run through all validation checks
    tick(3000);

    expect(component.validating()).toBeFalse();
    expect(component.validationComplete()).toBeTrue();
  }));

  it('should calculate validation summary', () => {
    component.validationChecks.set([
      { id: '1', name: 'Test1', description: '', status: 'passed' },
      { id: '2', name: 'Test2', description: '', status: 'passed' },
      { id: '3', name: 'Test3', description: '', status: 'failed' },
      { id: '4', name: 'Test4', description: '', status: 'warning' }
    ]);
    const summary = component.validationSummary();
    expect(summary.passed).toBe(2);
    expect(summary.failed).toBe(1);
    expect(summary.warnings).toBe(1);
    expect(summary.total).toBe(4);
  });

  it('should set all unmapped columns to a role', () => {
    component.columnMappings.set([
      { name: 'col1', sourceType: 'string', role: 'ignore', datatype: 'xsd:string', required: false },
      { name: 'col2', sourceType: 'string', role: 'ignore', datatype: 'xsd:string', required: false },
      { name: 'col3', sourceType: 'string', role: 'dimension', datatype: 'xsd:string', required: true }
    ]);
    component.setAllUnmappedToRole('measure');
    const mappings = component.columnMappings();
    expect(mappings.filter(m => m.role === 'measure').length).toBe(2);
    expect(mappings.find(m => m.name === 'col3')?.role).toBe('dimension');
  });

  it('should navigate to edit generated shape', () => {
    spyOn((component as any).router, 'navigate');
    component.generatedShapeId.set('shape-123');
    component.editGeneratedShape();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/shacl', 'shape-123']);
  });

  it('should navigate to edit generated pipeline', () => {
    spyOn((component as any).router, 'navigate');
    component.generatedPipelineId.set('pipeline-123');
    component.editGeneratedPipeline();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines', 'pipeline-123']);
  });

  it('should copy to clipboard', () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.copyToClipboard('test text');
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('test text');
  });

  it('should handle empty column mappings initialization', () => {
    component.initializeColumnMappings([]);
    expect(component.columnMappings().length).toBe(0);
  });

  it('should detect value column as measure', () => {
    component.initializeColumnMappings([
      { name: 'amount', type: 'decimal', nullable: false, nullCount: 0, uniqueCount: 100, sampleValues: [] }
    ]);
    expect(component.columnMappings()[0].role).toBe('measure');
  });

  it('should detect note column as attribute', () => {
    component.initializeColumnMappings([
      { name: 'description_note', type: 'string', nullable: true, nullCount: 0, uniqueCount: 100, sampleValues: [] }
    ]);
    // 'desc' in name triggers attribute role
    expect(component.columnMappings()[0].role).toBe('attribute');
  });

  it('should get upload options', () => {
    component.delimiter.set(';');
    component.encoding.set('UTF-8');
    component.hasHeader.set(true);
    const opts = component.uploadOptions;
    expect(opts.delimiter).toBe(';');
    expect(opts.encoding).toBe('UTF-8');
    expect(opts.hasHeader).toBeTrue();
  });

  it('should check if can proceed from step 2', () => {
    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true },
      { name: 'value', sourceType: 'decimal', role: 'measure', datatype: 'xsd:decimal', required: true }
    ]);
    expect(component.canProceed(2)).toBeTrue();

    component.columnMappings.set([
      { name: 'year', sourceType: 'integer', role: 'dimension', datatype: 'xsd:integer', required: true }
    ]);
    expect(component.canProceed(2)).toBeFalse(); // No measure
  });

  it('should check if can proceed from step 3', () => {
    component.metadata.update(m => ({ ...m, title: 'Test', license: '' }));
    expect(component.canProceed(3)).toBeFalse();

    component.metadata.update(m => ({ ...m, title: 'Test', license: 'http://example.org/license' }));
    expect(component.canProceed(3)).toBeTrue();
  });
});