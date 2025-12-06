import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export type GitProvider = 'GITHUB' | 'GITLAB';

export interface GitSyncConfig {
  id?: string;
  name: string;
  projectId?: string;
  provider: GitProvider;
  repositoryUrl: string;
  branch: string;
  accessToken?: string;
  configPath: string;
  syncPipelines: boolean;
  syncShapes: boolean;
  syncSettings: boolean;
  autoSync: boolean;
  syncIntervalMinutes?: number;
}

export interface GitSyncStatus {
  operationId: string;
  state: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  direction: 'PUSH' | 'PULL';
  startedAt: string;
  completedAt?: string;
  commitSha?: string;
  commitMessage?: string;
  syncedFiles: SyncedFile[];
  errors: string[];
  warnings: string[];
}

export interface SyncedFile {
  path: string;
  type: string;
  action: 'CREATED' | 'UPDATED' | 'DELETED' | 'UNCHANGED';
  resourceId?: string;
  resourceName?: string;
}

export interface ConnectionTestResult {
  connected: boolean;
  message: string;
}

/**
 * Service for managing Git sync configurations and operations.
 * Allows syncing pipelines, SHACL shapes, and settings with Git repositories.
 */
@Injectable({
  providedIn: 'root'
})
export class GitSyncService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/git-sync`;

  readonly configs = signal<GitSyncConfig[]>([]);
  readonly loading = signal(false);
  readonly syncing = signal(false);
  readonly currentStatus = signal<GitSyncStatus | null>(null);

  /**
   * Load all Git sync configurations.
   */
  loadConfigs(projectId?: string): Observable<GitSyncConfig[]> {
    this.loading.set(true);
    const url = projectId
      ? `${this.baseUrl}/configs?projectId=${projectId}`
      : `${this.baseUrl}/configs`;

    return this.http.get<GitSyncConfig[]>(url).pipe(
      tap(configs => {
        this.configs.set(configs);
        this.loading.set(false);
      }),
      catchError(err => {
        console.error('Failed to load Git sync configs', err);
        this.loading.set(false);
        return of([]);
      })
    );
  }

  /**
   * Get a single Git sync configuration.
   */
  getConfig(id: string): Observable<GitSyncConfig> {
    return this.http.get<GitSyncConfig>(`${this.baseUrl}/configs/${id}`);
  }

  /**
   * Create a new Git sync configuration.
   */
  createConfig(config: GitSyncConfig): Observable<GitSyncConfig> {
    return this.http.post<GitSyncConfig>(`${this.baseUrl}/configs`, config).pipe(
      tap(created => {
        this.configs.update(configs => [...configs, created]);
      })
    );
  }

  /**
   * Update a Git sync configuration.
   */
  updateConfig(id: string, config: GitSyncConfig): Observable<GitSyncConfig> {
    return this.http.put<GitSyncConfig>(`${this.baseUrl}/configs/${id}`, config).pipe(
      tap(updated => {
        this.configs.update(configs =>
          configs.map(c => c.id === id ? updated : c)
        );
      })
    );
  }

  /**
   * Delete a Git sync configuration.
   */
  deleteConfig(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/configs/${id}`).pipe(
      tap(() => {
        this.configs.update(configs => configs.filter(c => c.id !== id));
      })
    );
  }

  /**
   * Test connection to a Git repository.
   */
  testConnection(config: Partial<GitSyncConfig>): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>(`${this.baseUrl}/test-connection`, config);
  }

  /**
   * Push configurations to Git repository.
   */
  push(configId: string, commitMessage?: string): Observable<GitSyncStatus> {
    this.syncing.set(true);
    const body = commitMessage ? { commitMessage } : {};

    return this.http.post<GitSyncStatus>(`${this.baseUrl}/configs/${configId}/push`, body).pipe(
      tap(status => {
        this.currentStatus.set(status);
        this.syncing.set(false);
      }),
      catchError(err => {
        console.error('Push failed', err);
        this.syncing.set(false);
        throw err;
      })
    );
  }

  /**
   * Pull configurations from Git repository.
   */
  pull(configId: string, dryRun = false): Observable<GitSyncStatus> {
    this.syncing.set(true);

    return this.http.post<GitSyncStatus>(
      `${this.baseUrl}/configs/${configId}/pull?dryRun=${dryRun}`,
      {}
    ).pipe(
      tap(status => {
        this.currentStatus.set(status);
        this.syncing.set(false);
      }),
      catchError(err => {
        console.error('Pull failed', err);
        this.syncing.set(false);
        throw err;
      })
    );
  }

  /**
   * Get status of a sync operation.
   */
  getSyncStatus(operationId: string): Observable<GitSyncStatus> {
    return this.http.get<GitSyncStatus>(`${this.baseUrl}/status/${operationId}`);
  }
}
