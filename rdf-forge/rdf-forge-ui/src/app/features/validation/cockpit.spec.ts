import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { signal } from '@angular/core';

import { Cockpit } from './cockpit';
import { ValidationService } from '../../core/services/validation.service';
import {
  ValidationRun,
  ValidationSuite,
  ValidationIssue
} from '../../core/models/validation.model';

describe('Cockpit', () => {
  let fixture: ComponentFixture<Cockpit>;
  let component: Cockpit;
  let svcSpy: jasmine.SpyObj<ValidationService>;

  const suite: ValidationSuite = {
    id: 's1',
    projectId: 'p1',
    name: 'suite-1',
    rules: [{ id: 'r1', name: 'rule-1', type: 'SHACL_SHAPE',
              resourceRef: '00000000-0000-0000-0000-000000000000', severity: 'ERROR' }],
    gate: 'FAIL_ON_ERROR'
  };

  const run: ValidationRun = {
    id: 'run1',
    suiteId: 's1',
    projectId: 'p1',
    ranAt: new Date().toISOString(),
    durationMs: 12,
    status: 'PASSED',
    issueCount: 0,
    errorCount: 0,
    warningCount: 0,
    infoCount: 0,
    fatalCount: 0
  };

  beforeEach(async () => {
    svcSpy = jasmine.createSpyObj<ValidationService>('ValidationService',
      ['listSuites', 'history', 'runSuite', 'issues', 'createSuite',
       'updateSuite', 'deleteSuite', 'validateAll']);
    svcSpy.listSuites.and.returnValue(of([suite]));
    svcSpy.history.and.returnValue(of([run]));
    svcSpy.runSuite.and.returnValue(of(run));
    svcSpy.issues.and.returnValue(of([] as ValidationIssue[]));

    await TestBed.configureTestingModule({
      imports: [Cockpit, NoopAnimationsModule, MatDialogModule, MatSnackBarModule],
      providers: [{ provide: ValidationService, useValue: svcSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(Cockpit);
    component = fixture.componentInstance;
    component.projectId = 'p1';
    fixture.detectChanges();
  });

  it('loads suites on init', () => {
    expect(svcSpy.listSuites).toHaveBeenCalledWith('p1');
    expect(component.suites().length).toBe(1);
    expect(component.selectedSuite()?.id).toBe('s1');
  });

  it('loads run history for the selected suite', () => {
    expect(svcSpy.history).toHaveBeenCalledWith('s1', 20);
    expect(component.history().length).toBe(1);
  });

  it('runs the selected suite and prepends the result to history', () => {
    component.runSelected();
    expect(svcSpy.runSuite).toHaveBeenCalled();
    expect(component.history()[0].id).toBe(run.id);
    expect(component.selectedRun()?.id).toBe(run.id);
  });

  it('loads issues when a run is selected', () => {
    component.selectRun(run);
    expect(svcSpy.issues).toHaveBeenCalledWith('run1', undefined, 200);
    expect(component.issues()).toEqual([]);
  });

  it('computes danger health when a suite failed', () => {
    const failing: ValidationRun = { ...run, status: 'FAILED', errorCount: 2, issueCount: 2 };
    // Manually set the latest-run map to simulate a failed suite.
    component.latestRunBySuiteId.set({ s1: failing });
    expect(component.healthClass()).toBe('danger');
    expect(component.healthLabel()).toContain('failing');
  });
});
