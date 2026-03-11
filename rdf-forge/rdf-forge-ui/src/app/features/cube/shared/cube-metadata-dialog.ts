import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import {
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';

import { Cube } from '../../../core/models/cube.model';

export interface CubeMetadataDialogData {
  mode: 'create' | 'edit';
  cube?: Cube;
}

export interface CubeMetadataDialogResult {
  name: string;
  description?: string;
  uri?: string;
  publisherUri?: string;
  theme?: string;
  contactPoint?: string;
}

@Component({
  selector: 'app-cube-metadata-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      {{ data.mode === 'create' ? 'Create New Cube' : 'Edit Cube Metadata' }}
    </h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="metadata-form">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" placeholder="Cube name" />
          @if (form.controls['name'].hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea
            matInput
            formControlName="description"
            placeholder="Describe this cube"
            rows="3"
          ></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Publisher URI</mat-label>
          <input
            matInput
            formControlName="publisherUri"
            placeholder="https://example.org/publisher"
          />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Theme (dcat:theme)</mat-label>
          <input
            matInput
            formControlName="theme"
            placeholder="https://example.org/theme"
          />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Contact Point</mat-label>
          <input
            matInput
            formControlName="contactPoint"
            placeholder="Contact email or URI"
          />
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        [disabled]="form.invalid"
        (click)="submit()"
      >
        {{ data.mode === 'create' ? 'Create' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .metadata-form {
      display: flex;
      flex-direction: column;
      min-width: 400px;
      gap: 4px;
    }

    mat-form-field {
      width: 100%;
    }
  `],
})
export class CubeMetadataDialog implements OnInit {
  readonly data: CubeMetadataDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<CubeMetadataDialog>);
  private readonly fb = inject(FormBuilder);

  form!: FormGroup;

  ngOnInit(): void {
    const cube = this.data.cube;
    const metadata = cube?.metadata ?? {};

    this.form = this.fb.group({
      name: [cube?.name ?? '', Validators.required],
      description: [cube?.description ?? ''],
      publisherUri: [(metadata as Record<string, unknown>)['publisherUri'] ?? ''],
      theme: [(metadata as Record<string, unknown>)['theme'] ?? ''],
      contactPoint: [(metadata as Record<string, unknown>)['contactPoint'] ?? ''],
    });
  }

  submit(): void {
    if (this.form.invalid) return;

    const value = this.form.value;
    const result: CubeMetadataDialogResult = {
      name: value.name,
      description: value.description || undefined,
      publisherUri: value.publisherUri || undefined,
      theme: value.theme || undefined,
      contactPoint: value.contactPoint || undefined,
    };

    if (this.data.mode === 'create') {
      result.uri = this.generateUri(value.name);
    }

    this.dialogRef.close(result);
  }

  // TODO: Base URI should come from environment config or project settings
  private generateUri(name: string): string {
    const slug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');
    return `https://cube.example.org/${slug}`;
  }
}
