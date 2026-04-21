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
  ActivatedRoute,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectContextService } from '../services/project-context.service';
import { ProjectStatus, ProjectSummary } from '../../../core/models';

interface WorkspaceTab {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-project-workspace',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatChipsModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './project-workspace.html',
  styleUrl: './project-workspace.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectWorkspace implements OnInit, OnDestroy {
  private readonly projectService = inject(ProjectService);
  private readonly context = inject(ProjectContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly summary = this.context.currentSummary;

  readonly tabs: WorkspaceTab[] = [
    { path: 'overview',   label: 'Overview',   icon: 'dashboard' },
    { path: 'data',       label: 'Data',       icon: 'storage' },
    { path: 'ontology',   label: 'Ontology',   icon: 'schema' },
    { path: 'mapping',    label: 'Mapping',    icon: 'transform' },
    { path: 'validation', label: 'Validation', icon: 'verified' },
    { path: 'publish',    label: 'Publish',    icon: 'cloud_upload' },
    { path: 'lineage',    label: 'Lineage',    icon: 'account_tree' },
    { path: 'docs',       label: 'Docs',       icon: 'description' }
  ];

  readonly statusClass = computed(() => {
    const status = this.summary()?.status;
    return status === 'ARCHIVED' ? 'status-archived' : 'status-active';
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/projects']);
      return;
    }
    this.loadSummary(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.context.clear();
  }

  private loadSummary(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.projectService
      .summary(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (summary: ProjectSummary) => {
          this.context.setSummary(summary);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load project.');
          this.loading.set(false);
        }
      });
  }

  retry(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadSummary(id);
  }

  editProject(): void {
    const id = this.summary()?.id;
    if (id) this.router.navigate(['/projects', id, 'edit']);
  }

  archiveProject(): void {
    const project = this.summary();
    if (!project) return;
    this.projectService
      .archive(project.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.context.setProject(updated);
          this.snackBar.open('Project archived', 'Dismiss', { duration: 3000 });
        },
        error: () =>
          this.snackBar.open('Failed to archive project', 'Dismiss', { duration: 4000 })
      });
  }

  unarchiveProject(): void {
    const project = this.summary();
    if (!project) return;
    this.projectService
      .unarchive(project.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.context.setProject(updated);
          this.snackBar.open('Project unarchived', 'Dismiss', { duration: 3000 });
        },
        error: () =>
          this.snackBar.open('Failed to unarchive project', 'Dismiss', { duration: 4000 })
      });
  }

  backToList(): void {
    this.router.navigate(['/projects']);
  }

  statusLabel(status: ProjectStatus | undefined): string {
    return status ?? 'ACTIVE';
  }
}
