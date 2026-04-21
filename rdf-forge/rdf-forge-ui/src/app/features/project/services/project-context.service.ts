import { Injectable, computed, signal } from '@angular/core';
import { Project, ProjectSummary } from '../../../core/models';

/**
 * Holds the currently-active project for the Project Workspace.
 * Tabs read from `currentProject` / `currentSummary` to render scoped content.
 */
@Injectable({ providedIn: 'root' })
export class ProjectContextService {
  readonly currentProject = signal<Project | null>(null);
  readonly currentSummary = signal<ProjectSummary | null>(null);

  readonly projectId = computed(() => this.currentProject()?.id ?? null);
  readonly isLoaded = computed(() => this.currentProject() !== null);

  setSummary(summary: ProjectSummary): void {
    this.currentSummary.set(summary);
    this.currentProject.set(summary);
  }

  setProject(project: Project): void {
    this.currentProject.set(project);
    const existingSummary = this.currentSummary();
    if (existingSummary && existingSummary.id === project.id) {
      this.currentSummary.set({ ...existingSummary, ...project });
    }
  }

  clear(): void {
    this.currentProject.set(null);
    this.currentSummary.set(null);
  }
}
