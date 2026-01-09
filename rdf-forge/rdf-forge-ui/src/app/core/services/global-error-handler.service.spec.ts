import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { GlobalErrorHandlerService } from './global-error-handler.service';

describe('GlobalErrorHandlerService', () => {
  let service: GlobalErrorHandlerService;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [
        GlobalErrorHandlerService,
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    });

    service = TestBed.inject(GlobalErrorHandlerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should handle Error objects', () => {
    const error = new Error('Test error message');
    service.handleError(error);

    expect(snackBarSpy.open).toHaveBeenCalled();
    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toBe('Test error message');
  });

  it('should handle plain string errors', () => {
    service.handleError('String error');

    expect(snackBarSpy.open).toHaveBeenCalled();
    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toBe('String error');
  });

  it('should handle object errors with message property', () => {
    const error = { message: 'Object error message' };
    service.handleError(error);

    expect(snackBarSpy.open).toHaveBeenCalled();
    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toBe('Object error message');
  });

  it('should handle HTTP 401 errors', () => {
    const error = { status: 401 };
    service.handleError(error);

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toContain('Authentication');
  });

  it('should handle HTTP 403 errors', () => {
    const error = { status: 403 };
    service.handleError(error);

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toContain('Access denied');
  });

  it('should handle HTTP 404 errors', () => {
    const error = { status: 404 };
    service.handleError(error);

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toContain('not found');
  });

  it('should handle HTTP 500 errors', () => {
    const error = { status: 500 };
    service.handleError(error);

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toContain('Server error');
  });

  it('should handle network errors (status 0)', () => {
    const error = { status: 0 };
    service.handleError(error);

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage).toContain('Network error');
  });

  it('should log errors to error log', () => {
    service.handleError(new Error('Test 1'));
    service.handleError(new Error('Test 2'));

    const errors = service.getRecentErrors();
    expect(errors.length).toBe(2);
    expect(errors[0].message).toBe('Test 2'); // Most recent first
    expect(errors[1].message).toBe('Test 1');
  });

  it('should clear error log', () => {
    service.handleError(new Error('Test'));
    expect(service.getRecentErrors().length).toBe(1);

    service.clearErrors();
    expect(service.getRecentErrors().length).toBe(0);
  });

  it('should limit error log to 50 entries', () => {
    for (let i = 0; i < 60; i++) {
      service.handleError(new Error(`Error ${i}`));
    }

    expect(service.getRecentErrors().length).toBe(50);
  });

  it('should truncate long error messages', () => {
    const longMessage = 'A'.repeat(200);
    service.handleError(new Error(longMessage));

    const calledMessage = snackBarSpy.open.calls.mostRecent().args[0];
    expect(calledMessage.length).toBeLessThanOrEqual(150);
    expect(calledMessage.endsWith('...')).toBeTrue();
  });

  describe('getErrorMessage static method', () => {
    it('should return default message for null error', () => {
      expect(GlobalErrorHandlerService.getErrorMessage(null, 'Default')).toBe('Default');
    });

    it('should extract message from Error object', () => {
      const error = new Error('Test message');
      expect(GlobalErrorHandlerService.getErrorMessage(error)).toBe('Test message');
    });

    it('should handle HTTP status codes', () => {
      expect(GlobalErrorHandlerService.getErrorMessage({ status: 401 })).toBe('Authentication required');
      expect(GlobalErrorHandlerService.getErrorMessage({ status: 403 })).toBe('Access denied');
      expect(GlobalErrorHandlerService.getErrorMessage({ status: 404 })).toBe('Resource not found');
      expect(GlobalErrorHandlerService.getErrorMessage({ status: 500 })).toBe('Server error. Please try again later.');
    });

    it('should use server message when available', () => {
      const error = { status: 400, error: { message: 'Custom validation error' } };
      expect(GlobalErrorHandlerService.getErrorMessage(error)).toBe('Custom validation error');
    });
  });
});
