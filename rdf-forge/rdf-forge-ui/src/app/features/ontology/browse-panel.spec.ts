import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BrowsePanel } from './browse-panel';
import { OntologyService } from '../../core/services/ontology.service';
import { TermDetail, TermResult } from '../../core/models';

describe('BrowsePanel', () => {
  let component: BrowsePanel;
  let fixture: ComponentFixture<BrowsePanel>;
  let ontologyServiceSpy: jasmine.SpyObj<OntologyService>;

  const sampleClass: TermResult = {
    uri: 'http://example.org/schema/Person',
    type: 'CLASS',
    label: 'Person',
    comment: 'A human being.'
  };

  const sampleDetail: TermDetail = {
    uri: 'http://example.org/schema/Person',
    type: 'CLASS',
    label: 'Person',
    types: ['http://www.w3.org/2002/07/owl#Class'],
    comment: 'A human being.'
  };

  beforeEach(async () => {
    ontologyServiceSpy = jasmine.createSpyObj('OntologyService', [
      'classes', 'properties', 'skosConcepts', 'termDetail'
    ]);
    ontologyServiceSpy.classes.and.returnValue(of([sampleClass]));
    ontologyServiceSpy.properties.and.returnValue(of([]));
    ontologyServiceSpy.skosConcepts.and.returnValue(of([]));
    ontologyServiceSpy.termDetail.and.returnValue(of(sampleDetail));

    await TestBed.configureTestingModule({
      imports: [BrowsePanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: OntologyService, useValue: ontologyServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BrowsePanel);
    component = fixture.componentInstance;
    component.ontologyId = 'ont-1';
  });

  it('loads classes on init', () => {
    fixture.detectChanges();
    expect(ontologyServiceSpy.classes).toHaveBeenCalledWith('ont-1', undefined);
    expect(component.terms().length).toBe(1);
  });

  it('switches loader when kind changes', () => {
    fixture.detectChanges();
    component.setKind('properties');
    expect(ontologyServiceSpy.properties).toHaveBeenCalled();
  });

  it('fetches detail when a term is selected', () => {
    fixture.detectChanges();
    component.select(sampleClass);
    expect(ontologyServiceSpy.termDetail).toHaveBeenCalledWith('ont-1', sampleClass.uri);
    expect(component.detail()?.label).toBe('Person');
  });

  it('marks selected term as active', () => {
    fixture.detectChanges();
    component.select(sampleClass);
    expect(component.isActive(sampleClass)).toBeTrue();
  });

  it('shortUri extracts fragment after # or /', () => {
    expect(component.shortUri('http://example.org/schema#Person')).toBe('Person');
    expect(component.shortUri('http://example.org/schema/Person')).toBe('Person');
  });

  it('iconFor returns a material icon per term type', () => {
    expect(component.iconFor({ uri: 'x', type: 'CLASS' })).toBe('class');
    expect(component.iconFor({ uri: 'x', type: 'PROPERTY' })).toBe('tune');
    expect(component.iconFor({ uri: 'x', type: 'SKOS_CONCEPT' })).toBe('bookmark');
  });

  it('debounced search triggers a reload', fakeAsync(() => {
    fixture.detectChanges();
    ontologyServiceSpy.classes.calls.reset();
    const fakeEvent = { target: { value: 'Per' } } as unknown as Event;
    component.onSearchInput(fakeEvent);
    tick(300);
    expect(ontologyServiceSpy.classes).toHaveBeenCalledWith('ont-1', 'Per');
  }));
});
