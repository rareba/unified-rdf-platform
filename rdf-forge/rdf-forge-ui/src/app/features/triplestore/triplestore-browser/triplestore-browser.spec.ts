import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { TriplestoreBrowser } from './triplestore-browser';
import { TriplestoreService, ProviderService } from '../../../core/services';
import { TriplestoreConnection, Graph } from '../../../core/models';

describe('TriplestoreBrowser', () => {
  let component: TriplestoreBrowser;
  let fixture: ComponentFixture<TriplestoreBrowser>;
  let triplestoreServiceSpy: jasmine.SpyObj<TriplestoreService>;
  let providerServiceSpy: jasmine.SpyObj<ProviderService>;

  const mockConnections: TriplestoreConnection[] = [
    { id: '1', name: 'Local GraphDB', type: 'GRAPHDB', url: 'http://localhost:7200/repositories/test', authType: 'none', isDefault: true, healthStatus: 'healthy', createdBy: 'user', createdAt: new Date() },
    { id: '2', name: 'Remote Fuseki', type: 'FUSEKI', url: 'http://fuseki:3030/dataset', authType: 'basic', isDefault: false, healthStatus: 'unhealthy', createdBy: 'user', createdAt: new Date() }
  ];

  const mockGraphs: Graph[] = [
    { uri: 'http://example.org/graph1', name: 'Graph 1', tripleCount: 1000 },
    { uri: 'http://example.org/graph2', name: 'Graph 2', tripleCount: 2000 }
  ];

  beforeEach(async () => {
    triplestoreServiceSpy = jasmine.createSpyObj('TriplestoreService', [
      'list', 'get', 'create', 'update', 'delete', 'getGraphs', 'getGraphResources', 'executeQuery', 'test'
    ]);
    providerServiceSpy = jasmine.createSpyObj('ProviderService', ['getDestinations', 'getTriplestoreProviders']);

    triplestoreServiceSpy.list.and.returnValue(of(mockConnections));
    triplestoreServiceSpy.getGraphs.and.returnValue(of(mockGraphs));
    triplestoreServiceSpy.getGraphResources.and.returnValue(of([]));
    providerServiceSpy.getDestinations.and.returnValue(of([]));
    providerServiceSpy.getTriplestoreProviders.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [TriplestoreBrowser],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: TriplestoreService, useValue: triplestoreServiceSpy },
        { provide: ProviderService, useValue: providerServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TriplestoreBrowser);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });

  it('should load connections on init', fakeAsync(() => {
    tick();
    expect(triplestoreServiceSpy.list).toHaveBeenCalled();
    expect(component.connections().length).toBe(2);
    expect(component.loading()).toBeFalse();
  }));

  it('should select connection and load graphs', fakeAsync(() => {
    tick();
    component.selectConnection(mockConnections[0]);
    tick();
    expect(component.selectedConnection()).toBe(mockConnections[0]);
    expect(triplestoreServiceSpy.getGraphs).toHaveBeenCalledWith('1');
  }));

  it('should handle load error gracefully', fakeAsync(() => {
    triplestoreServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadConnections();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should open connection dialog', () => {
    component.openConnectionDialog();
    expect(component.connectionDialogVisible()).toBeTrue();
  });

  it('should open upload dialog', () => {
    component.openUploadDialog();
    expect(component.uploadDialogVisible()).toBeTrue();
  });

  it('should select graph', fakeAsync(() => {
    tick();
    // First select a connection so loadResources works
    component.selectConnection(mockConnections[0]);
    tick();
    component.selectGraph(mockGraphs[0]);
    tick();
    expect(component.selectedGraph()).toBe(mockGraphs[0]);
    expect(triplestoreServiceSpy.getGraphResources).toHaveBeenCalled();
  }));

  it('should get status color', () => {
    expect(component.getStatusColor('healthy')).toBe('primary');
    expect(component.getStatusColor('unhealthy')).toBe('warn');
    expect(component.getStatusColor('unknown')).toBe('');
  });

  it('should get status icon', () => {
    expect(component.getStatusIcon('healthy')).toBe('check_circle');
    expect(component.getStatusIcon('unhealthy')).toBe('cancel');
    expect(component.getStatusIcon('unknown')).toBe('help');
  });

  it('should get type icon', () => {
    expect(component.getTypeIcon('GRAPHDB')).toBe('account_tree');
    expect(component.getTypeIcon('FUSEKI')).toBe('dns');
    expect(component.getTypeIcon('STARDOG')).toBe('star');
  });

  it('should format number', () => {
    expect(component.formatNumber(500)).toBe('500');
    expect(component.formatNumber(1500)).toBe('1.5K');
    expect(component.formatNumber(1500000)).toBe('1.5M');
  });

  it('should compute total triples', fakeAsync(() => {
    tick();
    component.selectConnection(mockConnections[0]);
    tick();
    expect(component.totalTriples()).toBe(3000);
  }));

  it('should compute connection status when selected', fakeAsync(() => {
    tick();
    component.selectConnection(mockConnections[0]);
    tick();
    expect(component.connectionStatus()).toBe('healthy');
    component.selectConnection(mockConnections[1]);
    tick();
    expect(component.connectionStatus()).toBe('unhealthy');
  }));

  it('should toggle connection dialog visibility', () => {
    expect(component.connectionDialogVisible()).toBeFalse();
    component.openConnectionDialog();
    expect(component.connectionDialogVisible()).toBeTrue();
    component.connectionDialogVisible.set(false);
    expect(component.connectionDialogVisible()).toBeFalse();
  });

  it('should toggle upload dialog visibility', () => {
    expect(component.uploadDialogVisible()).toBeFalse();
    component.openUploadDialog();
    expect(component.uploadDialogVisible()).toBeTrue();
    component.uploadDialogVisible.set(false);
    expect(component.uploadDialogVisible()).toBeFalse();
  });

  it('should create new connection', fakeAsync(() => {
    triplestoreServiceSpy.create.and.returnValue(of(mockConnections[0]));
    component.newConnection.set({
      name: 'New Connection',
      type: 'GRAPHDB',
      url: 'http://localhost:7200/repositories/new',
      defaultGraph: '',
      authType: 'none',
      authConfig: {},
      isDefault: false
    });
    component.createConnection();
    tick();
    expect(triplestoreServiceSpy.create).toHaveBeenCalled();
  }));

  it('should delete connection when confirmed', fakeAsync(() => {
    triplestoreServiceSpy.delete.and.returnValue(of(void 0));
    tick();
    // deleteConnection is called directly (no confirmation in this method)
    component.deleteConnection(mockConnections[0]);
    tick();
    expect(triplestoreServiceSpy.delete).toHaveBeenCalledWith('1');
  }));

  it('should confirm delete connection', fakeAsync(() => {
    triplestoreServiceSpy.delete.and.returnValue(of(void 0));
    spyOn(window, 'confirm').and.returnValue(true);
    tick();
    component.confirmDeleteConnection(mockConnections[0]);
    tick();
    expect(triplestoreServiceSpy.delete).toHaveBeenCalledWith('1');
  }));

  it('should not delete connection if not confirmed', fakeAsync(() => {
    spyOn(window, 'confirm').and.returnValue(false);
    tick();
    component.confirmDeleteConnection(mockConnections[0]);
    tick();
    expect(triplestoreServiceSpy.delete).not.toHaveBeenCalled();
  }));

  it('should apply query template', fakeAsync(() => {
    tick();
    const template = { name: 'Test', query: 'SELECT * WHERE { ?s ?p ?o }', description: 'Test' };
    component.applyTemplate(template);
    expect(component.sparqlQuery()).toBe('SELECT * WHERE { ?s ?p ?o }');
  }));

  it('should test connection', fakeAsync(() => {
    triplestoreServiceSpy.test.and.returnValue(of({ success: true, latencyMs: 50 }));
    tick();
    component.selectConnection(mockConnections[0]);
    tick();
    component.testConnection(mockConnections[0]);
    tick();
    expect(triplestoreServiceSpy.test).toHaveBeenCalledWith('1');
  }));

  it('should have query templates defined', () => {
    expect(component.queryTemplates.length).toBeGreaterThan(0);
    expect(component.queryTemplates[0].name).toBeTruthy();
  });

  it('should have auth types defined', () => {
    expect(component.authTypes.length).toBeGreaterThan(0);
    expect(component.authTypes.some(a => a.value === 'basic')).toBeTrue();
  });

  it('should have rdf formats defined', () => {
    expect(component.rdfFormats.length).toBeGreaterThan(0);
    expect(component.rdfFormats.some(f => f.value === 'turtle')).toBeTrue();
  });
});