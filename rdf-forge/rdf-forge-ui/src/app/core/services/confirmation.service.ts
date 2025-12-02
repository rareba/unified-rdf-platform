import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  confirmColor?: 'primary' | 'accent' | 'warn';
}

@Injectable({
  providedIn: 'root'
})
export class ConfirmationService {
  private readonly dialog = inject(MatDialog);

  confirm(data: ConfirmDialogData): Observable<boolean> {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: data.title,
        message: data.message,
        confirmText: data.confirmText || 'Confirm',
        cancelText: data.cancelText || 'Cancel',
        confirmColor: data.confirmColor || 'primary'
      }
    });

    return dialogRef.afterClosed();
  }
}
