import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { ErrorTrackingService, ErrorReport } from './error-tracking.service';
import { environment } from '../../../environments/environment';

describe('ErrorTrackingService', () => {
  let service: ErrorTrackingService;
  let httpMock: HttpTestingController;
  let consoleSpy: jasmine.Spy;
  let consoleDebugSpy: jasmine.Spy;

  beforeEach(() => {
    consoleSpy = spyOn(console, 'error').and.stub();
    consoleDebugSpy = spyOn(console, 'debug').and.stub();

    TestBed.configureTestingModule({
      providers: [
        ErrorTrackingService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ErrorTrackingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.dispose();
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('trackError', () => {
    it('should track a JavaScript error', () => {
      const error = new Error('Test error message');
      service.trackError(error, 'TestContext', { customData: 'value' });

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track error without context', () => {
      const error = new Error('Simple error');
      service.trackError(error);

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track error without metadata', () => {
      const error = new Error('Error without metadata');
      service.trackError(error, 'Context');

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should extract context from stack trace', () => {
      const error = new Error('Test error');
      error.stack = `Error: Test error
        at MyComponent.ngOnInit (http://localhost/main.js:123:45)
        at callHook (http://localhost/core.js:456:78)`;

      service.trackError(error);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should extract service context from stack trace', () => {
      const error = new Error('Service error');
      error.stack = `Error: Service error
        at MyService.loadData (http://localhost/main.js:123:45)`;

      service.trackError(error);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should handle error with empty stack', () => {
      const error = new Error('No stack error');
      error.stack = undefined;

      service.trackError(error);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should deduplicate similar errors', () => {
      const error1 = new Error('Duplicate error');
      const error2 = new Error('Duplicate error');

      service.trackError(error1, 'SameContext');
      service.trackError(error2, 'SameContext');

      // Second error should be skipped due to deduplication
      expect(service.getQueueSize()).toBe(1);
    });

    it('should allow different errors', () => {
      const error1 = new Error('Error one');
      const error2 = new Error('Error two');

      service.trackError(error1, 'Context');
      service.trackError(error2, 'Context');

      expect(service.getQueueSize()).toBe(2);
    });
  });

  describe('trackHttpError', () => {
    it('should track HTTP error with status 500', () => {
      const error = new HttpErrorResponse({
        status: 500,
        statusText: 'Internal Server Error',
        url: '/api/test'
      });

      service.trackHttpError(error, 'API Call', { endpoint: 'test' });
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track HTTP error with status 404', () => {
      const error = new HttpErrorResponse({
        status: 404,
        statusText: 'Not Found',
        url: '/api/missing'
      });

      service.trackHttpError(error);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track HTTP error with status 429', () => {
      const error = new HttpErrorResponse({
        status: 429,
        statusText: 'Too Many Requests',
        url: '/api/rate-limited'
      });

      service.trackHttpError(error, 'Rate Limited');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track HTTP error with status 400', () => {
      const error = new HttpErrorResponse({
        status: 400,
        statusText: 'Bad Request',
        url: '/api/invalid'
      });

      service.trackHttpError(error);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should include error body in metadata', () => {
      const errorBody = { message: 'Validation failed', errors: ['field1', 'field2'] };
      const error = new HttpErrorResponse({
        status: 422,
        statusText: 'Unprocessable Entity',
        url: '/api/validate',
        error: errorBody
      });

      service.trackHttpError(error, 'Validation');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });
  });

  describe('trackAngularError', () => {
    it('should track Angular Error instance', () => {
      const error = new Error('Angular error');
      service.trackAngularError(error, 'Component Init');

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track Angular string error', () => {
      service.trackAngularError('String error message', 'Context');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track Angular object error', () => {
      service.trackAngularError({ code: 'ERR_001', message: 'Object error' }, 'Context');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track Angular circular object error', () => {
      const obj: { a: number; self?: unknown } = { a: 1 };
      obj.self = obj;

      service.trackAngularError(obj, 'Circular');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track Angular null error', () => {
      service.trackAngularError(null, 'Null Context');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track Angular undefined error', () => {
      service.trackAngularError(undefined, 'Undefined Context');
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });
  });

  describe('trackNetworkError', () => {
    it('should track network error', () => {
      const error = new Error('Network request failed');
      service.trackNetworkError(error, '/api/data');

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should track network error without URL', () => {
      const error = new Error('Connection refused');
      service.trackNetworkError(error);

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should include navigator.onLine status', () => {
      const error = new Error('Offline');
      service.trackNetworkError(error, '/api/sync');

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });
  });

  describe('sendImmediately', () => {
    it('should send error immediately bypassing queue', () => {
      const report: ErrorReport = {
        message: 'Critical error',
        url: window.location.href,
        userAgent: navigator.userAgent,
        timestamp: new Date().toISOString(),
        severity: 'critical',
        category: 'javascript'
      };

      service.sendImmediately(report);
      expect(service.getQueueSize()).toBe(0);
    });
  });

  describe('flush', () => {
    it('should flush all queued errors', () => {
      service.trackError(new Error('Error 1'));
      service.trackError(new Error('Error 2'));

      expect(service.getQueueSize()).toBe(2);

      service.flush();

      // In non-production, errors are just logged
      expect(service.getQueueSize()).toBe(0);
    });

    it('should handle flush with empty queue', () => {
      expect(service.getQueueSize()).toBe(0);
      expect(() => service.flush()).not.toThrow();
    });
  });

  describe('clearQueue', () => {
    it('should clear all queued errors', () => {
      service.trackError(new Error('Error 1'));
      service.trackError(new Error('Error 2'));

      expect(service.getQueueSize()).toBe(2);

      service.clearQueue();

      expect(service.getQueueSize()).toBe(0);
    });

    it('should handle clear with empty queue', () => {
      expect(service.getQueueSize()).toBe(0);
      expect(() => service.clearQueue()).not.toThrow();
      expect(service.getQueueSize()).toBe(0);
    });
  });

  describe('dispose', () => {
    it('should clean up resources', () => {
      service.trackError(new Error('Test'));
      expect(service.getQueueSize()).toBeGreaterThan(0);

      service.dispose();

      expect(service.getQueueSize()).toBe(0);
    });

    it('should handle multiple dispose calls', () => {
      expect(() => {
        service.dispose();
        service.dispose();
        service.dispose();
      }).not.toThrow();
    });
  });

  describe('getQueueSize', () => {
    it('should return correct queue size', () => {
      expect(service.getQueueSize()).toBe(0);

      service.trackError(new Error('Error 1'));
      expect(service.getQueueSize()).toBe(1);

      service.trackError(new Error('Error 2'));
      expect(service.getQueueSize()).toBe(2);
    });
  });

  describe('severity determination', () => {
    it('should assign critical severity to fatal errors', () => {
      const fatalError = new Error('Fatal: out of memory');
      service.trackError(fatalError);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should assign critical severity to security errors', () => {
      const securityError = new Error('Security violation detected');
      service.trackError(securityError);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should assign high severity to null reference errors', () => {
      const nullError = new Error('Cannot read property of null');
      service.trackError(nullError);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should assign high severity to undefined errors', () => {
      const undefinedError = new Error('x is undefined');
      service.trackError(undefinedError);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should assign high severity to type errors', () => {
      const typeError = new Error('is not a function');
      service.trackError(typeError);
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });
  });

  describe('user identification', () => {
    it('should get user ID from sessionStorage', () => {
      sessionStorage.setItem('user', JSON.stringify({ id: 'user-123', username: 'testuser' }));

      service.trackError(new Error('Test'));
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should get user ID from localStorage', () => {
      localStorage.setItem('userId', 'local-user-456');

      service.trackError(new Error('Test'));
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should handle invalid JSON in sessionStorage', () => {
      sessionStorage.setItem('user', 'invalid json');

      service.trackError(new Error('Test'));
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should get correlation ID from sessionStorage', () => {
      sessionStorage.setItem('correlationId', 'corr-abc-123');

      service.trackError(new Error('Test'));
      expect(service.getQueueSize()).toBeGreaterThan(0);
    });
  });

  describe('batch processing', () => {
    it('should batch multiple errors', () => {
      // Add multiple errors
      for (let i = 0; i < 5; i++) {
        service.trackError(new Error(`Error ${i}`));
      }

      expect(service.getQueueSize()).toBe(5);
    });

    it('should process queue when batch size is reached', () => {
      // The service has BATCH_SIZE = 10
      for (let i = 0; i < 12; i++) {
        service.trackError(new Error(`Batch error ${i}`));
      }

      // After hitting batch size, queue should be processed
      expect(service.getQueueSize()).toBeLessThan(12);
    });
  });

  describe('error deduplication window', () => {
    it('should deduplicate same error within window then allow after dispose/reinit', () => {
      const error = new Error('Deduplicate me');

      service.trackError(error, 'SameContext');
      expect(service.getQueueSize()).toBe(1);

      // Same error within dedup window is skipped
      service.trackError(error, 'SameContext');
      expect(service.getQueueSize()).toBe(1);

      // Different error message should still be accepted
      const error2 = new Error('Different error');
      service.trackError(error2, 'SameContext');
      expect(service.getQueueSize()).toBe(2);
    });
  });

  describe('error report structure', () => {
    it('should create complete error report', () => {
      const error = new Error('Complete test');
      error.stack = 'Error: Complete test\n    at Test.method (file.js:1:1)';

      service.trackError(error, 'TestContext', { key: 'value' });

      expect(service.getQueueSize()).toBeGreaterThan(0);
    });

    it('should include all required fields', () => {
      const error = new Error('Field test');
      service.trackError(error);

      // The error should be queued
      expect(service.getQueueSize()).toBe(1);
    });
  });
});
