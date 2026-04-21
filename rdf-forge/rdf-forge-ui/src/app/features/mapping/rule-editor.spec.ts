import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { RuleEditor } from './rule-editor';
import { MappingRule } from '../../core/models/mapping.model';

describe('RuleEditor', () => {
  let fixture: ComponentFixture<RuleEditor>;
  let component: RuleEditor;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<RuleEditor>>;

  const baseRule: MappingRule = {
    id: 'r1',
    type: 'COLUMN_TO_LITERAL',
    source: 'name',
    target: 'http://ex.org/name',
    uriTemplate: null,
    datatype: null,
    language: null,
    transform: null
  };

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [RuleEditor, MatDialogModule],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRefSpy },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            rule: baseRule,
            availableColumns: ['id', 'name'],
            targetPredicates: ['http://ex.org/name']
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(RuleEditor);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates and clones the input rule', () => {
    expect(component.rule.id).toBe('r1');
    // mutating component.rule should not mutate original
    component.rule.source = 'changed';
    expect(baseRule.source).toBe('name');
  });

  it('save without id does nothing', () => {
    component.rule.id = '';
    component.save();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('save closes with updated rule when valid', () => {
    component.rule.id = 'r1';
    component.rule.type = 'FIXED_URI';
    component.save();
    expect(dialogRefSpy.close).toHaveBeenCalled();
    const arg = dialogRefSpy.close.calls.mostRecent().args[0] as MappingRule;
    expect(arg.id).toBe('r1');
    expect(arg.type).toBe('FIXED_URI');
  });

  it('save persists transform when transformType chosen', () => {
    component.transformType = 'UPPER';
    component.save();
    const arg = dialogRefSpy.close.calls.mostRecent().args[0] as MappingRule;
    expect(arg.transform?.type).toBe('UPPER');
  });

  it('needsSource is false for FIXED_URI', () => {
    component.rule.type = 'FIXED_URI';
    expect(component.needsSource()).toBeFalse();
  });

  it('needsSource is true for COLUMN_TO_URI', () => {
    component.rule.type = 'COLUMN_TO_URI';
    expect(component.needsSource()).toBeTrue();
  });
});
