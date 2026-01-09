import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ConfirmDialogData } from '../../../core/services/confirmation.service';

describe('ConfirmDialogComponent', () => {
  let component: ConfirmDialogComponent;
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent>>;

  const mockData: ConfirmDialogData = {
    title: 'Delete Item',
    message: 'Are you sure you want to delete this item?',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    confirmColor: 'warn'
  };

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display title and message', () => {
    const titleEl = fixture.nativeElement.querySelector('h2');
    const messageEl = fixture.nativeElement.querySelector('p');

    expect(titleEl.textContent).toBe('Delete Item');
    expect(messageEl.textContent).toBe('Are you sure you want to delete this item?');
  });

  it('should display custom button text', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button');

    expect(buttons[0].textContent.trim()).toBe('Cancel');
    expect(buttons[1].textContent.trim()).toBe('Delete');
  });

  it('should close dialog with false when cancelled', () => {
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });

  it('should close dialog with true when confirmed', () => {
    component.onConfirm();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should close dialog when cancel button clicked', () => {
    const cancelBtn = fixture.nativeElement.querySelector('button');
    cancelBtn.click();

    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });

  it('should close dialog when confirm button clicked', () => {
    const confirmBtn = fixture.nativeElement.querySelectorAll('button')[1];
    confirmBtn.click();

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });
});
