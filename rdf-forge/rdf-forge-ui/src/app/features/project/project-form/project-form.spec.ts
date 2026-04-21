import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ProjectForm } from './project-form';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

describe('ProjectForm', () => {
  let component: ProjectForm;
  let fixture: ComponentFixture<ProjectForm>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  const mockProject: Project = {
    id: 'p1', name: 'Test', description: 'Desc',
    baseUri: 'https://example.org/test/',
    status: 'ACTIVE', createdBy: 'u',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  };

  async function configure(routeId: string | null) {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['get', 'create', 'update']);
    projectServiceSpy.get.and.returnValue(of(mockProject));
    projectServiceSpy.create.and.returnValue(of(mockProject));
    projectServiceSpy.update.and.returnValue(of(mockProject));

    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    const mockSnackBarRef = { onAction: () => new Subject<void>().asObservable() };
    snackBarSpy.open.and.returnValue(mockSnackBarRef as any);

    await TestBed.configureTestingModule({
      imports: [ProjectForm],
      providers: [
        provideNoopAnimations(),
        provideRouter([
          { path: 'projects/:id', children: [] },
          { path: 'projects', children: [] }
        ]),
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) }
          }
        }
      ]
    })
    .overrideComponent(ProjectForm, { remove: { imports: [MatSnackBarModule] } })
    .compileComponents();

    fixture = TestBed.createComponent(ProjectForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('create mode', () => {
    beforeEach(async () => {
      await configure(null);
    });

    it('should create', () => {
      expect(component).toBeTruthy();
      expect(component.isEditMode()).toBeFalse();
    });

    it('should mark form invalid when empty', () => {
      expect(component.form.invalid).toBeTrue();
    });

    it('should reject name over 255 chars', () => {
      component.form.controls.name.setValue('a'.repeat(256));
      component.form.controls.name.markAsTouched();
      expect(component.form.controls.name.valid).toBeFalse();
      expect(component.nameError).toBe('Name must be 255 characters or fewer');
    });

    it('should reject missing name', () => {
      component.form.controls.name.setValue('');
      component.form.controls.name.markAsTouched();
      expect(component.form.controls.name.hasError('required')).toBeTrue();
      expect(component.nameError).toBe('Name is required');
    });

    it('should reject invalid URL for baseUri', () => {
      component.form.controls.baseUri.setValue('not-a-url');
      component.form.controls.baseUri.markAsTouched();
      expect(component.form.controls.baseUri.hasError('invalidUrl')).toBeTrue();
      expect(component.baseUriError).toBe('Must be a valid http(s) URL');
    });

    it('should reject ftp URL for baseUri', () => {
      component.form.controls.baseUri.setValue('ftp://example.org/');
      component.form.controls.baseUri.markAsTouched();
      expect(component.form.controls.baseUri.hasError('invalidUrl')).toBeTrue();
    });

    it('should accept valid https URL', () => {
      component.form.controls.baseUri.setValue('https://example.org/foo/');
      expect(component.form.controls.baseUri.valid).toBeTrue();
    });

    it('should accept valid http URL', () => {
      component.form.controls.baseUri.setValue('http://example.org/foo/');
      expect(component.form.controls.baseUri.valid).toBeTrue();
    });

    it('should call create on save with valid form', fakeAsync(() => {
      component.form.setValue({
        name: 'New Project',
        description: 'About it',
        baseUri: 'https://example.org/new/'
      });
      component.save();
      tick();
      expect(projectServiceSpy.create).toHaveBeenCalled();
      const payload = projectServiceSpy.create.calls.mostRecent().args[0];
      expect(payload.name).toBe('New Project');
      expect(payload.baseUri).toBe('https://example.org/new/');
      expect(payload.description).toBe('About it');
    }));

    it('should not call create when form invalid', () => {
      component.save();
      expect(projectServiceSpy.create).not.toHaveBeenCalled();
    });

    it('should show snackbar on create error', fakeAsync(() => {
      projectServiceSpy.create.and.returnValue(throwError(() => new Error('boom')));
      component.form.setValue({
        name: 'Bad',
        description: '',
        baseUri: 'https://example.org/'
      });
      component.save();
      tick();
      expect(snackBarSpy.open).toHaveBeenCalled();
    }));
  });

  describe('edit mode', () => {
    beforeEach(async () => {
      await configure('p1');
    });

    it('should load project and patch form', fakeAsync(() => {
      tick();
      expect(projectServiceSpy.get).toHaveBeenCalledWith('p1');
      expect(component.isEditMode()).toBeTrue();
      expect(component.form.controls.name.value).toBe('Test');
      expect(component.form.controls.baseUri.value).toBe('https://example.org/test/');
    }));

    it('should call update on save', fakeAsync(() => {
      tick();
      component.form.controls.name.setValue('Renamed');
      component.save();
      tick();
      expect(projectServiceSpy.update).toHaveBeenCalled();
      const [id, payload] = projectServiceSpy.update.calls.mostRecent().args;
      expect(id).toBe('p1');
      expect(payload.name).toBe('Renamed');
    }));

    it('should navigate back to project after save', fakeAsync(() => {
      tick();
      const router = TestBed.inject(Router);
      spyOn(router, 'navigate');
      component.save();
      tick();
      expect(router.navigate).toHaveBeenCalledWith(['/projects', 'p1']);
    }));
  });
});
