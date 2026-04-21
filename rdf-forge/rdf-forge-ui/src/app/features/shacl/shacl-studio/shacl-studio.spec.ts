import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ShaclStudioComponent } from './shacl-studio';
import { ShaclService } from '../../../core/services';
import { ErrorHandlerService } from '../../../core/services/error-handler.service';

describe('ShaclStudioComponent', () => {
  let component: ShaclStudioComponent;
  let fixture: ComponentFixture<ShaclStudioComponent>;
  let shaclServiceSpy: jasmine.SpyObj<ShaclService>;
  let errorHandlerSpy: jasmine.SpyObj<ErrorHandlerService>;

  beforeEach(async () => {
    shaclServiceSpy = jasmine.createSpyObj('ShaclService', [
      'validateContent', 'saveShape', 'getProfiles'
    ]);
    shaclServiceSpy.getProfiles.and.returnValue(of([]));
    shaclServiceSpy.validateContent.and.returnValue(of({ conforms: true, results: [] }));

    errorHandlerSpy = jasmine.createSpyObj('ErrorHandlerService', ['handleError']);

    await TestBed.configureTestingModule({
      imports: [ShaclStudioComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ShaclService, useValue: shaclServiceSpy },
        { provide: ErrorHandlerService, useValue: errorHandlerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ShaclStudioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize form with default values', () => {
    expect(component.shapeForm).toBeTruthy();
    expect(component.shapeForm.get('name')).toBeTruthy();
    expect(component.shapeForm.get('content')).toBeTruthy();
    expect(component.shapeForm.get('autoValidate')?.value).toBeTrue();
  });

  it('should not validate empty content', () => {
    component.shapeForm.get('content')?.setValue('');
    component.validateContent();
    expect(shaclServiceSpy.validateContent).not.toHaveBeenCalled();
    expect(component.validationErrors).toEqual([]);
  });

  it('should validate content and show results', fakeAsync(() => {
    const mockResult = { conforms: true, results: [] };
    shaclServiceSpy.validateContent.and.returnValue(of(mockResult));

    component.shapeForm.get('content')?.setValue('@prefix sh: <http://www.w3.org/ns/shacl#> .');
    component.validateContent(true);
    tick();

    expect(shaclServiceSpy.validateContent).toHaveBeenCalled();
    expect(component.validationResult).toEqual(jasmine.objectContaining({ conforms: true }));
    expect(component.validationErrors).toEqual([]);
    expect(component.isValidating).toBeFalse();
  }));

  it('should handle validation errors', fakeAsync(() => {
    const mockResult = {
      conforms: false,
      results: [{ message: 'error', severity: 'Violation', focusNode: 'x', path: 'y' }]
    };
    shaclServiceSpy.validateContent.and.returnValue(of(mockResult));

    component.shapeForm.get('content')?.setValue('@prefix sh: <http://www.w3.org/ns/shacl#> .');
    component.validateContent();
    tick();

    expect(component.validationErrors.length).toBe(1);
  }));

  it('should handle validation service error', fakeAsync(() => {
    shaclServiceSpy.validateContent.and.returnValue(throwError(() => new Error('Service down')));

    component.shapeForm.get('content')?.setValue('@prefix sh: <http://www.w3.org/ns/shacl#> .');
    component.validateContent();
    tick();

    expect(component.isValidating).toBeFalse();
  }));

  it('should load example content', fakeAsync(() => {
    shaclServiceSpy.validateContent.and.returnValue(of({ conforms: true, results: [] }));
    component.loadExample();
    tick();
    const content = component.shapeForm.get('content')?.value;
    expect(content).toBeTruthy();
    expect(content.length).toBeGreaterThan(0);
  }));

  it('should not save invalid form', () => {
    component.shapeForm.get('name')?.setValue('');
    component.shapeForm.get('content')?.setValue('');
    component.saveShape();
    expect(shaclServiceSpy.saveShape).not.toHaveBeenCalled();
  });

  it('should save valid shape', fakeAsync(() => {
    shaclServiceSpy.saveShape.and.returnValue(of({ id: '1', name: 'TestShape' }));

    // Use name without spaces (must match pattern /^[a-zA-Z][a-zA-Z0-9_-]*$/)
    component.shapeForm.get('name')?.setValue('TestShape');
    component.shapeForm.get('content')?.setValue('@prefix sh: <http://www.w3.org/ns/shacl#> .');
    component.saveShape();
    tick();

    expect(shaclServiceSpy.saveShape).toHaveBeenCalled();
  }));
});
