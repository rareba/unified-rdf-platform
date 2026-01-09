import { TestBed } from '@angular/core/testing';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';
import { Subject } from 'rxjs';

describe('NotificationService', () => {
  let service: NotificationService;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;
  let snackBarRefSpy: jasmine.SpyObj<MatSnackBarRef<TextOnlySnackBar>>;

  beforeEach(() => {
    const onActionSubject = new Subject<void>();
    snackBarRefSpy = jasmine.createSpyObj('MatSnackBarRef', ['dismiss', 'onAction']);
    snackBarRefSpy.onAction.and.returnValue(onActionSubject.asObservable());

    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open', 'dismiss']);
    snackBarSpy.open.and.returnValue(snackBarRefSpy as MatSnackBarRef<TextOnlySnackBar>);

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    });

    service = TestBed.inject(NotificationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('success', () => {
    it('should show success notification', () => {
      service.success('Operation completed');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Operation completed',
        'Close',
        jasmine.objectContaining({
          panelClass: jasmine.arrayContaining(['notification-success'])
        })
      );
    });

    it('should use default duration of 3000ms', () => {
      service.success('Test');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Test',
        'Close',
        jasmine.objectContaining({ duration: 3000 })
      );
    });
  });

  describe('error', () => {
    it('should show error notification', () => {
      service.error('Something went wrong');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Something went wrong',
        'Close',
        jasmine.objectContaining({
          panelClass: jasmine.arrayContaining(['notification-error'])
        })
      );
    });

    it('should show retry button when callback provided', () => {
      const retryFn = jasmine.createSpy('retryFn');
      service.error('Failed to load', retryFn);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Failed to load',
        'Retry',
        jasmine.any(Object)
      );
    });

    it('should use longer duration for retry notifications', () => {
      service.error('Failed', () => {});

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        jasmine.any(String),
        jasmine.any(String),
        jasmine.objectContaining({ duration: undefined }) // No auto-dismiss when action present
      );
    });
  });

  describe('warning', () => {
    it('should show warning notification', () => {
      service.warning('Be careful');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Be careful',
        'Close',
        jasmine.objectContaining({
          panelClass: jasmine.arrayContaining(['notification-warning'])
        })
      );
    });
  });

  describe('info', () => {
    it('should show info notification', () => {
      service.info('For your information');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'For your information',
        'Close',
        jasmine.objectContaining({
          panelClass: jasmine.arrayContaining(['notification-info'])
        })
      );
    });
  });

  describe('show', () => {
    it('should accept custom options', () => {
      service.show('Custom message', 'info', {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'top'
      });

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Custom message',
        'Close',
        jasmine.objectContaining({
          horizontalPosition: 'center',
          verticalPosition: 'top'
        })
      );
    });

    it('should support custom action with callback', () => {
      const callback = jasmine.createSpy('callback');

      service.show('Message', 'info', {
        action: { label: 'Undo', callback }
      });

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Message',
        'Undo',
        jasmine.any(Object)
      );
    });
  });

  describe('showLoading', () => {
    it('should show loading notification without auto-dismiss', () => {
      service.showLoading('Loading...');

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Loading...',
        undefined,
        jasmine.objectContaining({
          duration: undefined,
          panelClass: ['notification-loading']
        })
      );
    });
  });

  describe('dismissAll', () => {
    it('should dismiss all notifications', () => {
      service.dismissAll();
      expect(snackBarSpy.dismiss).toHaveBeenCalled();
    });
  });
});
