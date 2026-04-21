import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { QueryWorkbench } from './query-workbench';
import { SavedQueryService } from '../../core/services/saved-query.service';
import { TriplestoreService } from '../../core/services/triplestore.service';
import { SettingsService } from '../../core/services/settings.service';
import { SavedQueryRunResponse } from '../../core/models/saved-query.model';
import { TriplestoreConnection } from '../../core/models/triplestore.model';

describe('QueryWorkbench', () => {
  let fixture: ComponentFixture<QueryWorkbench>;
  let component: QueryWorkbench;
  let savedQuerySvc: jasmine.SpyObj<SavedQueryService>;
  let triplestoreSvc: jasmine.SpyObj<TriplestoreService>;

  const sampleTs: TriplestoreConnection = {
    id: 'ts1', name: 'Local Fuseki', type: 'FUSEKI', url: 'http://localhost:3030',
    authType: 'none', isDefault: true, healthStatus: 'healthy',
    createdBy: 'u', createdAt: new Date()
  };

  const sampleResult: SavedQueryRunResponse = {
    type: 'SELECT',
    variables: ['s'],
    bindings: [{ s: { type: 'uri', value: 'http://example.org/x' } }],
    durationMs: 10,
    executedAt: new Date().toISOString()
  };

  beforeEach(async () => {
    savedQuerySvc = jasmine.createSpyObj<SavedQueryService>('SavedQueryService',
      ['list', 'get', 'create', 'update', 'delete', 'run', 'runInline']);
    savedQuerySvc.list.and.returnValue(of([]));
    savedQuerySvc.runInline.and.returnValue(of(sampleResult));
    savedQuerySvc.run.and.returnValue(of(sampleResult));

    triplestoreSvc = jasmine.createSpyObj<TriplestoreService>('TriplestoreService',
      ['list']);
    triplestoreSvc.list.and.returnValue(of([sampleTs]));

    const settingsSpy = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    await TestBed.configureTestingModule({
      imports: [QueryWorkbench],
      providers: [
        provideNoopAnimations(),
        { provide: SavedQueryService, useValue: savedQuerySvc },
        { provide: TriplestoreService, useValue: triplestoreSvc },
        { provide: SettingsService, useValue: settingsSpy },
        { provide: MatSnackBar, useValue: { open: jasmine.createSpy('open') } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ projectId: 'p1' }) }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(QueryWorkbench);
    component = fixture.componentInstance;
  });

  it('creates and loads the default triplestore', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(component.triplestoreId).toBe('ts1');
    expect(savedQuerySvc.list).toHaveBeenCalledWith('p1', undefined);
  });

  it('detects parameters from query text', () => {
    fixture.detectChanges();
    component.queryText = 'SELECT * WHERE { ?s rdfs:label ?q FILTER(?n > 10) }';
    const params = component.detectedParams();
    expect(params).toContain('s');
    expect(params).toContain('q');
    expect(params).toContain('n');
  });

  it('runs inline query when no saved query is selected', () => {
    fixture.detectChanges();
    component.queryText = 'SELECT * WHERE { ?s ?p ?o }';
    component.onRun();
    expect(savedQuerySvc.runInline).toHaveBeenCalled();
    expect(component.lastResult()).toEqual(sampleResult);
  });

  it('shows error when run fails', () => {
    fixture.detectChanges();
    savedQuerySvc.runInline.and.returnValue(
      throwError(() => ({ message: 'boom' }))
    );
    component.queryText = 'SELECT * WHERE { ?s ?p ?o }';
    component.onRun();
    expect(component.lastError()).toContain('boom');
  });
});
