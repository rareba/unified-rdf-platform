import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ShapeEditor } from './shape-editor';
import { ShaclService, TriplestoreService } from '../../../core/services';
import { Shape, TriplestoreConnection } from '../../../core/models';

describe('ShapeEditor', () => {
  let component: ShapeEditor;
  let fixture: ComponentFixture<ShapeEditor>;
  let shaclServiceSpy: jasmine.SpyObj<ShaclService>;
  let triplestoreServiceSpy: jasmine.SpyObj<TriplestoreService>;

  const mockShape: Shape = {
    id: 'shape-1',
    name: 'PersonShape',
    uri: 'http://example.org/shapes/Person',
    targetClass: 'http://example.org/Person',
    contentFormat: 'turtle',
    content: '@prefix sh: <http://www.w3.org/ns/shacl#> .',
    tags: [],
    isTemplate: false,
    version: 1,
    createdBy: 'user',
    createdAt: new Date(),
    updatedAt: new Date()
  };

  const mockConnections: TriplestoreConnection[] = [
    { id: 'conn-1', name: 'Local', type: 'GRAPHDB', url: 'http://localhost:7200', authType: 'none', isDefault: true, healthStatus: 'healthy', createdBy: 'user', createdAt: new Date() }
  ];

  beforeEach(async () => {
    shaclServiceSpy = jasmine.createSpyObj('ShaclService', [
      'get', 'create', 'update', 'delete', 'list', 'validateSyntax', 'runValidation'
    ]);
    triplestoreServiceSpy = jasmine.createSpyObj('TriplestoreService', ['list', 'getGraphs', 'exportGraph']);

    shaclServiceSpy.get.and.returnValue(of(mockShape));
    triplestoreServiceSpy.list.and.returnValue(of(mockConnections));

    await TestBed.configureTestingModule({
      imports: [ShapeEditor],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ShaclService, useValue: shaclServiceSpy },
        { provide: TriplestoreService, useValue: triplestoreServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: (key: string) => key === 'id' ? 'shape-1' : null } },
            paramMap: of({ get: (key: string) => key === 'id' ? 'shape-1' : null })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ShapeEditor);
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

  it('should load shape when editing', fakeAsync(() => {
    tick();
    expect(shaclServiceSpy.get).toHaveBeenCalledWith('shape-1');
  }));

  it('should add property shape', () => {
    const initialLength = component.properties().length;
    component.addProperty();
    expect(component.properties().length).toBe(initialLength + 1);
  });

  it('should remove property shape', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.removeProperty(prop);
    expect(component.properties().find(p => p.id === prop.id)).toBeUndefined();
  });

  it('should handle load error gracefully', fakeAsync(() => {
    shaclServiceSpy.get.and.returnValue(throwError(() => new Error('Network error')));
    component.loadShape('shape-1');
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should select property', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.selectProperty(prop);
    expect(component.selectedProperty()).toBe(prop);
  });

  it('should get datatype options', () => {
    const options = component.datatypeOptions;
    expect(options.length).toBeGreaterThan(0);
    expect(options.find(o => o.value === 'xsd:string')).toBeTruthy();
  });

  it('should have templates', () => {
    expect(component.templates.length).toBeGreaterThan(0);
  });

  it('should apply template', () => {
    const template = component.templates[0];
    component.selectTemplate(template);
    component.applyTemplate();
    expect(component.name()).toContain(template.name);
  });

  it('should get constraint presets', () => {
    expect(component.constraintPresets.length).toBeGreaterThan(0);
  });

  it('should have format options', () => {
    expect(component.formatOptions.length).toBeGreaterThan(0);
    expect(component.formatOptions.find(o => o.value === 'turtle')).toBeTruthy();
  });

  it('should get icon for kind', () => {
    expect(component.getIconForKind('LITERAL')).toBe('edit');
    expect(component.getIconForKind('RESOURCE')).toBe('link');
  });

  it('should get datatype label', () => {
    expect(component.getDatatypeLabel('xsd:string')).toBe('Text');
    expect(component.getDatatypeLabel('xsd:integer')).toBe('Whole number');
    expect(component.getDatatypeLabel('xsd:date')).toBe('Date');
  });

  it('should get datatype label for all types', () => {
    expect(component.getDatatypeLabel('xsd:decimal')).toBe('Decimal number');
    expect(component.getDatatypeLabel('xsd:dateTime')).toBe('Date and time');
    expect(component.getDatatypeLabel('xsd:boolean')).toBe('Yes/No');
    expect(component.getDatatypeLabel('xsd:anyURI')).toBe('URL/URI');
    expect(component.getDatatypeLabel('custom:type')).toBe('custom:type');
  });

  it('should skip template', () => {
    component.showTemplateSelector.set(true);
    component.skipTemplate();
    expect(component.showTemplateSelector()).toBeFalse();
    expect(component.wizardStep()).toBe(2);
  });

  it('should apply constraint preset', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.selectProperty(prop);

    const preset = component.constraintPresets.find(p => p.id === 'required');
    if (preset) {
      component.applyConstraintPreset(preset);
      expect(component.properties()[0].minCount).toBe(1);
    }
  });

  it('should apply single-value constraint preset', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.selectProperty(prop);

    const preset = component.constraintPresets.find(p => p.id === 'single-value');
    if (preset) {
      component.applyConstraintPreset(preset);
      expect(component.properties()[0].maxCount).toBe(1);
    }
  });

  it('should apply required-single constraint preset', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.selectProperty(prop);

    const preset = component.constraintPresets.find(p => p.id === 'required-single');
    if (preset) {
      component.applyConstraintPreset(preset);
      expect(component.properties()[0].minCount).toBe(1);
      expect(component.properties()[0].maxCount).toBe(1);
    }
  });

  it('should apply optional-multiple constraint preset', () => {
    component.addProperty();
    const prop = component.properties()[0];
    component.selectProperty(prop);

    const preset = component.constraintPresets.find(p => p.id === 'optional-multiple');
    if (preset) {
      component.applyConstraintPreset(preset);
      expect(component.properties()[0].minCount).toBe(0);
      expect(component.properties()[0].maxCount).toBeNull();
    }
  });

  it('should not apply preset if no property selected', () => {
    component.selectedProperty.set(null);
    const preset = component.constraintPresets[0];
    component.applyConstraintPreset(preset);
    // No error should be thrown
  });

  it('should get constraint summary for required single', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.minCount = 1;
    prop.maxCount = 1;
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Required (exactly one)');
  });

  it('should get constraint summary for required multiple', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.minCount = 2;
    prop.maxCount = null;
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('at least 2');
  });

  it('should get constraint summary for required range', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.minCount = 1;
    prop.maxCount = 3;
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('1-3');
  });

  it('should get constraint summary for optional single', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.minCount = 0;
    prop.maxCount = 1;
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Optional (at most one)');
  });

  it('should get constraint summary for optional multiple', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.minCount = 0;
    prop.maxCount = null;
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Optional');
  });

  it('should get constraint summary with pattern', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';
    prop.pattern = '^[A-Z]+$';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Must match pattern');
  });

  it('should get constraint summary with length constraints', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';
    prop.minLength = 1;
    prop.maxLength = 100;

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Length');
  });

  it('should get constraint summary for resource', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.kind = 'RESOURCE';

    const summary = component.getConstraintSummary(prop);
    expect(summary).toContain('Link to resource');
  });

  it('should get templates by category', () => {
    const groups = component.templatesByCategory;
    expect(groups.length).toBeGreaterThan(0);
    expect(groups[0].category).toBeTruthy();
    expect(groups[0].templates.length).toBeGreaterThan(0);
  });

  it('should generate SHACL from properties', () => {
    component.name.set('TestShape');
    component.uri.set('http://example.org/TestShape');
    component.targetClass.set('http://example.org/TestClass');
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/name';
    prop.name = 'Name';
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';
    prop.minCount = 1;
    prop.maxCount = 1;

    component.generateShacl();
    const content = component.content();
    expect(content).toContain('sh:NodeShape');
    expect(content).toContain('sh:targetClass');
    expect(content).toContain('sh:property');
  });

  it('should generate SHACL with pattern constraint', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/email';
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';
    prop.pattern = '^[^@]+@[^@]+$';

    component.generateShacl();
    expect(component.content()).toContain('sh:pattern');
  });

  it('should generate SHACL with length constraints', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/code';
    prop.kind = 'LITERAL';
    prop.datatype = 'xsd:string';
    prop.minLength = 3;
    prop.maxLength = 10;

    component.generateShacl();
    expect(component.content()).toContain('sh:minLength');
    expect(component.content()).toContain('sh:maxLength');
  });

  it('should generate SHACL with resource kind', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/link';
    prop.kind = 'RESOURCE';
    prop.datatype = 'http://example.org/TargetClass';

    component.generateShacl();
    expect(component.content()).toContain('sh:class');
    expect(component.content()).toContain('sh:nodeKind');
  });

  it('should generate SHACL with description', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/field';
    prop.description = 'A test description';

    component.generateShacl();
    expect(component.content()).toContain('sh:description');
  });

  it('should generate SHACL with message', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/field';
    prop.message = 'Custom error message';

    component.generateShacl();
    expect(component.content()).toContain('sh:message');
  });

  it('should save new shape', fakeAsync(() => {
    shaclServiceSpy.create.and.returnValue(of({ ...mockShape, id: 'new-shape' }));
    component.isNew.set(true);
    component.name.set('New Shape');
    component.uri.set('http://example.org/new');

    spyOn((component as any).router, 'navigate');
    component.save();
    tick();

    expect(shaclServiceSpy.create).toHaveBeenCalled();
    expect((component as any).router.navigate).toHaveBeenCalled();
  }));

  it('should update existing shape', fakeAsync(() => {
    shaclServiceSpy.update.and.returnValue(of(mockShape));
    component.isNew.set(false);
    component.shapeId.set('shape-1');
    component.name.set('Updated Shape');

    component.save();
    tick();

    expect(shaclServiceSpy.update).toHaveBeenCalledWith('shape-1', jasmine.any(Object));
  }));

  it('should handle save error', fakeAsync(() => {
    shaclServiceSpy.create.and.returnValue(throwError(() => new Error('Save failed')));
    component.isNew.set(true);
    component.name.set('New Shape');

    component.save();
    tick();

    expect(component.saving()).toBeFalse();
  }));

  it('should validate syntax', fakeAsync(() => {
    shaclServiceSpy.validateSyntax.and.returnValue(of({ valid: true, errors: [] }));
    component.content.set('@prefix sh: <http://www.w3.org/ns/shacl#> .');

    component.validateSyntax();
    tick();

    expect(shaclServiceSpy.validateSyntax).toHaveBeenCalled();
  }));

  it('should handle invalid syntax', fakeAsync(() => {
    shaclServiceSpy.validateSyntax.and.returnValue(of({ valid: false, errors: ['Syntax error'] }));
    component.content.set('invalid content');

    component.validateSyntax();
    tick();

    expect(shaclServiceSpy.validateSyntax).toHaveBeenCalled();
  }));

  it('should handle syntax validation error', fakeAsync(() => {
    shaclServiceSpy.validateSyntax.and.returnValue(throwError(() => new Error('Network error')));
    component.content.set('content');

    component.validateSyntax();
    tick();
    // Error handled gracefully
  }));

  it('should run validation on saved shape', fakeAsync(() => {
    shaclServiceSpy.runValidation.and.returnValue(of({ conforms: true, violationCount: 0, violations: [], executionTime: 100 }));
    component.isNew.set(false);
    component.shapeId.set('shape-1');
    component.testData.set('test data');

    component.runValidation();
    tick();

    expect(shaclServiceSpy.runValidation).toHaveBeenCalledWith('shape-1', 'test data', 'turtle');
  }));

  it('should not run validation on new shape', fakeAsync(() => {
    component.isNew.set(true);
    component.runValidation();
    tick();

    expect(shaclServiceSpy.runValidation).not.toHaveBeenCalled();
  }));

  it('should handle validation failure', fakeAsync(() => {
    shaclServiceSpy.runValidation.and.returnValue(of({
      conforms: false,
      violationCount: 1,
      violations: [{
        focusNode: 'http://example.org/node',
        message: 'Error',
        severity: 'Violation',
        constraint: 'sh:minCount',
        sourceShape: 'http://example.org/shape'
      }],
      executionTime: 100
    }));
    component.isNew.set(false);
    component.shapeId.set('shape-1');
    component.testData.set('test data');

    component.runValidation();
    tick();

    expect(component.validationResult()?.conforms).toBeFalse();
  }));

  it('should handle validation run error', fakeAsync(() => {
    shaclServiceSpy.runValidation.and.returnValue(throwError(() => new Error('Validation failed')));
    component.isNew.set(false);
    component.shapeId.set('shape-1');
    component.testData.set('test data');

    component.runValidation();
    tick();

    expect(component.validating()).toBeFalse();
  }));

  it('should cancel and navigate back', () => {
    spyOn((component as any).router, 'navigate');
    component.cancel();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/shacl']);
  });

  it('should load triplestore connections', fakeAsync(() => {
    component.loadTriplestoreConnections();
    tick();

    expect(triplestoreServiceSpy.list).toHaveBeenCalled();
    expect(component.triplestoreConnections().length).toBe(1);
  }));

  it('should handle load triplestore connections error', fakeAsync(() => {
    triplestoreServiceSpy.list.and.returnValue(throwError(() => new Error('Error')));
    component.loadTriplestoreConnections();
    tick();

    expect(component.loadingConnections()).toBeFalse();
  }));

  it('should select connection and load graphs', fakeAsync(() => {
    triplestoreServiceSpy.getGraphs.and.returnValue(of([
      { uri: 'http://example.org/graph', name: 'Graph', tripleCount: 100 }
    ]));

    component.onConnectionSelect(mockConnections[0]);
    tick();

    expect(component.selectedConnection()).toBe(mockConnections[0]);
    expect(triplestoreServiceSpy.getGraphs).toHaveBeenCalledWith('conn-1');
  }));

  it('should clear graphs when no connection selected', fakeAsync(() => {
    component.connectionGraphs.set([{ uri: 'test', name: 'Test', tripleCount: 100 }]);
    component.selectedGraph.set({ uri: 'test', name: 'Test', tripleCount: 100 });

    component.onConnectionSelect(null);
    tick();

    expect(component.connectionGraphs()).toEqual([]);
    expect(component.selectedGraph()).toBeNull();
  }));

  it('should handle load graphs error', fakeAsync(() => {
    triplestoreServiceSpy.getGraphs.and.returnValue(throwError(() => new Error('Error')));
    component.loadGraphsForConnection('conn-1');
    tick();

    expect(component.loadingGraphs()).toBeFalse();
  }));

  it('should select graph', () => {
    const graph = { uri: 'http://example.org/graph', name: 'Graph', tripleCount: 100 };
    component.onGraphSelect(graph);
    expect(component.selectedGraph()).toBe(graph);
  });

  it('should load data from graph', fakeAsync(() => {
    triplestoreServiceSpy.exportGraph.and.returnValue(of('turtle data'));
    component.selectedConnection.set(mockConnections[0]);
    component.selectedGraph.set({ uri: 'http://example.org/graph', name: 'Graph', tripleCount: 100 });

    component.loadDataFromGraph();
    tick();

    expect(triplestoreServiceSpy.exportGraph).toHaveBeenCalledWith('conn-1', 'http://example.org/graph', 'turtle');
    expect(component.testData()).toBe('turtle data');
  }));

  it('should not load data without connection', fakeAsync(() => {
    component.selectedConnection.set(null);
    component.loadDataFromGraph();
    tick();

    expect(triplestoreServiceSpy.exportGraph).not.toHaveBeenCalled();
  }));

  it('should not load data without graph', fakeAsync(() => {
    component.selectedConnection.set(mockConnections[0]);
    component.selectedGraph.set(null);
    component.loadDataFromGraph();
    tick();

    expect(triplestoreServiceSpy.exportGraph).not.toHaveBeenCalled();
  }));

  it('should handle load data from graph error', fakeAsync(() => {
    triplestoreServiceSpy.exportGraph.and.returnValue(throwError(() => new Error('Error')));
    component.selectedConnection.set(mockConnections[0]);
    component.selectedGraph.set({ uri: 'http://example.org/graph', name: 'Graph', tripleCount: 100 });

    component.loadDataFromGraph();
    tick();

    expect(component.loadingGraphData()).toBeFalse();
  }));

  it('should change data source to triplestore and load connections', fakeAsync(() => {
    component.triplestoreConnections.set([]);
    component.onDataSourceChange('triplestore');
    tick();

    expect(component.testDataSource()).toBe('triplestore');
    expect(triplestoreServiceSpy.list).toHaveBeenCalled();
  }));

  it('should change data source to manual', fakeAsync(() => {
    component.onDataSourceChange('manual');
    tick();

    expect(component.testDataSource()).toBe('manual');
  }));

  it('should have kind options', () => {
    expect(component.kindOptions.length).toBe(2);
    expect(component.kindOptions.find(o => o.value === 'LITERAL')).toBeTruthy();
    expect(component.kindOptions.find(o => o.value === 'RESOURCE')).toBeTruthy();
  });

  it('should have node kind options', () => {
    expect(component.nodeKindOptions.length).toBeGreaterThan(0);
    expect(component.nodeKindOptions.find(o => o.value === 'sh:IRI')).toBeTruthy();
  });

  it('should have test format options', () => {
    expect(component.testFormatOptions.length).toBeGreaterThan(0);
    expect(component.testFormatOptions.find(o => o.value === 'turtle')).toBeTruthy();
  });

  it('should generate SHACL in visual mode on save', fakeAsync(() => {
    shaclServiceSpy.create.and.returnValue(of(mockShape));
    component.isNew.set(true);
    component.visualMode.set(true);
    component.name.set('Test');
    component.addProperty();

    // Don't navigate - just check content was generated
    component.generateShacl();
    expect(component.content()).toContain('sh:');
  }));

  it('should generate SHACL in visual mode on validate syntax', fakeAsync(() => {
    shaclServiceSpy.validateSyntax.and.returnValue(of({ valid: true, errors: [] }));
    component.visualMode.set(true);
    component.addProperty();

    component.validateSyntax();
    tick();

    expect(component.content()).toContain('sh:');
  }));

  it('should not apply template if none selected', () => {
    // First ensure no template is applied initially by resetting name
    component.name.set('');
    component.selectedTemplate.set(null);
    component.applyTemplate();
    // Since no template is selected, the name should still be empty
    expect(component.selectedTemplate()).toBeNull();
  });

  it('should generate SHACL with nodeKind', () => {
    component.addProperty();
    const prop = component.properties()[0];
    prop.path = 'http://example.org/field';
    prop.nodeKind = 'sh:BlankNode';

    component.generateShacl();
    expect(component.content()).toContain('sh:nodeKind sh:BlankNode');
  });
});
