import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ProjectWorkspace } from './project-workspace';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectContextService } from '../services/project-context.service';
import { ProjectSummary } from '../../../core/models';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

describe('ProjectWorkspace', () => {
  let component: ProjectWorkspace;
  let fixture: ComponentFixture<ProjectWorkspace>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;
  let context: ProjectContextService;

  const mockSummary: ProjectSummary = {
    id: 'p1',
    name: 'Project One',
    description: 'A description',
    baseUri: 'https://example.org/p1/',
    status: 'ACTIVE',
    createdBy: 'u',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-05T00:00:00Z',
    counts: { pipelines: 3, cubes: 1, shapes: 2 }
  };

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService',
      ['summary', 'archive', 'unarchive']);
    projectServiceSpy.summary.and.returnValue(of(mockSummary));
    projectServiceSpy.archive.and.returnValue(of({ ...mockSummary, status: 'ARCHIVED' }));
    projectServiceSpy.unarchive.and.returnValue(of(mockSummary));

    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    snackBarSpy.open.and.returnValue({ onAction: () => new Subject<void>().asObservable() } as any);

    await TestBed.configureTestingModule({
      imports: [ProjectWorkspace],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        ProjectContextService,
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } }
        }
      ]
    })
    .overrideComponent(ProjectWorkspace, { remove: { imports: [MatSnackBarModule] } })
    .compileComponents();

    fixture = TestBed.createComponent(ProjectWorkspace);
    component = fixture.componentInstance;
    context = TestBed.inject(ProjectContextService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load project summary on init', fakeAsync(() => {
    tick();
    expect(projectServiceSpy.summary).toHaveBeenCalledWith('p1');
    expect(component.summary()?.id).toBe('p1');
    expect(component.loading()).toBeFalse();
  }));

  it('should populate the ProjectContextService with summary', fakeAsync(() => {
    tick();
    expect(context.currentSummary()?.id).toBe('p1');
    expect(context.currentProject()?.id).toBe('p1');
  }));

  it('should expose tab configuration', () => {
    const paths = component.tabs.map(t => t.path);
    expect(paths).toContain('overview');
    expect(paths).toContain('data');
    expect(paths).toContain('ontology');
    expect(paths).toContain('mapping');
    expect(paths).toContain('validation');
    expect(paths).toContain('publish');
    expect(paths).toContain('lineage');
    expect(paths).toContain('docs');
  });

  it('should show error state on load failure', fakeAsync(() => {
    projectServiceSpy.summary.and.returnValue(throwError(() => new Error('boom')));
    component.retry();
    tick();
    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBeFalse();
  }));

  it('should archive and update context', fakeAsync(() => {
    tick();
    component.archiveProject();
    tick();
    expect(projectServiceSpy.archive).toHaveBeenCalledWith('p1');
    expect(context.currentProject()?.status).toBe('ARCHIVED');
  }));

  it('should unarchive and update context', fakeAsync(() => {
    tick();
    component.unarchiveProject();
    tick();
    expect(projectServiceSpy.unarchive).toHaveBeenCalledWith('p1');
  }));

  it('should compute status class correctly', fakeAsync(() => {
    tick();
    expect(component.statusClass()).toBe('status-active');
    context.setProject({ ...mockSummary, status: 'ARCHIVED' });
    expect(component.statusClass()).toBe('status-archived');
  }));
});
