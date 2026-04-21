import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OntologyList } from './ontology-list';
import { OntologyService } from '../../core/services/ontology.service';
import { Ontology } from '../../core/models';

describe('OntologyList', () => {
  let component: OntologyList;
  let fixture: ComponentFixture<OntologyList>;
  let ontologyServiceSpy: jasmine.SpyObj<OntologyService>;

  const mockOntology: Ontology = {
    id: 'ont-1',
    projectId: 'proj-1',
    name: 'Person Ontology',
    description: 'A simple ontology',
    namespace: 'http://example.org/schema/',
    prefix: 'ex',
    format: 'TURTLE',
    version: 1,
    createdBy: 'user-1',
    createdAt: new Date().toISOString(),
    metadata: { tripleCount: 42, classCount: 2, propertyCount: 3 }
  };

  beforeEach(async () => {
    ontologyServiceSpy = jasmine.createSpyObj('OntologyService', [
      'list', 'get', 'import', 'delete'
    ]);
    ontologyServiceSpy.list.and.returnValue(of([mockOntology]));
    ontologyServiceSpy.delete.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [OntologyList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: OntologyService, useValue: ontologyServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OntologyList);
    component = fixture.componentInstance;
  });

  it('creates', () => {
    expect(component).toBeTruthy();
  });

  it('renders ontologies for the configured project', () => {
    component.setProjectId('proj-1');
    fixture.detectChanges();

    expect(ontologyServiceSpy.list).toHaveBeenCalledWith('proj-1');
    expect(component.ontologies().length).toBe(1);
    expect(component.ontologies()[0].name).toBe('Person Ontology');
  });

  it('exposes triple / class / property counts from metadata', () => {
    component.setProjectId('proj-1');
    fixture.detectChanges();
    const o = component.ontologies()[0];
    expect(component.tripleCount(o)).toBe('42');
    expect(component.classCount(o)).toBe('2');
    expect(component.propCount(o)).toBe('3');
  });

  it('calls delete and reloads on remove()', () => {
    component.setProjectId('proj-1');
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);

    component.remove(mockOntology);

    expect(ontologyServiceSpy.delete).toHaveBeenCalledWith('ont-1');
  });

  it('does not delete when confirm returns false', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.remove(mockOntology);
    expect(ontologyServiceSpy.delete).not.toHaveBeenCalled();
  });
});
