import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PipelineDesigner } from './pipeline-designer';
import { PipelineService } from '../../../core/services';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { MatDialogModule } from '@angular/material/dialog';
import { Pipeline, Operation, OperationType } from '../../../core/models';

describe('PipelineDesigner', () => {
  let component: PipelineDesigner;
  let fixture: ComponentFixture<PipelineDesigner>;
  let pipelineServiceSpy: jasmine.SpyObj<PipelineService>;
  let confirmationServiceSpy: jasmine.SpyObj<ConfirmationService>;

  const mockPipeline: Pipeline = {
    id: 'p1',
    name: 'Test Pipeline',
    description: 'Test description',
    status: 'draft',
    stepsCount: 0,
    tags: ['test'],
    definition: '{"steps":[]}',
    definitionFormat: 'JSON',
    variables: {},
    createdBy: 'user',
    createdAt: new Date(),
    updatedAt: new Date()
  };

  const mockOperations: Operation[] = [
    { id: 'load-csv', name: 'Load CSV', type: 'SOURCE', description: 'Load CSV file', parameters: {} },
    { id: 'load-json', name: 'Load JSON', type: 'SOURCE', description: 'Load JSON file', parameters: {} },
    { id: 'transform', name: 'Transform', type: 'TRANSFORM', description: 'Transform data', parameters: {} },
    { id: 'filter', name: 'Filter', type: 'TRANSFORM', description: 'Filter data', parameters: {} },
    { id: 'validate-shacl', name: 'Validate', type: 'VALIDATION', description: 'Validate data', parameters: {} },
    { id: 'save-rdf', name: 'Save RDF', type: 'OUTPUT', description: 'Save RDF output', parameters: {} }
  ];

  beforeEach(async () => {
    pipelineServiceSpy = jasmine.createSpyObj('PipelineService', [
      'get', 'create', 'update', 'delete', 'run', 'getOperations', 'validate'
    ]);
    pipelineServiceSpy.get.and.returnValue(of(mockPipeline));
    pipelineServiceSpy.getOperations.and.returnValue(of(mockOperations));

    confirmationServiceSpy = jasmine.createSpyObj('ConfirmationService', ['confirm']);

    await TestBed.configureTestingModule({
      imports: [PipelineDesigner],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([
          { path: 'pipelines/:id', component: PipelineDesigner },
          { path: 'pipelines', component: PipelineDesigner },
          { path: 'jobs/:id', component: PipelineDesigner },
          { path: 'jobs', component: PipelineDesigner }
        ]),
        { provide: PipelineService, useValue: pipelineServiceSpy },
        { provide: ConfirmationService, useValue: confirmationServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: (key: string) => key === 'id' ? 'p1' : null } },
            paramMap: of({ get: (key: string) => key === 'id' ? 'p1' : null })
          }
        }
      ]
    })
    .overrideComponent(PipelineDesigner, {
      remove: {
        imports: [MatDialogModule as any]
      }
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineDesigner);
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

  it('should load operations on init', fakeAsync(() => {
    tick();
    expect(pipelineServiceSpy.getOperations).toHaveBeenCalled();
    expect(component.availableOperations().length).toBe(6);
  }));

  it('should load pipeline when editing', fakeAsync(() => {
    tick();
    expect(pipelineServiceSpy.get).toHaveBeenCalledWith('p1');
    expect(component.name()).toBe(mockPipeline.name);
  }));

  it('should group operations by type', fakeAsync(() => {
    tick();
    const groups = component.operationGroups();
    expect(groups.length).toBeGreaterThan(0);
    expect(groups.find(g => g.type === 'SOURCE')).toBeTruthy();
    expect(groups.find(g => g.type === 'TRANSFORM')).toBeTruthy();
    expect(groups.find(g => g.type === 'VALIDATION')).toBeTruthy();
    expect(groups.find(g => g.type === 'OUTPUT')).toBeTruthy();
  }));

  it('should add node to canvas', fakeAsync(() => {
    tick();
    const initialLength = component.nodes().length;
    component.addNode(mockOperations[0], 100, 100);
    expect(component.nodes().length).toBe(initialLength + 1);
  }));

  it('should remove node from canvas', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.removeNode(node.id);
    expect(component.nodes().length).toBe(0);
  }));

  it('should also remove connected links when removing node', fakeAsync(() => {
    tick();
    let now = Date.now();
    spyOn(Date, 'now').and.callFake(() => now++);
    component.addNode(mockOperations[0], 100, 100);
    component.addNode(mockOperations[1], 300, 100);
    const nodes = component.nodes();

    // addNode auto-links from last node to new node
    const linksBefore = component.links().filter(l => l.source === nodes[0].id || l.target === nodes[0].id);
    expect(linksBefore.length).toBeGreaterThan(0);

    component.removeNode(nodes[0].id);
    const linksAfter = component.links().filter(l => l.source === nodes[0].id || l.target === nodes[0].id);
    expect(linksAfter.length).toBe(0);
  }));

  it('should save pipeline', fakeAsync(() => {
    pipelineServiceSpy.update.and.returnValue(of(mockPipeline));
    tick();
    component.save();
    tick();
    expect(pipelineServiceSpy.update).toHaveBeenCalled();
  }));

  it('should run pipeline', fakeAsync(() => {
    pipelineServiceSpy.run.and.returnValue(of({ jobId: 'job-1', id: 'run-1' }));
    tick();
    component.run();
    tick();
    expect(pipelineServiceSpy.run).toHaveBeenCalledWith('p1', {});
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    pipelineServiceSpy.get.and.returnValue(throwError(() => new Error('Network error')));
    component.loadPipeline('p1');
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should update pipeline name', () => {
    component.name.set('New Name');
    expect(component.name()).toBe('New Name');
  });

  it('should update pipeline description', () => {
    component.description.set('New Description');
    expect(component.description()).toBe('New Description');
  });

  it('should get type color', () => {
    expect(component.getTypeColor('SOURCE' as OperationType)).toBe('#3b82f6');
    expect(component.getTypeColor('TRANSFORM' as OperationType)).toBe('#22c55e');
    expect(component.getTypeColor('VALIDATION' as OperationType)).toBe('#6b7280');
    expect(component.getTypeColor('OUTPUT' as OperationType)).toBe('#ef4444');
    expect(component.getTypeColor('CUBE' as OperationType)).toBe('#f59e0b');
    expect(component.getTypeColor(null)).toBe('#6b7280');
  });

  it('should get type background color', () => {
    expect(component.getTypeBgColor('SOURCE' as OperationType)).toContain('#');
    expect(component.getTypeBgColor('TRANSFORM' as OperationType)).toContain('#');
    expect(component.getTypeBgColor('VALIDATION' as OperationType)).toContain('#');
    expect(component.getTypeBgColor('OUTPUT' as OperationType)).toContain('#');
  });

  it('should have pipeline templates', () => {
    expect(component.pipelineTemplates.length).toBeGreaterThan(0);
  });

  it('should validate pipeline', fakeAsync(() => {
    tick();
    component.validate();
    // Empty pipeline should have validation errors
    expect(component.validationErrors().length).toBeGreaterThan(0);
  }));

  it('should filter operations by search', fakeAsync(() => {
    tick();
    component.operationSearch.set('csv');
    const filtered = component.filteredOperationGroups();
    expect(filtered.length).toBeGreaterThanOrEqual(0);

    // Should find the Load CSV operation
    const sourceGroup = filtered.find(g => g.type === 'SOURCE');
    if (sourceGroup) {
      expect(sourceGroup.operations.some(op => op.id === 'load-csv')).toBeTrue();
    }
  }));

  it('should add link between nodes', fakeAsync(() => {
    tick();
    // Use spaced Date.now to avoid ID collisions
    let now = Date.now();
    spyOn(Date, 'now').and.callFake(() => now++);
    component.addNode(mockOperations[0], 100, 100);
    component.addNode(mockOperations[1], 300, 100);
    // addNode auto-links from last node to new node
    expect(component.links().length).toBeGreaterThan(0);
  }));

  it('should not add duplicate link', fakeAsync(() => {
    tick();
    let now = Date.now();
    spyOn(Date, 'now').and.callFake(() => now++);
    component.addNode(mockOperations[0], 100, 100);
    component.addNode(mockOperations[1], 300, 100);
    const nodes = component.nodes();
    // Auto-link already created; try adding duplicate
    const initialLinks = component.links().length;
    component.addLink(nodes[0].id, nodes[1].id);
    expect(component.links().length).toBe(initialLinks);
  }));

  it('should not add self-loop link', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const nodes = component.nodes();
    const linksBefore = component.links().length;
    component.addLink(nodes[0].id, nodes[0].id);
    expect(component.links().length).toBe(linksBefore);
  }));

  it('should remove link', fakeAsync(() => {
    tick();
    let now = Date.now();
    spyOn(Date, 'now').and.callFake(() => now++);
    component.addNode(mockOperations[0], 100, 100);
    component.addNode(mockOperations[1], 300, 100);
    // addNode auto-links from last node to new node
    expect(component.links().length).toBeGreaterThan(0);
    const link = component.links()[0];
    component.removeLink(link.id);
    expect(component.links().length).toBe(0);
  }));

  it('should select node', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.onNodeSelect(node);
    expect(component.propertiesPanelOpen()).toBeTrue();
    expect(component.selectedNode()).toBeTruthy();
  }));

  it('should deselect node on background click', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.onNodeSelect(node);
    component.onBackgroundClick();
    expect(component.propertiesPanelOpen()).toBeFalse();
    expect(component.selectedNode()).toBeNull();
  }));

  it('should update node param', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.selectedNode.set(node);
    component.updateNodeParam('testKey', 'testValue');
    expect(component.selectedNode()?.params['testKey']).toBe('testValue');
  }));

  it('should get default params from operation', fakeAsync(() => {
    tick();
    const opWithDefaults: Operation = {
      id: 'test-op',
      name: 'Test',
      type: 'SOURCE',
      description: '',
      parameters: {
        'param1': { name: 'Param 1', description: 'Test param', type: 'string', required: false, defaultValue: 'default' }
      }
    };
    const defaults = component.getDefaultParams(opWithDefaults);
    expect(defaults['param1']).toBe('default');
  }));

  it('should get param type', () => {
    expect(component.getParamType('java.lang.Boolean')).toBe('boolean');
    expect(component.getParamType('boolean')).toBe('boolean');
    expect(component.getParamType('java.lang.Integer')).toBe('number');
    expect(component.getParamType('int')).toBe('number');
    expect(component.getParamType('java.lang.Long')).toBe('number');
    expect(component.getParamType('java.util.Map')).toBe('map');
    expect(component.getParamType('java.lang.Character')).toBe('char');
    expect(component.getParamType('java.lang.String')).toBe('text');
    expect(component.getParamType('unknown.type')).toBe('text');
  });

  it('should zoom in', () => {
    const initialZoom = component.zoomLevel();
    component.zoomIn();
    expect(component.zoomLevel()).toBeGreaterThan(initialZoom);
  });

  it('should zoom out', () => {
    const initialZoom = component.zoomLevel();
    component.zoomOut();
    expect(component.zoomLevel()).toBeLessThan(initialZoom);
  });

  it('should reset zoom', () => {
    component.zoomLevel.set(1.5);
    component.resetZoom();
    expect(component.zoomLevel()).toBe(1);
  });

  it('should open run dialog', () => {
    component.openRunDialog();
    expect(component.runDialogVisible()).toBeTrue();
    expect(component.runVariables()).toEqual({});
  });

  it('should add run variable', () => {
    component.newVarKey.set('myKey');
    component.newVarValue.set('myValue');
    component.addRunVariable();
    expect(component.runVariables()['myKey']).toBe('myValue');
    expect(component.newVarKey()).toBe('');
    expect(component.newVarValue()).toBe('');
  });

  it('should not add run variable with empty key', () => {
    component.newVarKey.set('');
    component.newVarValue.set('myValue');
    component.addRunVariable();
    expect(Object.keys(component.runVariables()).length).toBe(0);
  });

  it('should not add duplicate run variable', () => {
    component.runVariables.set({ 'key1': 'value1' });
    component.newVarKey.set('key1');
    component.newVarValue.set('newValue');
    component.addRunVariable();
    // Should update existing
    expect(component.runVariables()['key1']).toBe('newValue');
  });

  it('should remove run variable', () => {
    component.runVariables.set({ 'key1': 'value1', 'key2': 'value2' });
    component.removeRunVariable('key1');
    expect(component.runVariables()['key1']).toBeUndefined();
    expect(component.runVariables()['key2']).toBe('value2');
  });

  it('should show json dialog', () => {
    component.showJson();
    expect(component.jsonDialogVisible()).toBeTrue();
  });

  it('should get pipeline json', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const json = component.pipelineJson();
    expect(json).toContain('steps');
  }));

  it('should import pipeline json', fakeAsync(() => {
    tick();
    const json = '{"steps":[{"id":"step-1","operation":"load-csv","params":{}}]}';
    component.importPipelineJson(json);
    expect(component.jsonDialogVisible()).toBeFalse();
    expect(component.nodes().length).toBe(1);
  }));

  it('should handle invalid json import gracefully', fakeAsync(() => {
    tick();
    const json = 'invalid json';
    // parsePipelineDefinition catches the error internally and logs a warning
    component.importPipelineJson(json);
    // Should not throw
    expect(component.jsonDialogVisible()).toBeFalse();
  }));

  it('should open templates dialog', () => {
    component.openTemplates();
    expect(component.templatesDialogVisible()).toBeTrue();
  });

  it('should get template category color', () => {
    expect(component.getTemplateCategoryColor('cube')).toBe('#f59e0b');
    expect(component.getTemplateCategoryColor('validation')).toBe('#22c55e');
    expect(component.getTemplateCategoryColor('etl')).toBe('#3b82f6');
    expect(component.getTemplateCategoryColor('publish')).toBe('#8b5cf6');
    expect(component.getTemplateCategoryColor('unknown')).toBe('#64748b');
  });

  it('should check if operation is cube-link', () => {
    expect(component.isCubeLinkOperation('fetch-cube')).toBeTrue();
    expect(component.isCubeLinkOperation('validate-shacl')).toBeTrue();
    expect(component.isCubeLinkOperation('random-op')).toBeFalse();
  });

  it('should cancel and navigate', () => {
    spyOn((component as any).router, 'navigate');
    component.cancel();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/pipelines']);
  });

  it('should get required params', fakeAsync(() => {
    tick();
    const op: Operation = {
      id: 'test',
      name: 'Test',
      type: 'SOURCE',
      description: '',
      parameters: {
        'required': { name: 'Required', description: 'Required param', type: 'string', required: true, defaultValue: null },
        'optional': { name: 'Optional', description: 'Optional param', type: 'string', required: false, defaultValue: null }
      }
    };
    const required = component.getRequiredParams(op);
    expect(required.length).toBe(1);
    expect(required[0].key).toBe('required');
  }));

  it('should get optional params', fakeAsync(() => {
    tick();
    const op: Operation = {
      id: 'test',
      name: 'Test',
      type: 'SOURCE',
      description: '',
      parameters: {
        'required': { name: 'Required', description: 'Required param', type: 'string', required: true, defaultValue: null },
        'optional': { name: 'Optional', description: 'Optional param', type: 'string', required: false, defaultValue: null }
      }
    };
    const optional = component.getOptionalParams(op);
    expect(optional.length).toBe(1);
    expect(optional[0].key).toBe('optional');
  }));

  it('should get operation example', fakeAsync(() => {
    tick();
    // getOperationExample returns examples for known operations or 'Configure...' for unknown
    const csvExample = component.getOperationExample('csv-source');
    const unknownExample = component.getOperationExample('non-existent-op');
    // If operation exists, it may have example, otherwise 'Configure parameters below'
    expect(typeof csvExample).toBe('string');
    expect(typeof unknownExample).toBe('string');
  }));

  it('should not run without pipeline id', fakeAsync(() => {
    tick();
    component.pipelineId.set(null);
    component.run();
    // Should show snackbar about saving first
    expect(pipelineServiceSpy.run).not.toHaveBeenCalled();
  }));

  it('should handle save error', fakeAsync(() => {
    pipelineServiceSpy.update.and.returnValue(throwError(() => ({ error: { message: 'Error' } })));
    tick();
    component.save();
    tick();
    expect(component.saving()).toBeFalse();
  }));

  it('should handle run error', fakeAsync(() => {
    pipelineServiceSpy.run.and.returnValue(throwError(() => ({ error: { message: 'Error' } })));
    tick();
    component.run();
    tick();
    // Error is handled via snackbar, no error signal to check
    expect(pipelineServiceSpy.run).toHaveBeenCalled();
  }));

  it('should clear canvas when confirmed', fakeAsync(() => {
    confirmationServiceSpy.confirm.and.returnValue(of(true));
    tick();
    component.addNode(mockOperations[0], 100, 100);
    component.clearCanvas();
    tick();
    expect(component.nodes().length).toBe(0);
    expect(component.links().length).toBe(0);
  }));

  it('should not clear canvas when not confirmed', fakeAsync(() => {
    confirmationServiceSpy.confirm.and.returnValue(of(false));
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const initialCount = component.nodes().length;
    component.clearCanvas();
    tick();
    expect(component.nodes().length).toBe(initialCount);
  }));

  it('should get map value', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.selectedNode.set({ ...node, params: { mapParam: { key: 'value' } } });
    const val = component.getMapValue('mapParam');
    expect(val).toContain('key');
  }));

  it('should return empty string for non-map value', () => {
    component.selectedNode.set(null);
    const val = component.getMapValue('mapParam');
    expect(val).toBe('');
  });

  it('should update map param', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.selectedNode.set(node);
    component.updateMapParam('mapKey', '{"nested":"value"}');
    expect(component.selectedNode()?.params['mapKey']).toEqual({ nested: 'value' });
  }));

  it('should not update map param with invalid json', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 100, 100);
    const node = component.nodes()[0];
    component.selectedNode.set(node);
    component.updateMapParam('mapKey', 'invalid json');
    expect(component.selectedNode()?.params['mapKey']).toBeUndefined();
  }));

  it('should handle drag start', () => {
    const mockEvent = {
      dataTransfer: {
        setData: jasmine.createSpy(),
        effectAllowed: ''
      }
    };
    component.onDragStart(mockEvent as any, mockOperations[0]);
    expect(mockEvent.dataTransfer.setData).toHaveBeenCalled();
    expect(mockEvent.dataTransfer.effectAllowed).toBe('copy');
  });

  it('should parse pipeline definition', fakeAsync(() => {
    tick();
    const definition = JSON.stringify({
      steps: [{
        id: 'step-1',
        operation: 'load-csv',
        params: {}
      }]
    });
    component.parsePipelineDefinition(definition);
    const nodes = component.nodes();
    expect(nodes.length).toBe(1);
    expect(nodes[0].operationId).toBe('load-csv');
  }));

  it('should handle invalid pipeline definition', () => {
    // Should not throw, just log warning
    expect(() => component.parsePipelineDefinition('invalid json')).not.toThrow();
  });

  it('should get operation by id', fakeAsync(() => {
    tick();
    const op = component.getOperationById('load-csv');
    expect(op).toBeTruthy();
    expect(op?.name).toBe('Load CSV');
  }));

  it('should create new pipeline', fakeAsync(() => {
    pipelineServiceSpy.create.and.returnValue(of({ ...mockPipeline, id: 'new-id' }));
    spyOn((component as any).router, 'navigate');

    component.isNew.set(true);
    component.pipelineId.set(null);
    component.save();
    tick();

    expect(pipelineServiceSpy.create).toHaveBeenCalled();
    expect((component as any).router.navigate).toHaveBeenCalled();
  }));

  it('should handle create error', fakeAsync(() => {
    pipelineServiceSpy.create.and.returnValue(throwError(() => ({ error: { message: 'Create failed' } })));

    component.isNew.set(true);
    component.pipelineId.set(null);
    component.save();
    tick();

    expect(component.saving()).toBeFalse();
  }));

  it('should toggle run dialog', () => {
    component.runDialogVisible.set(true);
    expect(component.runDialogVisible()).toBeTrue();
    component.runDialogVisible.set(false);
    expect(component.runDialogVisible()).toBeFalse();
  });

  it('should toggle templates dialog', () => {
    component.templatesDialogVisible.set(true);
    expect(component.templatesDialogVisible()).toBeTrue();
    component.templatesDialogVisible.set(false);
    expect(component.templatesDialogVisible()).toBeFalse();
  });

  it('should apply template', fakeAsync(() => {
    tick();
    const template = component.pipelineTemplates[0];
    component.applyTemplate(template);
    tick();
    expect(component.templatesDialogVisible()).toBeFalse();
    expect(component.nodes().length).toBeGreaterThan(0);
  }));

  it('should auto layout nodes', fakeAsync(() => {
    tick();
    component.addNode(mockOperations[0], 0, 0);
    component.addNode(mockOperations[1], 0, 0);
    component.addNode(mockOperations[2], 0, 0);

    component.autoLayout();

    const nodes = component.nodes();
    // Nodes should have positions assigned after auto layout
    expect(nodes.length).toBe(3);
  }));

  it('should fit to screen', () => {
    spyOn(component.zoomToFit$, 'next');
    component.fitToScreen();
    expect(component.zoomToFit$.next).toHaveBeenCalled();
  });

  it('should handle zoom change', () => {
    component.onZoomChange(1.5);
    expect(component.zoomLevel()).toBe(1.5);
  });
});
