import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MappingStudio } from './mapping-studio';
import { MappingService } from '../../core/services/mapping.service';
import { Mapping, TripleDto } from '../../core/models/mapping.model';

describe('MappingStudio', () => {
  let fixture: ComponentFixture<MappingStudio>;
  let component: MappingStudio;
  let svc: jasmine.SpyObj<MappingService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let dialog: jasmine.SpyObj<MatDialog>;

  const sampleTriple: TripleDto = { subject: 's', predicate: 'p', object: 'o', objectType: 'URI' };

  const baseMapping: Mapping = {
    id: 'm1', projectId: 'p1', name: 'Test',
    sourceType: 'CSV', rules: [
      { id: 'r1', type: 'FIXED_URI', source: null, target: null,
        uriTemplate: '${baseUri}a', datatype: null, language: null, transform: null }
    ],
    mappingType: 'GENERIC', version: 1, createdBy: 'u',
    createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-04-10T00:00:00Z',
    targetNamespace: 'https://ex.org/'
  };

  beforeEach(async () => {
    svc = jasmine.createSpyObj('MappingService',
      ['get', 'update', 'preview', 'explain', 'validate']);
    svc.get.and.returnValue(of(baseMapping));
    svc.preview.and.returnValue(of({ triples: [sampleTriple], sampleSize: 1, totalSourceRows: 1 }));
    svc.explain.and.returnValue(of({
      rows: [{
        rowIndex: 0, row: { id: '1' }, triples: [{
          triple: sampleTriple,
          trace: {
            ruleId: 'r1', ruleType: 'FIXED_URI', source: 'id', target: null,
            uriTemplateUsed: '${baseUri}a', sourceValue: '1', transforms: [], finalValue: 'o'
          }
        }]
      }]
    }));
    svc.update.and.returnValue(of(baseMapping));
    svc.validate.and.returnValue(of({ valid: true, issues: [] }));

    snack = jasmine.createSpyObj('MatSnackBar', ['open']);
    const ref = { onAction: () => new Subject<void>().asObservable(), dismiss: () => {} };
    snack.open.and.returnValue(ref as any);

    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    // Default dialog.open -> closed with undefined (no rule added).
    const defaultDialogRef = { afterClosed: () => of(undefined) } as any;
    dialog.open.and.returnValue(defaultDialogRef);

    await TestBed.configureTestingModule({
      imports: [MappingStudio],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: MappingService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
        { provide: MatDialog, useValue: dialog },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'm1' })) }
        }
      ]
    })
    .overrideComponent(MappingStudio, {
      remove: { imports: [MatSnackBarModule, MatDialogModule] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(MappingStudio);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the mapping on init', fakeAsync(() => {
    tick();
    expect(svc.get).toHaveBeenCalledWith('m1');
    expect(component.mapping()?.id).toBe('m1');
  }));

  it('preview is triggered after debounce on rule change', fakeAsync(() => {
    tick(); // initial load
    // Provide sample rows so schedulePreview will actually fire runPreview
    // (it short-circuits on an empty sample to avoid useless backend hits).
    component.sampleRows.set([{ id: '1' }]);
    tick(500);
    svc.preview.calls.reset();
    component.deleteRule(0);
    tick(500);
    expect(svc.preview).toHaveBeenCalled();
  }));

  it('explain triggers on triple click', fakeAsync(() => {
    tick();
    tick(500);
    component.onTripleClick(0, sampleTriple);
    tick();
    expect(svc.explain).toHaveBeenCalled();
    expect(component.selectedRow()?.rowIndex).toBe(0);
    expect(component.highlightedRuleId()).toBe('r1');
  }));

  it('save calls service.update with current mapping', () => {
    component.mapping.set(baseMapping);
    component.save();
    expect(svc.update).toHaveBeenCalledWith('m1', jasmine.objectContaining({ name: 'Test' }));
  });

  it('preview error populates inline previewError', fakeAsync(() => {
    tick();
    svc.preview.and.returnValue({
      subscribe: (obs: any) => { obs.error({ message: 'bad rule' }); return { unsubscribe: () => {} }; }
    } as any);
    component.addRule();
    // adding opens dialog — we short-circuit to direct schedulePreview by mutating rules:
    component.mapping.set({ ...baseMapping, rules: [...baseMapping.rules] });
    (component as any).schedulePreview();
    tick(500);
    expect(component.previewError()).toContain('bad rule');
  }));

  it('sample JSON parse error is inline', () => {
    component.sampleJson.set('not-json');
    component.onSampleJsonChange();
    expect(component.sampleParseError()).toContain('Invalid JSON');
  });
});
