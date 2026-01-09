import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { ConfirmationService, ConfirmDialogData } from './confirmation.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

describe('ConfirmationService', () => {
  let service: ConfirmationService;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent, boolean>>;

  beforeEach(() => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    dialogSpy.open.and.returnValue(dialogRefSpy);

    TestBed.configureTestingModule({
      providers: [
        ConfirmationService,
        { provide: MatDialog, useValue: dialogSpy }
      ]
    });
    service = TestBed.inject(ConfirmationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('confirm()', () => {
    it('should open dialog with provided data', () => {
      const data: ConfirmDialogData = {
        title: 'Delete Item',
        message: 'Are you sure you want to delete this item?'
      };

      dialogRefSpy.afterClosed.and.returnValue(of(true));

      service.confirm(data).subscribe();

      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialogComponent,
        jasmine.objectContaining({
          width: '400px',
          data: {
            title: 'Delete Item',
            message: 'Are you sure you want to delete this item?',
            confirmText: 'Confirm',
            cancelText: 'Cancel',
            confirmColor: 'primary'
          }
        })
      );
    });

    it('should use custom confirm and cancel text', () => {
      const data: ConfirmDialogData = {
        title: 'Delete',
        message: 'Are you sure?',
        confirmText: 'Yes, Delete',
        cancelText: 'No, Keep It',
        confirmColor: 'warn'
      };

      dialogRefSpy.afterClosed.and.returnValue(of(true));

      service.confirm(data).subscribe();

      expect(dialogSpy.open).toHaveBeenCalledWith(
        ConfirmDialogComponent,
        jasmine.objectContaining({
          data: {
            title: 'Delete',
            message: 'Are you sure?',
            confirmText: 'Yes, Delete',
            cancelText: 'No, Keep It',
            confirmColor: 'warn'
          }
        })
      );
    });

    it('should return true when confirmed', () => {
      dialogRefSpy.afterClosed.and.returnValue(of(true));

      service.confirm({ title: 'Test', message: 'Test?' }).subscribe(result => {
        expect(result).toBeTrue();
      });
    });

    it('should return false when cancelled', () => {
      dialogRefSpy.afterClosed.and.returnValue(of(false));

      service.confirm({ title: 'Test', message: 'Test?' }).subscribe(result => {
        expect(result).toBeFalse();
      });
    });

    it('should return undefined when dialog is dismissed', () => {
      dialogRefSpy.afterClosed.and.returnValue(of(undefined));

      service.confirm({ title: 'Test', message: 'Test?' }).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});
