import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface RoleInfo {
  name: string;
  description: string;
  permissions: string[];
  userCount: number;
  isDefault: boolean;
}

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="admin-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Role Management</mat-card-title>
          <mat-card-subtitle>
            @if (env.auth.enabled) {
              Roles are managed via Keycloak (read-only view)
            } @else {
              Default roles for offline development
            }
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="toolbar">
            <button mat-raised-button color="primary" (click)="loadRoles()" [disabled]="loading()">
              <mat-icon>refresh</mat-icon>
              Refresh
            </button>

            @if (env.auth.enabled && keycloakAdminUrl) {
              <a mat-raised-button [href]="keycloakAdminUrl" target="_blank">
                <mat-icon>open_in_new</mat-icon>
                Keycloak Admin
              </a>
            }
          </div>

          @if (loading()) {
            <div class="loading-container">
              <mat-spinner diameter="40"></mat-spinner>
              <p>Loading roles...</p>
            </div>
          } @else {
            <div class="roles-grid">
              @for (role of roles(); track role.name) {
                <mat-card class="role-card">
                  <mat-card-header>
                    <mat-card-title>
                      {{ role.name }}
                      @if (role.isDefault) {
                        <mat-chip color="accent" class="default-chip">Default</mat-chip>
                      }
                    </mat-card-title>
                    <mat-card-subtitle>{{ role.userCount }} users</mat-card-subtitle>
                  </mat-card-header>

                  <mat-card-content>
                    <p class="description">{{ role.description }}</p>

                    <div class="permissions">
                      <strong>Permissions:</strong>
                      <mat-chip-set>
                        @for (perm of role.permissions; track perm) {
                          <mat-chip>{{ perm }}</mat-chip>
                        }
                      </mat-chip-set>
                    </div>
                  </mat-card-content>
                </mat-card>
              }
            </div>

            @if (roles().length === 0) {
              <div class="no-data">
                <mat-icon>admin_panel_settings</mat-icon>
                <p>No roles found</p>
              </div>
            }
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .admin-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .toolbar {
      display: flex;
      gap: 16px;
      align-items: center;
      margin-bottom: 24px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px;
      gap: 16px;
    }

    .roles-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 16px;
    }

    .role-card {
      height: 100%;
    }

    .role-card mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .default-chip {
      font-size: 10px;
      height: 20px;
    }

    .description {
      margin-bottom: 16px;
      color: var(--mdc-theme-text-secondary-on-background);
    }

    .permissions {
      margin-top: 16px;
    }

    .permissions strong {
      display: block;
      margin-bottom: 8px;
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
  `]
})
export class RoleManagement implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  readonly env = environment;

  roles = signal<RoleInfo[]>([]);
  loading = signal(false);

  get keycloakAdminUrl(): string {
    if (this.env.auth.enabled && this.env.auth.keycloak) {
      return `${this.env.auth.keycloak.url}/admin/${this.env.auth.keycloak.realm}/console/#/realms/${this.env.auth.keycloak.realm}/roles`;
    }
    return '';
  }

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.loading.set(true);

    if (!this.env.auth.enabled) {
      // Demo roles for offline mode
      this.roles.set([
        {
          name: 'admin',
          description: 'Full system administrator with all permissions',
          permissions: ['read', 'write', 'delete', 'admin', 'manage_users'],
          userCount: 1,
          isDefault: false
        },
        {
          name: 'editor',
          description: 'Can create and edit pipelines, shapes, and data',
          permissions: ['read', 'write', 'delete'],
          userCount: 1,
          isDefault: false
        },
        {
          name: 'viewer',
          description: 'Read-only access to all resources',
          permissions: ['read'],
          userCount: 1,
          isDefault: true
        }
      ]);
      this.loading.set(false);
      return;
    }

    // Fetch from Keycloak via backend API
    this.http.get<RoleInfo[]>(`${this.env.apiBaseUrl}/admin/roles`).subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load roles from Keycloak', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }
}
