import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ProjectList } from './project-list';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

describe('ProjectList', () => {
  let component: ProjectList;
  let fixture: ComponentFixture<ProjectList>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  const mockProjects: Project[] = [
    {
      id: '1', name: 'Alpha', description: 'First project', baseUri: 'https://example.org/alpha/',
      status: 'ACTIVE', createdBy: 'u', createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-10T00:00:00Z'
    },
    {
      id: '2', name: 'Beta', description: 'Second project', baseUri: 'https://example.org/beta/',
      status: 'ACTIVE', createdBy: 'u', createdAt: '2026-01-02T00:00:00Z',
      updatedAt: '2026-01-11T00:00:00Z'
    },
    {
      id: '3', name: 'Gamma', description: '', baseUri: 'https://example.org/gamma/',
      status: 'ARCHIVED', createdBy: 'u', createdAt: '2025-12-01T00:00:00Z',
      updatedAt: '2025-12-20T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService',
      ['list', 'archive', 'unarchive', 'delete']);
    projectServiceSpy.list.and.returnValue(of(mockProjects));
    projectServiceSpy.archive.and.returnValue(of(mockProjects[0]));
    projectServiceSpy.unarchive.and.returnValue(of(mockProjects[2]));
    projectServiceSpy.delete.and.returnValue(of(void 0));

    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    const mockSnackBarRef = { onAction: () => new Subject<void>().asObservable(), dismiss: () => {} };
    snackBarSpy.open.and.returnValue(mockSnackBarRef as any);

    await TestBed.configureTestingModule({
      imports: [ProjectList],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    })
    .overrideComponent(ProjectList, {
      remove: { imports: [MatSnackBarModule] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProjectList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load projects on init', fakeAsync(() => {
    tick();
    expect(projectServiceSpy.list).toHaveBeenCalledWith('ACTIVE');
    expect(component.projects().length).toBe(3);
    expect(component.loading()).toBeFalse();
  }));

  it('should filter by search term (name)', fakeAsync(() => {
    tick();
    component.searchTerm.set('alpha');
    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].id).toBe('1');
  }));

  it('should filter by search term (description)', fakeAsync(() => {
    tick();
    component.searchTerm.set('second');
    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].id).toBe('2');
  }));

  it('should filter by search term (baseUri)', fakeAsync(() => {
    tick();
    component.searchTerm.set('gamma');
    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].id).toBe('3');
  }));

  it('should get status class', () => {
    expect(component.getStatusClass('ACTIVE')).toBe('status-active');
    expect(component.getStatusClass('ARCHIVED')).toBe('status-archived');
  });

  it('should reload with new status filter', () => {
    projectServiceSpy.list.calls.reset();
    component.onStatusChange('ARCHIVED');
    expect(projectServiceSpy.list).toHaveBeenCalledWith('ARCHIVED');
  });

  it('should call list() without status when filter is ALL', () => {
    projectServiceSpy.list.calls.reset();
    component.onStatusChange('ALL');
    expect(projectServiceSpy.list).toHaveBeenCalledWith(undefined);
  });

  it('should navigate to new project form', () => {
    spyOn((component as any).router, 'navigate');
    component.createProject();
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/projects/new']);
  });

  it('should navigate to project workspace on open', () => {
    spyOn((component as any).router, 'navigate');
    component.openProject(mockProjects[0]);
    expect((component as any).router.navigate).toHaveBeenCalledWith(['/projects', '1']);
  });

  it('should archive project', () => {
    component.archiveProject(mockProjects[0]);
    expect(projectServiceSpy.archive).toHaveBeenCalledWith('1');
  });

  it('should unarchive project', () => {
    component.unarchiveProject(mockProjects[2]);
    expect(projectServiceSpy.unarchive).toHaveBeenCalledWith('3');
  });

  it('should delete project after confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.deleteProject(mockProjects[0]);
    expect(projectServiceSpy.delete).toHaveBeenCalledWith('1');
  });

  it('should skip delete when not confirmed', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.deleteProject(mockProjects[0]);
    expect(projectServiceSpy.delete).not.toHaveBeenCalled();
  });

  it('should handle load error gracefully', fakeAsync(() => {
    projectServiceSpy.list.and.returnValue(throwError(() => new Error('Network')));
    component.loadProjects();
    tick();
    expect(component.loading()).toBeFalse();
    expect(snackBarSpy.open).toHaveBeenCalled();
  }));
});
