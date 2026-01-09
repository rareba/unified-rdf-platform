import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { timeoutInterceptor, OPERATION_TYPE, CUSTOM_TIMEOUT, DISABLE_TIMEOUT } from './timeout.interceptor';
import { SettingsService } from '../services/settings.service';
import { fakeAsync, tick } from '@angular/core/testing';

describe('timeoutInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let settingsService: jasmine.SpyObj<SettingsService>;

  beforeEach(() => {
    settingsService = jasmine.createSpyObj('SettingsService', ['getTimeoutMs'], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000)
    });
    settingsService.getTimeoutMs.and.callFake((type?: 'default' | 'pipeline' | 'sparql') => {
      switch (type) {
        case 'pipeline': return 60000;
        case 'sparql': return 30000;
        default: return 10000;
      }
    });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([timeoutInterceptor])),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsService }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should allow request to complete successfully', () => {
    httpClient.get('/api/test').subscribe(response => {
      expect(response).toEqual({ data: 'test' });
    });

    const req = httpMock.expectOne('/api/test');
    req.flush({ data: 'test' });
  });

  it('should use default timeout for regular requests', () => {
    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(settingsService.getTimeoutMs).toHaveBeenCalledWith('default');
    req.flush({});
  });

  it('should use pipeline timeout for pipeline operations', () => {
    const context = new HttpContext().set(OPERATION_TYPE, 'pipeline');

    httpClient.get('/api/pipelines', { context }).subscribe();

    const req = httpMock.expectOne('/api/pipelines');
    expect(settingsService.getTimeoutMs).toHaveBeenCalledWith('pipeline');
    req.flush({});
  });

  it('should use sparql timeout for sparql operations', () => {
    const context = new HttpContext().set(OPERATION_TYPE, 'sparql');

    httpClient.get('/api/sparql', { context }).subscribe();

    const req = httpMock.expectOne('/api/sparql');
    expect(settingsService.getTimeoutMs).toHaveBeenCalledWith('sparql');
    req.flush({});
  });

  it('should use custom timeout when specified', () => {
    const context = new HttpContext().set(CUSTOM_TIMEOUT, 5000);

    httpClient.get('/api/custom', { context }).subscribe();

    const req = httpMock.expectOne('/api/custom');
    // Custom timeout should override operation type
    expect(settingsService.getTimeoutMs).not.toHaveBeenCalled();
    req.flush({});
  });

  it('should skip timeout when disabled', () => {
    const context = new HttpContext().set(DISABLE_TIMEOUT, true);

    httpClient.get('/api/no-timeout', { context }).subscribe();

    const req = httpMock.expectOne('/api/no-timeout');
    expect(settingsService.getTimeoutMs).not.toHaveBeenCalled();
    req.flush({});
  });

  it('should pass through non-timeout errors', () => {
    let errorReceived: Error | undefined;

    httpClient.get('/api/test').subscribe({
      error: (err) => {
        errorReceived = err;
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

    expect(errorReceived).toBeTruthy();
  });
});
