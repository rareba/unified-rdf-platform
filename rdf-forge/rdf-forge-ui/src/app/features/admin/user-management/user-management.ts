import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

interface UserInfo {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: string[];
  createdAt: string;
  lastLogin?: string;
}

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="admin-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>User Management</mat-card-title>
          <mat-card-subtitle>
            @if (env.auth.enabled) {
              Users are managed via Keycloak (read-only view)
            } @else {
              Demo users for offline development
            }
          </mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="toolbar">
            <mat-form-field appearance="outline" class="search-field">
              <mat-label>Search users</mat-label>
              <input matInput [(ngModel)]="searchQuery" (ngModelChange)="filterUsers()" placeholder="Username or email...">
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <button mat-raised-button color="primary" (click)="loadUsers()" [disabled]="loading()">
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
              <p>Loading users...</p>
            </div>
          } @else {
            <table mat-table [dataSource]="filteredUsers()" class="users-table">
              <ng-container matColumnDef="username">
                <th mat-header-cell *matHeaderCellDef>Username</th>
                <td mat-cell *matCellDef="let user">{{ user.username }}</td>
              </ng-container>

              <ng-container matColumnDef="email">
                <th mat-header-cell *matHeaderCellDef>Email</th>
                <td mat-cell *matCellDef="let user">{{ user.email }}</td>
              </ng-container>

              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let user">{{ user.firstName }} {{ user.lastName }}</td>
              </ng-container>

              <ng-container matColumnDef="roles">
                <th mat-header-cell *matHeaderCellDef>Roles</th>
                <td mat-cell *matCellDef="let user">
                  <mat-chip-set>
                    @for (role of user.roles; track role) {
                      <mat-chip [color]="role === 'admin' ? 'warn' : 'primary'">{{ role }}</mat-chip>
                    }
                  </mat-chip-set>
                </td>
              </ng-container>

              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let user">
                  <mat-chip [color]="user.enabled ? 'accent' : 'warn'">
                    {{ user.enabled ? 'Active' : 'Disabled' }}
                  </mat-chip>
                </td>
              </ng-container>

              <ng-container matColumnDef="lastLogin">
                <th mat-header-cell *matHeaderCellDef>Last Login</th>
                <td mat-cell *matCellDef="let user">
                  {{ user.lastLogin ? (user.lastLogin | date:'short') : 'Never' }}
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
            </table>

            @if (filteredUsers().length === 0) {
              <div class="no-data">
                <mat-icon>people_outline</mat-icon>
                <p>No users found</p>
              </div>
            }
          }
        </mat-card-content>

        <mat-card-footer>
          <p class="footer-info">
            Total users: {{ users().length }} |
            Active: {{ activeUsersCount() }}
          </p>
        </mat-card-footer>
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
      flex-wrap: wrap;
    }

    .search-field {
      flex: 1;
      min-width: 300px;
    }

    .users-table {
      width: 100%;
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

    .footer-info {
      padding: 16px;
      color: var(--mdc-theme-text-secondary-on-background);
      font-size: 14px;
    }
  `]
})
export class UserManagement implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly authService = inject(AuthService);

  readonly env = environment;
  readonly displayedColumns = ['username', 'email', 'name', 'roles', 'status', 'lastLogin'];

  users = signal<UserInfo[]>([]);
  loading = signal(false);
  searchQuery = '';

  activeUsersCount = computed(() => this.users().filter(u => u.enabled).length);

  filteredUsers = computed(() => {
    const query = this.searchQuery.toLowerCase();
    if (!query) return this.users();
    return this.users().filter(u =>
      u.username.toLowerCase().includes(query) ||
      u.email.toLowerCase().includes(query) ||
      u.firstName.toLowerCase().includes(query) ||
      u.lastName.toLowerCase().includes(query)
    );
  });

  get keycloakAdminUrl(): string {
    if (this.env.auth.enabled && this.env.auth.keycloak) {
      return `${this.env.auth.keycloak.url}/admin/${this.env.auth.keycloak.realm}/console`;
    }
    return '';
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);

    if (!this.env.auth.enabled) {
      // Demo users for offline mode
      this.users.set([
        {
          id: '1',
          username: 'admin',
          email: 'admin@example.org',
          firstName: 'Admin',
          lastName: 'User',
          enabled: true,
          roles: ['admin'],
          createdAt: '2024-01-01T00:00:00Z',
          lastLogin: new Date().toISOString()
        },
        {
          id: '2',
          username: 'editor',
          email: 'editor@example.org',
          firstName: 'Editor',
          lastName: 'User',
          enabled: true,
          roles: ['editor'],
          createdAt: '2024-01-15T00:00:00Z',
          lastLogin: new Date(Date.now() - 86400000).toISOString()
        },
        {
          id: '3',
          username: 'viewer',
          email: 'viewer@example.org',
          firstName: 'Viewer',
          lastName: 'User',
          enabled: true,
          roles: ['viewer'],
          createdAt: '2024-02-01T00:00:00Z'
        }
      ]);
      this.loading.set(false);
      return;
    }

    // Fetch from Keycloak via backend API
    this.http.get<UserInfo[]>(`${this.env.apiBaseUrl}/admin/users`).subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load users from Keycloak', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  filterUsers(): void {
    // Filtering is handled by the computed signal
  }
}
