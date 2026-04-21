import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ReleaseList } from './release-list';
import { ReleaseService } from '../../core/services/release.service';
import { Release } from '../../core/models/release.model';

describe('ReleaseList', () => {
  let fixture: ComponentFixture<ReleaseList>;
  let component: ReleaseList;
  let svcSpy: jasmine.SpyObj<ReleaseService>;
  let snackSpy: jasmine.SpyObj<MatSnackBar>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockReleases: Release[] = [
    {
      id: 'r1', projectId: 'p1', version: '1.0.0', name: 'Alpha',
      status: 'PUBLISHED', artifactSizeBytes: 2048,
      createdBy: 'u', createdAt: '2026-04-01T00:00:00Z',
      updatedAt: '2026-04-05T00:00:00Z',
      publishedAt: '2026-04-05T00:00:00Z'
    },
    {
      id: 'r2', projectId: 'p1', version: '1.1.0', name: 'Beta',
      status: 'DRAFT', artifactSizeBytes: 0,
      createdBy: 'u', createdAt: '2026-04-10T00:00:00Z',
      updatedAt: '2026-04-10T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    svcSpy = jasmine.createSpyObj('ReleaseService',
      ['listByProject', 'build', 'archive', 'delete', 'download']);
    svcSpy.listByProject.and.returnValue(of(mockReleases));
    svcSpy.delete.and.returnValue(of(void 0));
    svcSpy.archive.and.returnValue(of(mockReleases[1]));

    snackSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    const ref = { onAction: () => new Subject<void>().asObservable(), dismiss: () => {} };
    snackSpy.open.and.returnValue(ref as any);

    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [ReleaseList],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ReleaseService, useValue: svcSpy },
        { provide: MatSnackBar, useValue: snackSpy },
        { provide: MatDialog, useValue: dialogSpy }
      ]
    })
    .overrideComponent(ReleaseList, {
      remove: { imports: [MatSnackBarModule, MatDialogModule] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReleaseList);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
  });

  it('renders release rows from service', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    expect(svcSpy.listByProject).toHaveBeenCalledWith('p1');
    expect(component.releases().length).toBe(2);
  }));

  it('shows empty state when service returns []', fakeAsync(() => {
    svcSpy.listByProject.and.returnValue(of([]));
    component.reload('p1');
    tick();
    expect(component.releases().length).toBe(0);
  }));

  it('opens the create dialog when openCreate is called', () => {
    const afterClosed = new Subject<Release | undefined>();
    dialogSpy.open.and.returnValue({ afterClosed: () => afterClosed.asObservable() } as any);
    component.openCreate();
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('formatBytes produces human-readable strings', () => {
    expect(component.formatBytes(500)).toBe('500 B');
    expect(component.formatBytes(2048)).toBe('2.0 KB');
    expect(component.formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });

  it('statusClass returns a CSS class string', () => {
    expect(component.statusClass('PUBLISHED')).toBe('status-published');
    expect(component.statusClass('DRAFT')).toBe('status-draft');
  });
});
