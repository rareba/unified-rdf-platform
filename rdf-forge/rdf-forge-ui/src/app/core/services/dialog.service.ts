import { Injectable } from '@angular/core';
import { MatDialog, MatDialogRef, MatDialogConfig } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';

/**
 * Service for managing dialogs (confirmations, alerts, etc.)
 * Uses ngx-wig for consistent dialog styling
 * @obsolete Note: MatDialog is deprecated. Consider migrating to NgxWigDialogService.
 */
@Injectable({
  providedIn: 'root'
})
export class DialogService {
  constructor(private dialog: MatDialog) {}

  /**
   * Opens a confirmation dialog
   * @param data Dialog configuration data
   * @returns Observable that emits true if confirmed, false otherwise
   */
  confirm(data: ConfirmDialogData): Observable<boolean> {
    const dialogRef: MatDialogRef<ConfirmDialogComponent, boolean> = this.dialog.open(
      ConfirmDialogComponent,
      {
        width: '400px',
        data,
        disableClose: true,
        autoFocus: 'dialog',
        ariaLabel: 'Confirmation dialog'
      }
    );

    return dialogRef.afterClosed();
  }

  /**
   * Opens an alert dialog
   * @param title Alert title
   * @param message Alert message
   * @returns Observable that completes when dialog is closed
   */
  alert(title: string, message: string): Observable<void> {
    const dialogRef: MatDialogRef<ConfirmDialogComponent, boolean> = this.dialog.open(
      ConfirmDialogComponent,
      {
        width: '400px',
        data: {
          title,
          message,
          confirmText: 'OK',
          requireConfirmation: false
        } as ConfirmDialogData,
        disableClose: false,
        autoFocus: 'dialog',
        ariaLabel: 'Alert dialog'
      }
    );

    return dialogRef.afterClosed().pipe(
      map(() => undefined)
    );
  }

  /**
   * Opens a custom dialog with the specified component
   * @param component Component to render in the dialog
   * @param config Dialog configuration
   * @returns MatDialogRef for the opened dialog
   */
  open<T, D = unknown, R = unknown>(
    component: ComponentType<T>,
    config?: MatDialogConfig<D>
  ): MatDialogRef<T, R> {
    return this.dialog.open(component, {
      disableClose: false,
      autoFocus: 'first-tabbable',
      ...config
    });
  }

  /**
   * Shortcut for opening input dialog
   * @param title Dialog title
   * @param message Prompt message
   * @param defaultValue Default input value
   * @returns Observable with the input value or undefined if cancelled
   */
  prompt(title: string, message: string, defaultValue = ''): Observable<string | undefined> {
    const dialogRef: MatDialogRef<PromptDialogComponent, string> = this.dialog.open(
      PromptDialogComponent,
      {
        width: '400px',
        data: { title, message, defaultValue },
        disableClose: true,
        autoFocus: 'dialog',
        ariaLabel: 'Input prompt dialog'
      }
    );

    return dialogRef.afterClosed();
  }
}

import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { CommonModule } from '@angular/common';
import { map } from 'rxjs/operators';
import { ComponentType } from '@angular/cdk/portal';

/**
 * Data for prompt dialog
 */
export interface PromptDialogData {
  title: string;
  message: string;
  defaultValue: string;
}

/**
 * Component for prompt dialog (input dialog)
 */
@Component({
  selector: 'app-prompt-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    FormsModule
  ],
  template: `
    <h2 mat-dialog-title role="heading" aria-level="2">{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      <mat-form-field appearance="outline" class="full-width">
        <input matInput [(ngModel)]="value" aria-label="Input value" (keyup.enter)="onSubmit()">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()" type="button">Cancel</button>
      <button mat-flat-button color="primary" (click)="onSubmit()" [disabled]="!value.trim()" type="button">
        OK
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width {
      width: 100%;
    }
    mat-dialog-content p {
      margin-top: 0;
    }
  `]
})
export class PromptDialogComponent {
  value: string;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PromptDialogData,
    private dialogRef: MatDialogRef<PromptDialogComponent, string>
  ) {
    this.value = data.defaultValue;
  }

  onSubmit(): void {
    if (this.value.trim()) {
      this.dialogRef.close(this.value);
    }
  }

  onCancel(): void {
    this.dialogRef.close(undefined);
  }
}
