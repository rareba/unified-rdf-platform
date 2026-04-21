import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';

import { IssueDetailDialog } from './issue-detail-dialog';
import { ValidationIssue } from '../../core/models/validation.model';

describe('IssueDetailDialog', () => {
  let fixture: ComponentFixture<IssueDetailDialog>;
  let component: IssueDetailDialog;

  const baseIssue: ValidationIssue = {
    id: 'i1',
    runId: 'r1',
    severity: 'ERROR',
    message: 'Something went wrong',
    resourceUri: 'http://example.org/thing'
  };

  async function render(issue: ValidationIssue): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [IssueDetailDialog, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: MAT_DIALOG_DATA, useValue: { issue, projectId: 'p1' } },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(IssueDetailDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders the issue message', async () => {
    await render(baseIssue);
    const html = fixture.nativeElement.textContent as string;
    expect(html).toContain('Something went wrong');
    expect(html).toContain('http://example.org/thing');
  });

  it('returns null for mappingTarget when sourcePath is missing', async () => {
    await render(baseIssue);
    expect(component.mappingTarget()).toBeNull();
  });

  it('parses a mapping:<uuid>/rule:<ruleId> sourcePath', async () => {
    const uuid = '11111111-2222-3333-4444-555555555555';
    await render({ ...baseIssue, sourcePath: `mapping:${uuid}/rule:abc-123` });
    const target = component.mappingTarget();
    expect(target).not.toBeNull();
    expect(target?.mappingId).toBe(uuid);
    expect(target?.ruleId).toBe('abc-123');
  });
});
