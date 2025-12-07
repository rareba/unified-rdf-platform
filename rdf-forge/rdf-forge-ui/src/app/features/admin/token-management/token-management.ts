import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface PersonalAccessToken {
  id: string;
  name: string;
  description?: string;
  tokenPrefix: string;
  scopes: string[];
  createdAt: string;
  expiresAt?: string;
  lastUsedAt?: string;
  lastUsedIp?: string;
  revoked: boolean;
}

interface CreateTokenRequest {
  name: string;
  description?: string;
  expiration: string;
  scopes?: string[];
}

interface CreateTokenResponse {
  token: PersonalAccessToken;
  plainToken: string;
}

const TOKEN_EXPIRATION_OPTIONS = [
  { label: '1 week', value: 'ONE_WEEK' },
  { label: '2 weeks', value: 'TWO_WEEKS' },
  { label: '1 month', value: 'ONE_MONTH' },
  { label: '3 months', value: 'THREE_MONTHS' },
  { label: '6 months', value: 'SIX_MONTHS' },
  { label: '1 year', value: 'ONE_YEAR' },
  { label: 'Never expires', value: 'NEVER' }
];

@Component({
  selector: 'app-token-management',
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
    MatSelectModule,
    MatDialogModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="admin-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Personal Access Tokens</mat-card-title>
          <mat-card-subtitle>Manage API tokens for programmatic access</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="toolbar">
            <button mat-raised-button color="primary" (click)="openNewTokenDialog()">
              <mat-icon>add</mat-icon>
              Create Token
            </button>

            <button mat-raised-button (click)="loadTokens()" [disabled]="loading()">
              <mat-icon>refresh</mat-icon>
              Refresh
            </button>
          </div>

          @if (loading()) {
            <div class="loading-container">
              <mat-spinner diameter="40"></mat-spinner>
              <p>Loading tokens...</p>
            </div>
          } @else {
            <table mat-table [dataSource]="tokens()" class="tokens-table">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let token">
                  {{ token.name }}
                  @if (token.description) {
                    <br><small class="description">{{ token.description }}</small>
                  }
                </td>
              </ng-container>

              <ng-container matColumnDef="token">
                <th mat-header-cell *matHeaderCellDef>Token</th>
                <td mat-cell *matCellDef="let token">
                  <code>{{ token.tokenPrefix }}...</code>
                </td>
              </ng-container>

              <ng-container matColumnDef="expires">
                <th mat-header-cell *matHeaderCellDef>Expires</th>
                <td mat-cell *matCellDef="let token">
                  {{ formatExpiration(token.expiresAt) }}
                </td>
              </ng-container>

              <ng-container matColumnDef="lastUsed">
                <th mat-header-cell *matHeaderCellDef>Last Used</th>
                <td mat-cell *matCellDef="let token">
                  {{ formatLastUsed(token.lastUsedAt) }}
                  @if (token.lastUsedIp) {
                    <br><small>from {{ token.lastUsedIp }}</small>
                  }
                </td>
              </ng-container>

              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let token">
                  <mat-chip [color]="token.revoked ? 'warn' : 'accent'">
                    {{ token.revoked ? 'Revoked' : 'Active' }}
                  </mat-chip>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>Actions</th>
                <td mat-cell *matCellDef="let token">
                  @if (!token.revoked) {
                    <button mat-icon-button color="warn" (click)="revokeToken(token)"
                            matTooltip="Revoke token">
                      <mat-icon>block</mat-icon>
                    </button>
                  }
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;"
                  [class.revoked]="row.revoked"></tr>
            </table>

            @if (tokens().length === 0) {
              <div class="no-data">
                <mat-icon>vpn_key</mat-icon>
                <p>No personal access tokens</p>
                <button mat-raised-button color="primary" (click)="openNewTokenDialog()">
                  Create your first token
                </button>
              </div>
            }
          }
        </mat-card-content>

        <mat-card-footer>
          <p class="footer-info">
            Active tokens: {{ activeTokensCount() }}
          </p>
        </mat-card-footer>
      </mat-card>

      <!-- Create Token Dialog -->
      @if (newTokenDialogVisible()) {
        <div class="dialog-overlay" (click)="newTokenDialogVisible.set(false)">
          <mat-card class="dialog-card" (click)="$event.stopPropagation()">
            <mat-card-header>
              <mat-card-title>Create Personal Access Token</mat-card-title>
            </mat-card-header>

            <mat-card-content>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Token Name</mat-label>
                <input matInput [(ngModel)]="newToken.name" placeholder="e.g., CI/CD Pipeline">
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Description (optional)</mat-label>
                <textarea matInput [(ngModel)]="newToken.description" rows="2"></textarea>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Expiration</mat-label>
                <mat-select [(ngModel)]="newToken.expiration">
                  @for (opt of expirationOptions; track opt.value) {
                    <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </mat-card-content>

            <mat-card-actions align="end">
              <button mat-button (click)="newTokenDialogVisible.set(false)">Cancel</button>
              <button mat-raised-button color="primary" (click)="createToken()"
                      [disabled]="!newToken.name">
                Create Token
              </button>
            </mat-card-actions>
          </mat-card>
        </div>
      }

      <!-- Token Created Dialog -->
      @if (tokenCreatedDialogVisible()) {
        <div class="dialog-overlay">
          <mat-card class="dialog-card">
            <mat-card-header>
              <mat-card-title>Token Created Successfully</mat-card-title>
            </mat-card-header>

            <mat-card-content>
              <div class="warning-box">
                <mat-icon>warning</mat-icon>
                <p>Make sure to copy your token now. You will not be able to see it again!</p>
              </div>

              <div class="token-display">
                <code>{{ createdToken() }}</code>
                <button mat-icon-button (click)="copyToken()" matTooltip="Copy to clipboard">
                  <mat-icon>content_copy</mat-icon>
                </button>
              </div>
            </mat-card-content>

            <mat-card-actions align="end">
              <button mat-raised-button color="primary" (click)="closeTokenCreatedDialog()">
                Done
              </button>
            </mat-card-actions>
          </mat-card>
        </div>
      }
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

    .tokens-table {
      width: 100%;
    }

    .tokens-table .revoked {
      opacity: 0.6;
    }

    .description {
      color: var(--mdc-theme-text-secondary-on-background);
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
      gap: 16px;
      color: var(--mdc-theme-text-secondary-on-background);
    }

    .no-data mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
    }

    .footer-info {
      padding: 16px;
      color: var(--mdc-theme-text-secondary-on-background);
      font-size: 14px;
    }

    .dialog-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .dialog-card {
      width: 500px;
      max-width: 90vw;
    }

    .full-width {
      width: 100%;
      margin-bottom: 16px;
    }

    .warning-box {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      background: #fff3cd;
      border-radius: 8px;
      margin-bottom: 16px;
    }

    .warning-box mat-icon {
      color: #856404;
    }

    .token-display {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px;
      background: #f5f5f5;
      border-radius: 8px;
      overflow-x: auto;
    }

    .token-display code {
      flex: 1;
      font-family: monospace;
      word-break: break-all;
    }
  `]
})
export class TokenManagement implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  readonly env = environment;
  readonly displayedColumns = ['name', 'token', 'expires', 'lastUsed', 'status', 'actions'];
  readonly expirationOptions = TOKEN_EXPIRATION_OPTIONS;

  tokens = signal<PersonalAccessToken[]>([]);
  loading = signal(false);
  newTokenDialogVisible = signal(false);
  tokenCreatedDialogVisible = signal(false);
  createdToken = signal<string>('');

  activeTokensCount = computed(() => this.tokens().filter(t => !t.revoked).length);

  newToken: CreateTokenRequest = {
    name: '',
    description: '',
    expiration: 'ONE_MONTH'
  };

  ngOnInit(): void {
    this.loadTokens();
  }

  loadTokens(): void {
    this.loading.set(true);

    this.http.get<PersonalAccessToken[]>(`${this.env.apiBaseUrl}/auth/tokens`).subscribe({
      next: (tokens) => {
        this.tokens.set(tokens);
        this.loading.set(false);
      },
      error: () => {
        // Demo tokens for offline mode
        this.tokens.set([
          {
            id: 'demo-1',
            name: 'CI/CD Pipeline Token',
            description: 'Used for automated deployments',
            tokenPrefix: 'ccx_abc12345',
            scopes: [],
            createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
            expiresAt: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000).toISOString(),
            lastUsedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
            lastUsedIp: '192.168.1.100',
            revoked: false
          },
          {
            id: 'demo-2',
            name: 'Local Development',
            description: 'For testing API calls locally',
            tokenPrefix: 'ccx_xyz98765',
            scopes: [],
            createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            lastUsedAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
            revoked: false
          }
        ]);
        this.loading.set(false);
      }
    });
  }

  openNewTokenDialog(): void {
    this.newToken = { name: '', description: '', expiration: 'ONE_MONTH' };
    this.newTokenDialogVisible.set(true);
  }

  createToken(): void {
    if (!this.newToken.name) return;

    this.loading.set(true);
    this.http.post<CreateTokenResponse>(`${this.env.apiBaseUrl}/auth/tokens`, this.newToken).subscribe({
      next: (response) => {
        this.tokens.update(tokens => [response.token, ...tokens]);
        this.createdToken.set(response.plainToken);
        this.newTokenDialogVisible.set(false);
        this.tokenCreatedDialogVisible.set(true);
        this.loading.set(false);
      },
      error: () => {
        // Demo mode - simulate token creation
        const demoToken = 'ccx_' + Array(40).fill(0).map(() =>
          'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'.charAt(Math.random() * 62 | 0)
        ).join('');

        const newTokenObj: PersonalAccessToken = {
          id: 'demo-' + Date.now(),
          name: this.newToken.name,
          description: this.newToken.description,
          tokenPrefix: demoToken.substring(0, 12) + '...',
          scopes: [],
          createdAt: new Date().toISOString(),
          expiresAt: this.calculateExpiration(this.newToken.expiration),
          revoked: false
        };

        this.tokens.update(tokens => [newTokenObj, ...tokens]);
        this.createdToken.set(demoToken);
        this.newTokenDialogVisible.set(false);
        this.tokenCreatedDialogVisible.set(true);
        this.loading.set(false);
        this.snackBar.open('Token created (demo mode)', 'Close', { duration: 3000 });
      }
    });
  }

  revokeToken(token: PersonalAccessToken): void {
    if (!confirm(`Revoke token "${token.name}"? This cannot be undone.`)) return;

    this.http.delete(`${this.env.apiBaseUrl}/auth/tokens/${token.id}`).subscribe({
      next: () => {
        this.tokens.update(tokens =>
          tokens.map(t => t.id === token.id ? { ...t, revoked: true } : t)
        );
        this.snackBar.open('Token revoked', 'Close', { duration: 3000 });
      },
      error: () => {
        // Demo mode
        this.tokens.update(tokens =>
          tokens.map(t => t.id === token.id ? { ...t, revoked: true } : t)
        );
        this.snackBar.open('Token revoked (demo mode)', 'Close', { duration: 3000 });
      }
    });
  }

  copyToken(): void {
    navigator.clipboard.writeText(this.createdToken());
    this.snackBar.open('Token copied to clipboard', 'Close', { duration: 3000 });
  }

  closeTokenCreatedDialog(): void {
    this.createdToken.set('');
    this.tokenCreatedDialogVisible.set(false);
  }

  formatExpiration(expiresAt?: string): string {
    if (!expiresAt) return 'Never';
    const expDate = new Date(expiresAt);
    const now = new Date();
    if (expDate < now) return 'Expired';
    const diffDays = Math.ceil((expDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays <= 7) return `${diffDays} days`;
    if (diffDays <= 30) return `${Math.ceil(diffDays / 7)} weeks`;
    return expDate.toLocaleDateString();
  }

  formatLastUsed(lastUsedAt?: string): string {
    if (!lastUsedAt) return 'Never';
    const lastUsed = new Date(lastUsedAt);
    const now = new Date();
    const diffMins = Math.floor((now.getTime() - lastUsed.getTime()) / (1000 * 60));
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return lastUsed.toLocaleDateString();
  }

  private calculateExpiration(expiration: string): string | undefined {
    const days: { [key: string]: number } = {
      'ONE_WEEK': 7,
      'TWO_WEEKS': 14,
      'ONE_MONTH': 30,
      'THREE_MONTHS': 90,
      'SIX_MONTHS': 180,
      'ONE_YEAR': 365,
      'NEVER': 0
    };

    const d = days[expiration];
    if (d === 0) return undefined;
    return new Date(Date.now() + d * 24 * 60 * 60 * 1000).toISOString();
  }
}
