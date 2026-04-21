import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { SuiteEditor } from './suite-editor';
import { ShaclService } from '../../core/services/shacl.service';
import { ValidationSuite } from '../../core/models/validation.model';

describe('SuiteEditor', () => {
  let fixture: ComponentFixture<SuiteEditor>;
  let component: SuiteEditor;

  beforeEach(async () => {
    const shaclSpy = jasmine.createSpyObj<ShaclService>('ShaclService', ['list', 'getProfiles']);
    shaclSpy.list.and.returnValue(of([]));
    shaclSpy.getProfiles.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [SuiteEditor, NoopAnimationsModule],
      providers: [{ provide: ShaclService, useValue: shaclSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(SuiteEditor);
    component = fixture.componentInstance;
    component.projectId = 'p1';
    fixture.detectChanges();
  });

  it('marks the form invalid when name is empty', () => {
    expect(component.form.valid).toBeFalse();
  });

  it('marks the form valid when name is present', () => {
    component.form.patchValue({ name: 'my-suite' });
    expect(component.form.valid).toBeTrue();
  });

  it('addRule appends a rule to the form array', () => {
    expect(component.rules.length).toBe(0);
    component.addRule();
    expect(component.rules.length).toBe(1);
  });

  it('removeRule drops the selected rule', () => {
    component.addRule();
    component.addRule();
    expect(component.rules.length).toBe(2);
    component.removeRule(0);
    expect(component.rules.length).toBe(1);
  });

  it('patches the form when a suite @Input arrives', () => {
    const suite: ValidationSuite = {
      id: 's1', projectId: 'p1', name: 'loaded', description: 'desc',
      rules: [{ id: 'r1', name: 'rule-1', type: 'SHACL_SHAPE',
                resourceRef: 'abc', severity: 'WARNING' }],
      gate: 'FAIL_ON_WARNING'
    };
    component.suite = suite;
    component.ngOnChanges({
      suite: { currentValue: suite, previousValue: null, firstChange: true, isFirstChange: () => true }
    } as any);
    expect(component.form.get('name')?.value).toBe('loaded');
    expect(component.rules.length).toBe(1);
  });

  it('emits save with the current form value', (done) => {
    component.form.patchValue({ name: 'emit-me' });
    component.save.subscribe(v => {
      expect(v.name).toBe('emit-me');
      done();
    });
    component.emitSave();
  });
});
