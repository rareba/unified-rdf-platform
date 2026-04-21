import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { OntologyImport } from './ontology-import';
import { OntologyService } from '../../core/services/ontology.service';

describe('OntologyImport', () => {
  let component: OntologyImport;
  let fixture: ComponentFixture<OntologyImport>;
  let ontologyServiceSpy: jasmine.SpyObj<OntologyService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<OntologyImport>>;

  beforeEach(async () => {
    ontologyServiceSpy = jasmine.createSpyObj('OntologyService', ['import']);
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [OntologyImport],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: OntologyService, useValue: ontologyServiceSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'proj-1' } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OntologyImport);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates', () => {
    expect(component).toBeTruthy();
  });

  it('has an invalid form on load (name and content required)', () => {
    expect(component.form.valid).toBeFalse();
    expect(component.hasContent()).toBeFalse();
  });

  it('becomes valid when name and content are provided', () => {
    component.form.patchValue({
      name: 'My Ontology',
      content: '@prefix ex: <http://example.org/> .'
    });
    expect(component.form.valid).toBeTrue();
    expect(component.hasContent()).toBeTrue();
  });

  it('submits import request and closes on success', () => {
    const imported = {
      id: 'ont-1',
      projectId: 'proj-1',
      name: 'My Ontology',
      namespace: 'http://example.org/',
      format: 'TURTLE' as const,
      version: 1,
      createdBy: 'user-1',
      createdAt: new Date().toISOString()
    };
    ontologyServiceSpy.import.and.returnValue(of(imported));

    component.form.patchValue({
      name: 'My Ontology',
      namespace: 'http://example.org/',
      content: '@prefix ex: <http://example.org/> .',
      format: 'TURTLE'
    });

    component.submit();

    expect(ontologyServiceSpy.import).toHaveBeenCalled();
    const req = ontologyServiceSpy.import.calls.mostRecent().args[0];
    expect(req.projectId).toBe('proj-1');
    expect(req.name).toBe('My Ontology');
    expect(req.format).toBe('TURTLE');
    expect(dialogRefSpy.close).toHaveBeenCalledWith(imported);
  });

  it('surfaces an error message and does not close on failure', () => {
    ontologyServiceSpy.import.and.returnValue(throwError(() => ({ error: { detail: 'Bad RDF' } })));

    component.form.patchValue({
      name: 'X',
      content: 'not rdf',
      format: 'TURTLE'
    });
    component.submit();

    expect(component.errorMessage()).toBe('Bad RDF');
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });
});
