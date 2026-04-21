import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { LineageGraphComponent } from './lineage-graph';
import { LineageService } from '../../core/services/lineage.service';
import { LineageGraph } from '../../core/models/lineage.model';

describe('LineageGraphComponent', () => {
  let fixture: ComponentFixture<LineageGraphComponent>;
  let component: LineageGraphComponent;
  let svcSpy: jasmine.SpyObj<LineageService>;

  const mockGraph: LineageGraph = {
    projectId: 'p1',
    nodes: [
      { id: 'uuid:project-p1', kind: 'PROJECT', label: 'My Project' },
      { id: 'uuid:mapping-m1', kind: 'MAPPING', label: 'csv-to-rdf' },
      { id: 'uuid:data-d1', kind: 'DATA_SOURCE', label: 'sales.csv' }
    ],
    edges: [
      { from: 'uuid:mapping-m1', to: 'uuid:project-p1', kind: 'BELONGS_TO' },
      { from: 'uuid:mapping-m1', to: 'uuid:data-d1', kind: 'USED_BY' }
    ]
  };

  beforeEach(async () => {
    svcSpy = jasmine.createSpyObj('LineageService', ['forProject', 'forResource']);
    svcSpy.forProject.and.returnValue(of(mockGraph));

    await TestBed.configureTestingModule({
      imports: [LineageGraphComponent],
      providers: [
        provideNoopAnimations(),
        { provide: LineageService, useValue: svcSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LineageGraphComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
  });

  it('loads the graph on projectId input', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    expect(svcSpy.forProject).toHaveBeenCalledWith('p1');
    expect(component.graph()?.nodes.length).toBe(3);
  }));

  it('positions nodes within a positive canvas', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    const positioned = component.positionedNodes();
    expect(positioned.length).toBe(3);
    for (const n of positioned) {
      expect(n.x).toBeGreaterThan(0);
      expect(n.y).toBeGreaterThan(0);
    }
  }));

  it('focus toggles the focused id', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    component.focus({ id: 'uuid:mapping-m1', kind: 'MAPPING', label: 'csv-to-rdf' });
    expect(component.focusedId()).toBe('uuid:mapping-m1');
    // Second click on same node clears focus.
    component.focus({ id: 'uuid:mapping-m1', kind: 'MAPPING', label: 'csv-to-rdf' });
    expect(component.focusedId()).toBeNull();
  }));

  it('visibleEdges filters to focused neighbourhood', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    component.focus({ id: 'uuid:data-d1', kind: 'DATA_SOURCE', label: 'sales.csv' });
    const edges = component.visibleEdges();
    expect(edges.length).toBe(1);
    expect(edges[0].kind).toBe('USED_BY');
  }));

  it('edgeCoords returns null when endpoint is missing', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    const c = component.edgeCoords({ from: 'bogus', to: 'uuid:project-p1', kind: 'USED_BY' });
    expect(c).toBeNull();
  }));

  it('truncate shortens long labels', () => {
    expect(component.truncate('short')).toBe('short');
    expect(component.truncate('this-is-a-very-long-label')).toContain('…');
  });
});
