import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProjectService } from '../../../core/services/project.service';
import { Project, ProjectCreateRequest, ProjectUpdateRequest } from '../../../core/models';

/**
 * Validator for a baseUri — must be a valid absolute URL (http/https).
 */
function urlValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value?.trim();
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return { invalidUrl: true };
    }
    return null;
  } catch {
    return { invalidUrl: true };
  }
}

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './project-form.html',
  styleUrl: './project-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectForm implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly projectId = signal<string | null>(null);
  readonly isEditMode = computed(() => this.projectId() !== null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    baseUri: ['', [Validators.required, urlValidator]]
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.projectId.set(id);
      this.loadProject(id);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadProject(id: string): void {
    this.loading.set(true);
    this.projectService
      .get(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: project => {
          this.form.patchValue({
            name: project.name,
            description: project.description,
            baseUri: project.baseUri
          });
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snackBar.open('Failed to load project', 'Dismiss', { duration: 4000 });
          this.router.navigate(['/projects']);
        }
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const payload: ProjectCreateRequest | ProjectUpdateRequest = {
      name: value.name.trim(),
      description: value.description.trim() || undefined,
      baseUri: value.baseUri.trim()
    };

    this.saving.set(true);
    const id = this.projectId();
    const request$ = id
      ? this.projectService.update(id, payload as ProjectUpdateRequest)
      : this.projectService.create(payload as ProjectCreateRequest);

    request$.pipe(takeUntil(this.destroy$)).subscribe({
      next: (project: Project) => {
        this.saving.set(false);
        const message = id ? 'Project updated' : 'Project created';
        this.snackBar.open(message, 'Dismiss', { duration: 3000 });
        this.router.navigate(['/projects', project.id]);
      },
      error: () => {
        this.saving.set(false);
        const message = id ? 'Failed to update project' : 'Failed to create project';
        this.snackBar.open(message, 'Dismiss', { duration: 4000 });
      }
    });
  }

  cancel(): void {
    const id = this.projectId();
    if (id) {
      this.router.navigate(['/projects', id]);
    } else {
      this.router.navigate(['/projects']);
    }
  }

  get nameError(): string | null {
    const ctrl = this.form.controls.name;
    if (!(ctrl.dirty || ctrl.touched)) return null;
    if (ctrl.hasError('required')) return 'Name is required';
    if (ctrl.hasError('maxlength')) return 'Name must be 255 characters or fewer';
    return null;
  }

  get baseUriError(): string | null {
    const ctrl = this.form.controls.baseUri;
    if (!(ctrl.dirty || ctrl.touched)) return null;
    if (ctrl.hasError('required')) return 'Base URI is required';
    if (ctrl.hasError('invalidUrl')) return 'Must be a valid http(s) URL';
    return null;
  }
}
