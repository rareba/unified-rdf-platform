import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { GitSyncService, GitSyncConfig, GitSyncStatus, GitProvider } from '../../../core/services/git-sync.service';

@Component({
  selector: 'app-git-sync',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    MatDialogModule,
    MatExpansionModule
  ],
  template: `
    <div class="admin-container">
      <!-- Header -->
      <mat-card class="header-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>sync</mat-icon>
            Git Configuration Sync
          </mat-card-title>
          <mat-card-subtitle>
            Sync pipelines, SHACL shapes, and settings with Git repositories (GitHub/GitLab)
          </mat-card-subtitle>
        </mat-card-header>
      </mat-card>

      <!-- Add New Configuration -->
      <mat-card class="config-form-card">
        <mat-card-header>
          <mat-card-title>{{ editingConfig() ? 'Edit' : 'Add' }} Repository</mat-card-title>
        </mat-card-header>

        <mat-card-content>
          <form class="config-form" (ngSubmit)="saveConfig()">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Name</mat-label>
                <input matInput [(ngModel)]="formConfig.name" name="name" required placeholder="My Config Repo">
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Provider</mat-label>
                <mat-select [(ngModel)]="formConfig.provider" name="provider" required>
                  <mat-option value="GITHUB">GitHub</mat-option>
                  <mat-option value="GITLAB">GitLab</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Repository URL</mat-label>
                <input matInput [(ngModel)]="formConfig.repositoryUrl" name="repositoryUrl" required
                       placeholder="https://github.com/org/repo or https://gitlab.com/group/project">
                <mat-hint>Full URL to your Git repository</mat-hint>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Branch</mat-label>
                <input matInput [(ngModel)]="formConfig.branch" name="branch" placeholder="main">
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Config Path</mat-label>
                <input matInput [(ngModel)]="formConfig.configPath" name="configPath" placeholder="config">
                <mat-hint>Directory in repo for configs</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Access Token</mat-label>
                <input matInput [(ngModel)]="formConfig.accessToken" name="accessToken" type="password"
                       [placeholder]="editingConfig() ? '(unchanged)' : 'ghp_... or glpat-...'">
              </mat-form-field>
            </div>

            <div class="form-row toggles">
              <mat-slide-toggle [(ngModel)]="formConfig.syncPipelines" name="syncPipelines">
                Sync Pipelines
              </mat-slide-toggle>
              <mat-slide-toggle [(ngModel)]="formConfig.syncShapes" name="syncShapes">
                Sync SHACL Shapes
              </mat-slide-toggle>
              <mat-slide-toggle [(ngModel)]="formConfig.syncSettings" name="syncSettings">
                Sync Settings
              </mat-slide-toggle>
            </div>

            <div class="form-row">
              <mat-slide-toggle [(ngModel)]="formConfig.autoSync" name="autoSync">
                Auto-Sync (Pull periodically)
              </mat-slide-toggle>

              @if (formConfig.autoSync) {
                <mat-form-field appearance="outline" class="interval-field">
                  <mat-label>Interval (minutes)</mat-label>
                  <input matInput type="number" [(ngModel)]="formConfig.syncIntervalMinutes"
                         name="syncIntervalMinutes" min="5" max="1440">
                </mat-form-field>
              }
            </div>

            <div class="form-actions">
              <button mat-button type="button" (click)="testConnection()" [disabled]="testing()">
                @if (testing()) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  <mat-icon>wifi_tethering</mat-icon>
                }
                Test Connection
              </button>

              @if (editingConfig()) {
                <button mat-button type="button" (click)="cancelEdit()">Cancel</button>
              }

              <button mat-raised-button color="primary" type="submit"
                      [disabled]="!formConfig.name || !formConfig.repositoryUrl">
                <mat-icon>{{ editingConfig() ? 'save' : 'add' }}</mat-icon>
                {{ editingConfig() ? 'Update' : 'Add' }} Repository
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Configured Repositories -->
      <mat-card class="repos-card">
        <mat-card-header>
          <mat-card-title>Configured Repositories</mat-card-title>
        </mat-card-header>

        <mat-card-content>
          @if (gitSyncService.loading()) {
            <div class="loading-container">
              <mat-spinner diameter="40"></mat-spinner>
              <p>Loading configurations...</p>
            </div>
          } @else if (gitSyncService.configs().length === 0) {
            <div class="no-data">
              <mat-icon>folder_off</mat-icon>
              <p>No repositories configured</p>
              <p class="hint">Add a repository above to start syncing configurations</p>
            </div>
          } @else {
            <mat-accordion>
              @for (config of gitSyncService.configs(); track config.id) {
                <mat-expansion-panel>
                  <mat-expansion-panel-header>
                    <mat-panel-title>
                      <mat-icon class="provider-icon">{{ config.provider === 'GITHUB' ? 'code' : 'merge' }}</mat-icon>
                      {{ config.name }}
                    </mat-panel-title>
                    <mat-panel-description>
                      {{ config.repositoryUrl }}
                      @if (config.autoSync) {
                        <mat-chip color="accent" class="auto-sync-chip">Auto-sync</mat-chip>
                      }
                    </mat-panel-description>
                  </mat-expansion-panel-header>

                  <div class="repo-details">
                    <div class="detail-row">
                      <span class="label">Branch:</span>
                      <span class="value">{{ config.branch }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="label">Config Path:</span>
                      <span class="value">{{ config.configPath }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="label">Synced Resources:</span>
                      <span class="value">
                        @if (config.syncPipelines) {
                          <mat-chip>Pipelines</mat-chip>
                        }
                        @if (config.syncShapes) {
                          <mat-chip>Shapes</mat-chip>
                        }
                        @if (config.syncSettings) {
                          <mat-chip>Settings</mat-chip>
                        }
                      </span>
                    </div>
                  </div>

                  <mat-action-row>
                    <button mat-button (click)="pullFromGit(config)" [disabled]="gitSyncService.syncing()">
                      <mat-icon>cloud_download</mat-icon>
                      Pull
                    </button>
                    <button mat-button (click)="pushToGit(config)" [disabled]="gitSyncService.syncing()">
                      <mat-icon>cloud_upload</mat-icon>
                      Push
                    </button>
                    <button mat-button (click)="editConfig(config)">
                      <mat-icon>edit</mat-icon>
                      Edit
                    </button>
                    <button mat-button color="warn" (click)="deleteConfig(config)">
                      <mat-icon>delete</mat-icon>
                      Delete
                    </button>
                  </mat-action-row>
                </mat-expansion-panel>
              }
            </mat-accordion>
          }
        </mat-card-content>
      </mat-card>

      <!-- Sync Status -->
      @if (gitSyncService.currentStatus()) {
        <mat-card class="status-card">
          <mat-card-header>
            <mat-card-title>
              <mat-icon>{{ getStatusIcon(gitSyncService.currentStatus()!) }}</mat-icon>
              Last Sync: {{ gitSyncService.currentStatus()!.direction }}
            </mat-card-title>
          </mat-card-header>

          <mat-card-content>
            @if (gitSyncService.currentStatus()!.state === 'IN_PROGRESS') {
              <mat-progress-bar mode="indeterminate"></mat-progress-bar>
            }

            <div class="status-details">
              <div class="detail-row">
                <span class="label">Status:</span>
                <mat-chip [color]="getStatusColor(gitSyncService.currentStatus()!.state)">
                  {{ gitSyncService.currentStatus()!.state }}
                </mat-chip>
              </div>
              @if (gitSyncService.currentStatus()!.commitSha) {
                <div class="detail-row">
                  <span class="label">Commit:</span>
                  <code>{{ gitSyncService.currentStatus()!.commitSha?.substring(0, 7) }}</code>
                </div>
              }
            </div>

            @if (gitSyncService.currentStatus()!.syncedFiles.length > 0) {
              <h4>Synced Files</h4>
              <table mat-table [dataSource]="gitSyncService.currentStatus()!.syncedFiles" class="synced-files-table">
                <ng-container matColumnDef="path">
                  <th mat-header-cell *matHeaderCellDef>Path</th>
                  <td mat-cell *matCellDef="let file">{{ file.path }}</td>
                </ng-container>
                <ng-container matColumnDef="type">
                  <th mat-header-cell *matHeaderCellDef>Type</th>
                  <td mat-cell *matCellDef="let file">{{ file.type }}</td>
                </ng-container>
                <ng-container matColumnDef="action">
                  <th mat-header-cell *matHeaderCellDef>Action</th>
                  <td mat-cell *matCellDef="let file">
                    <mat-chip [color]="getActionColor(file.action)">{{ file.action }}</mat-chip>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['path', 'type', 'action']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['path', 'type', 'action']"></tr>
              </table>
            }

            @if (gitSyncService.currentStatus()!.errors.length > 0) {
              <div class="errors">
                <h4>Errors</h4>
                @for (error of gitSyncService.currentStatus()!.errors; track error) {
                  <div class="error-item">{{ error }}</div>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .admin-container {
      padding: 24px;
      max-width: 1200px;
      margin: 0 auto;
      display: grid;
      gap: 24px;
    }

    .header-card mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .config-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .form-row {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
    }

    .form-row mat-form-field {
      flex: 1;
      min-width: 200px;
    }

    .form-row.full-width mat-form-field {
      width: 100%;
    }

    .full-width {
      flex-basis: 100%;
    }

    .form-row.toggles {
      gap: 24px;
    }

    .interval-field {
      max-width: 150px;
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 8px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px;
      gap: 16px;
    }

    .no-data {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px;
      color: var(--mdc-theme-text-secondary-on-background);
    }

    .no-data mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
    }

    .no-data .hint {
      font-size: 14px;
    }

    .provider-icon {
      margin-right: 8px;
    }

    .auto-sync-chip {
      font-size: 10px;
      margin-left: 8px;
    }

    .repo-details {
      padding: 16px 0;
    }

    .detail-row {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
      align-items: center;
    }

    .detail-row .label {
      font-weight: 500;
      min-width: 120px;
    }

    .status-card {
      border-left: 4px solid;
    }

    .status-card mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .status-details {
      padding: 16px 0;
    }

    .synced-files-table {
      width: 100%;
      margin-top: 16px;
    }

    .errors {
      margin-top: 16px;
      padding: 16px;
      background: #ffebee;
      border-radius: 4px;
    }

    .error-item {
      color: #c62828;
      margin-bottom: 8px;
    }
  `]
})
export class GitSyncComponent implements OnInit {
  readonly gitSyncService = inject(GitSyncService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly testing = signal(false);
  readonly editingConfig = signal<GitSyncConfig | null>(null);

  formConfig: Partial<GitSyncConfig> = this.getEmptyConfig();

  ngOnInit(): void {
    this.gitSyncService.loadConfigs().subscribe();
  }

  getEmptyConfig(): Partial<GitSyncConfig> {
    return {
      name: '',
      provider: 'GITHUB',
      repositoryUrl: '',
      branch: 'main',
      configPath: 'config',
      accessToken: '',
      syncPipelines: true,
      syncShapes: true,
      syncSettings: true,
      autoSync: false,
      syncIntervalMinutes: 60
    };
  }

  testConnection(): void {
    if (!this.formConfig.repositoryUrl || !this.formConfig.accessToken) {
      this.snackBar.open('Repository URL and Access Token are required', 'Close', { duration: 3000 });
      return;
    }

    this.testing.set(true);
    this.gitSyncService.testConnection(this.formConfig as GitSyncConfig).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.snackBar.open(
          result.connected ? 'Connection successful!' : 'Connection failed: ' + result.message,
          'Close',
          { duration: 3000 }
        );
      },
      error: (err) => {
        this.testing.set(false);
        this.snackBar.open('Connection test failed', 'Close', { duration: 3000 });
      }
    });
  }

  saveConfig(): void {
    const config = this.formConfig as GitSyncConfig;

    if (this.editingConfig()) {
      this.gitSyncService.updateConfig(this.editingConfig()!.id!, config).subscribe({
        next: () => {
          this.snackBar.open('Configuration updated', 'Close', { duration: 3000 });
          this.cancelEdit();
        },
        error: () => {
          this.snackBar.open('Failed to update configuration', 'Close', { duration: 3000 });
        }
      });
    } else {
      this.gitSyncService.createConfig(config).subscribe({
        next: () => {
          this.snackBar.open('Configuration created', 'Close', { duration: 3000 });
          this.formConfig = this.getEmptyConfig();
        },
        error: () => {
          this.snackBar.open('Failed to create configuration', 'Close', { duration: 3000 });
        }
      });
    }
  }

  editConfig(config: GitSyncConfig): void {
    this.editingConfig.set(config);
    this.formConfig = { ...config, accessToken: '' };
  }

  cancelEdit(): void {
    this.editingConfig.set(null);
    this.formConfig = this.getEmptyConfig();
  }

  deleteConfig(config: GitSyncConfig): void {
    if (!confirm(`Delete configuration "${config.name}"?`)) return;

    this.gitSyncService.deleteConfig(config.id!).subscribe({
      next: () => {
        this.snackBar.open('Configuration deleted', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Failed to delete configuration', 'Close', { duration: 3000 });
      }
    });
  }

  pushToGit(config: GitSyncConfig): void {
    const message = prompt('Commit message:', 'Sync configurations from RDF Forge');
    if (message === null) return;

    this.gitSyncService.push(config.id!, message).subscribe({
      next: (status) => {
        if (status.state === 'COMPLETED') {
          this.snackBar.open(`Pushed ${status.syncedFiles.length} files`, 'Close', { duration: 3000 });
        } else if (status.state === 'FAILED') {
          this.snackBar.open('Push failed: ' + status.errors.join(', '), 'Close', { duration: 5000 });
        }
      },
      error: () => {
        this.snackBar.open('Push failed', 'Close', { duration: 3000 });
      }
    });
  }

  pullFromGit(config: GitSyncConfig): void {
    this.gitSyncService.pull(config.id!, false).subscribe({
      next: (status) => {
        if (status.state === 'COMPLETED') {
          this.snackBar.open(`Pulled ${status.syncedFiles.length} files`, 'Close', { duration: 3000 });
        } else if (status.state === 'FAILED') {
          this.snackBar.open('Pull failed: ' + status.errors.join(', '), 'Close', { duration: 5000 });
        }
      },
      error: () => {
        this.snackBar.open('Pull failed', 'Close', { duration: 3000 });
      }
    });
  }

  getStatusIcon(status: GitSyncStatus): string {
    switch (status.state) {
      case 'COMPLETED': return 'check_circle';
      case 'FAILED': return 'error';
      case 'IN_PROGRESS': return 'sync';
      default: return 'pending';
    }
  }

  getStatusColor(state: string): 'primary' | 'accent' | 'warn' {
    switch (state) {
      case 'COMPLETED': return 'accent';
      case 'FAILED': return 'warn';
      default: return 'primary';
    }
  }

  getActionColor(action: string): 'primary' | 'accent' | 'warn' {
    switch (action) {
      case 'CREATED': return 'accent';
      case 'UPDATED': return 'primary';
      case 'DELETED': return 'warn';
      default: return 'primary';
    }
  }
}
