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
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProjectService } from '../../../core/services/project.service';
import { Project, ProjectStatus } from '../../../core/models';

type StatusFilter = ProjectStatus | 'ALL';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatMenuModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './project-list.html',
  styleUrl: './project-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectList implements OnInit, OnDestroy {
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();
  private readonly searchInput$ = new Subject<string>();

  readonly projects = signal<Project[]>([]);
  readonly loading = signal(false);
  readonly searchTerm = signal('');
  readonly statusFilter = signal<StatusFilter>('ACTIVE');

  readonly statusOptions: { label: string; value: StatusFilter }[] = [
    { label: 'Active', value: 'ACTIVE' },
    { label: 'Archived', value: 'ARCHIVED' },
    { label: 'All', value: 'ALL' }
  ];

  readonly filteredProjects = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const list = this.projects();
    if (!term) return list;
    return list.filter(
      p =>
        p.name.toLowerCase().includes(term) ||
        (p.description ?? '').toLowerCase().includes(term) ||
        (p.baseUri ?? '').toLowerCase().includes(term)
    );
  });

  ngOnInit(): void {
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(term => this.searchTerm.set(term));

    this.loadProjects();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchChange(value: string): void {
    this.searchInput$.next(value);
  }

  onStatusChange(value: StatusFilter): void {
    this.statusFilter.set(value);
    this.loadProjects();
  }

  loadProjects(): void {
    this.loading.set(true);
    const filter = this.statusFilter();
    const statusParam = filter === 'ALL' ? undefined : filter;
    this.projectService
      .list(statusParam)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: projects => {
          this.projects.set(projects);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snackBar.open('Failed to load projects', 'Dismiss', { duration: 4000 });
        }
      });
  }

  createProject(): void {
    this.router.navigate(['/projects/new']);
  }

  openProject(project: Project): void {
    this.router.navigate(['/projects', project.id]);
  }

  editProject(project: Project, event?: Event): void {
    event?.stopPropagation();
    this.router.navigate(['/projects', project.id, 'edit']);
  }

  archiveProject(project: Project, event?: Event): void {
    event?.stopPropagation();
    this.projectService
      .archive(project.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.projects.update(list => list.map(p => (p.id === updated.id ? updated : p)));
          this.snackBar.open(`"${updated.name}" archived`, 'Dismiss', { duration: 3000 });
          this.loadProjects();
        },
        error: () => {
          this.snackBar.open('Failed to archive project', 'Dismiss', { duration: 4000 });
        }
      });
  }

  unarchiveProject(project: Project, event?: Event): void {
    event?.stopPropagation();
    this.projectService
      .unarchive(project.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.projects.update(list => list.map(p => (p.id === updated.id ? updated : p)));
          this.snackBar.open(`"${updated.name}" unarchived`, 'Dismiss', { duration: 3000 });
          this.loadProjects();
        },
        error: () => {
          this.snackBar.open('Failed to unarchive project', 'Dismiss', { duration: 4000 });
        }
      });
  }

  deleteProject(project: Project, event?: Event): void {
    event?.stopPropagation();
    const confirmed = window.confirm(
      `Delete project "${project.name}"? This cannot be undone.`
    );
    if (!confirmed) return;

    this.projectService
      .delete(project.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.projects.update(list => list.filter(p => p.id !== project.id));
          this.snackBar.open(`"${project.name}" deleted`, 'Dismiss', { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Failed to delete project', 'Dismiss', { duration: 4000 });
        }
      });
  }

  getStatusClass(status: ProjectStatus): string {
    return status === 'ACTIVE' ? 'status-active' : 'status-archived';
  }

  trackByProjectId(_index: number, project: Project): string {
    return project.id;
  }
}
