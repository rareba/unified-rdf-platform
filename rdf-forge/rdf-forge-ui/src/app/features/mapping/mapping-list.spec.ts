import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MappingList } from './mapping-list';
import { MappingService } from '../../core/services/mapping.service';
import { Mapping } from '../../core/models/mapping.model';

describe('MappingList', () => {
  let fixture: ComponentFixture<MappingList>;
  let component: MappingList;
  let svcSpy: jasmine.SpyObj<MappingService>;
  let snackSpy: jasmine.SpyObj<MatSnackBar>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockMappings: Mapping[] = [
    {
      id: 'm1', projectId: 'p1', name: 'Alpha Map',
      sourceType: 'CSV', rules: [],
      mappingType: 'GENERIC', version: 1, createdBy: 'u',
      createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-04-10T00:00:00Z'
    },
    {
      id: 'm2', projectId: 'p1', name: 'Cube Obs',
      sourceType: 'CSV', rules: [],
      mappingType: 'CUBE', version: 1, createdBy: 'u',
      createdAt: '2026-04-02T00:00:00Z', updatedAt: '2026-04-11T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    svcSpy = jasmine.createSpyObj('MappingService', ['listByProject', 'delete']);
    svcSpy.listByProject.and.returnValue(of(mockMappings));
    svcSpy.delete.and.returnValue(of(void 0));

    snackSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    const ref = { onAction: () => new Subject<void>().asObservable(), dismiss: () => {} };
    snackSpy.open.and.returnValue(ref as any);

    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [MappingList],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: MappingService, useValue: svcSpy },
        { provide: MatSnackBar, useValue: snackSpy },
        { provide: MatDialog, useValue: dialogSpy }
      ]
    })
    .overrideComponent(MappingList, {
      remove: { imports: [MatSnackBarModule, MatDialogModule] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(MappingList);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
  });

  it('renders mapping rows from service', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    expect(svcSpy.listByProject).toHaveBeenCalledWith('p1');
    expect(component.mappings().length).toBe(2);
  }));

  it('shows empty state when service returns []', fakeAsync(() => {
    svcSpy.listByProject.and.returnValue(of([]));
    component.reload('p1');
    tick();
    expect(component.mappings().length).toBe(0);
  }));

  it('opens the create dialog when openCreateDialog is called', () => {
    const afterClosed = new Subject<Mapping | undefined>();
    dialogSpy.open.and.returnValue({ afterClosed: () => afterClosed.asObservable() } as any);
    component.openCreateDialog();
    expect(dialogSpy.open).toHaveBeenCalled();
  });
});
