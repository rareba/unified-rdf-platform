import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';

import { ReconciliationDashboard } from './reconciliation-dashboard';
import { ReconciliationService } from '../../core/services/reconciliation.service';
import { SettingsService } from '../../core/services/settings.service';
import { MatchCandidate, MatchStats } from '../../core/models/reconciliation.model';

describe('ReconciliationDashboard', () => {
  let fixture: ComponentFixture<ReconciliationDashboard>;
  let component: ReconciliationDashboard;
  let service: jasmine.SpyObj<ReconciliationService>;

  const sample: MatchCandidate = {
    id: 'c1',
    projectId: 'p1',
    sourceUri: 'http://example.org/a',
    targetUri: 'http://example.org/b',
    predicate: 'SAME_AS',
    confidence: 0.85,
    source: 'LOCAL_DUPLICATE',
    matcherName: 'local-duplicate',
    status: 'PENDING',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  const stats: MatchStats = {
    projectId: 'p1',
    pending: 1, approved: 0, rejected: 0, archived: 0,
    byPredicate: { SAME_AS: 1 }, byMatcher: { 'local-duplicate': 1 }
  };

  beforeEach(async () => {
    service = jasmine.createSpyObj<ReconciliationService>('ReconciliationService',
      ['list', 'matchers', 'stats', 'approve', 'reject', 'manual', 'suggest', 'get']);
    service.list.and.returnValue(of([sample]));
    service.matchers.and.returnValue(of([{ id: 'local-duplicate', displayName: 'Local', enabled: true }]));
    service.stats.and.returnValue(of(stats));
    service.approve.and.returnValue(of({ ...sample, status: 'APPROVED' }));
    service.reject.and.returnValue(of({ ...sample, status: 'REJECTED' }));

    const settingsSpy = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20), sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false), retryAttempts: signal(3)
    });

    await TestBed.configureTestingModule({
      imports: [ReconciliationDashboard],
      providers: [
        provideNoopAnimations(),
        { provide: ReconciliationService, useValue: service },
        { provide: SettingsService, useValue: settingsSpy },
        { provide: MatSnackBar, useValue: { open: jasmine.createSpy('open') } },
        { provide: MatDialog, useValue: { open: jasmine.createSpy('open') } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
              parent: { paramMap: convertToParamMap({ id: 'p1', projectId: 'p1' }) },
              queryParamMap: convertToParamMap({ projectId: 'p1' })
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReconciliationDashboard);
    component = fixture.componentInstance;
  });

  it('loads stats and candidates for the project', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(service.list).toHaveBeenCalled();
    expect(service.matchers).toHaveBeenCalled();
    expect(component.candidates().length).toBe(1);
    expect(component.stats()?.pending).toBe(1);
  });

  it('approves a candidate and refreshes', () => {
    fixture.detectChanges();
    component.approve(sample);
    expect(service.approve).toHaveBeenCalledWith('c1');
  });

  it('rejects a candidate', () => {
    fixture.detectChanges();
    component.reject(sample);
    expect(service.reject).toHaveBeenCalledWith('c1');
  });
});
