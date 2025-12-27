import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface ServiceHealth {
  name: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  url: string;
  responseTime?: number;
  version?: string;
}

interface SystemInfo {
  version: string;
  buildTime: string;
  environment: string;
  javaVersion?: string;
  nodeVersion?: string;
}

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="admin-container">
      <!-- System Information -->
      <mat-card class="info-card">
        <mat-card-header>
          <mat-card-title>System Information</mat-card-title>
        </mat-card-header>

        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">Application</span>
              <span class="value">RDF Forge</span>
            </div>
            <div class="info-item">
              <span class="label">Version</span>
              <span class="value">{{ systemInfo()?.version || '1.0.0' }}</span>
            </div>
            <div class="info-item">
              <span class="label">Environment</span>
              <mat-chip [color]="env.production ? 'warn' : 'accent'">
                {{ env.production ? 'Production' : 'Development' }}
              </mat-chip>
            </div>
            <div class="info-item">
              <span class="label">Auth Mode</span>
              <mat-chip [color]="env.auth.enabled ? 'primary' : 'accent'">
                {{ env.auth.enabled ? 'Keycloak' : 'Offline' }}
              </mat-chip>
            </div>
            <div class="info-item">
              <span class="label">API Base URL</span>
              <span class="value">{{ env.apiBaseUrl }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Service Health -->
      <mat-card class="health-card">
        <mat-card-header>
          <mat-card-title>Service Health</mat-card-title>
          <mat-card-subtitle>Status of backend microservices</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="toolbar">
            <button mat-raised-button color="primary" (click)="checkHealth()" [disabled]="checking()">
              <mat-icon>refresh</mat-icon>
              Check Health
            </button>
          </div>

          @if (checking()) {
            <div class="loading-container">
              <mat-spinner diameter="40"></mat-spinner>
              <p>Checking services...</p>
            </div>
          } @else {
            <div class="services-grid">
              @for (service of services(); track service.name) {
                <div class="service-item" [class]="service.status.toLowerCase()">
                  <div class="service-header">
                    <mat-icon>{{ getStatusIcon(service.status) }}</mat-icon>
                    <span class="service-name">{{ service.name }}</span>
                    <mat-chip [color]="getChipColor(service.status)">
                      {{ service.status }}
                    </mat-chip>
                  </div>
                  @if (service.responseTime) {
                    <span class="response-time">{{ service.responseTime }}ms</span>
                  }
                </div>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      <!-- Maintenance Actions -->
      <mat-card class="maintenance-card">
        <mat-card-header>
          <mat-card-title>Maintenance</mat-card-title>
          <mat-card-subtitle>System maintenance actions</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <div class="actions-grid">
            <button mat-raised-button (click)="clearCache()">
              <mat-icon>delete_sweep</mat-icon>
              Clear Cache
            </button>

            <button mat-raised-button (click)="downloadLogs()">
              <mat-icon>download</mat-icon>
              Download Logs
            </button>

            <button mat-raised-button color="warn" (click)="restartServices()">
              <mat-icon>restart_alt</mat-icon>
              Restart Services
            </button>
          </div>

          <div class="storage-info">
            <h4>Local Storage</h4>
            <p>Used: {{ getStorageUsage() }}</p>
            <button mat-button color="warn" (click)="clearLocalStorage()">
              Clear Local Storage
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .admin-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
      display: grid;
      gap: 24px;
    }

    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
      gap: 16px;
    }

    .info-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .info-item .label {
      font-size: 12px;
      color: var(--mdc-theme-text-secondary-on-background);
      text-transform: uppercase;
    }

    .info-item .value {
      font-size: 16px;
      font-weight: 500;
    }

    .toolbar {
      margin-bottom: 16px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 32px;
      gap: 16px;
    }

    .services-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 12px;
    }

    .service-item {
      display: flex;
      flex-direction: column;
      padding: 16px;
      border-radius: 8px;
      background: #f5f5f5;
    }

    .service-item.up {
      background: #e8f5e9;
    }

    .service-item.down {
      background: #ffebee;
    }

    .service-header {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .service-item.up mat-icon {
      color: #4caf50;
    }

    .service-item.down mat-icon {
      color: #f44336;
    }

    .service-item.unknown mat-icon {
      color: #ff9800;
    }

    .service-name {
      flex: 1;
      font-weight: 500;
    }

    .response-time {
      font-size: 12px;
      color: var(--mdc-theme-text-secondary-on-background);
      margin-top: 4px;
    }

    .actions-grid {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 24px;
    }

    .storage-info {
      padding: 16px;
      background: #f5f5f5;
      border-radius: 8px;
    }

    .storage-info h4 {
      margin: 0 0 8px 0;
    }
  `]
})
export class SystemSettings implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  readonly env = environment;

  systemInfo = signal<SystemInfo | null>(null);
  services = signal<ServiceHealth[]>([]);
  checking = signal(false);

  ngOnInit(): void {
    this.loadSystemInfo();
    this.checkHealth();
  }

  loadSystemInfo(): void {
    this.http.get<SystemInfo>(`${this.env.apiBaseUrl}/system/info`).subscribe({
      next: (info) => this.systemInfo.set(info),
      error: () => {
        // Default info for offline mode
        this.systemInfo.set({
          version: '1.0.0',
          buildTime: new Date().toISOString(),
          environment: this.env.production ? 'production' : 'development'
        });
      }
    });
  }

  checkHealth(): void {
    this.checking.set(true);

    const servicesList: ServiceHealth[] = [
      { name: 'API Gateway', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/health` },
      { name: 'Pipeline Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/pipelines` },
      { name: 'Data Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/data` },
      { name: 'SHACL Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/shapes` },
      { name: 'Job Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/jobs` },
      { name: 'Dimension Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/dimensions` },
      { name: 'Triplestore Service', status: 'UNKNOWN', url: `${this.env.apiBaseUrl}/triplestores` }
    ];

    let completed = 0;
    servicesList.forEach((service, index) => {
      const start = Date.now();
      this.http.get(service.url, { observe: 'response' }).subscribe({
        next: () => {
          servicesList[index].status = 'UP';
          servicesList[index].responseTime = Date.now() - start;
          completed++;
          if (completed === servicesList.length) {
            this.services.set([...servicesList]);
            this.checking.set(false);
          }
        },
        error: () => {
          servicesList[index].status = 'DOWN';
          servicesList[index].responseTime = Date.now() - start;
          completed++;
          if (completed === servicesList.length) {
            this.services.set([...servicesList]);
            this.checking.set(false);
          }
        }
      });
    });
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'UP': return 'check_circle';
      case 'DOWN': return 'error';
      default: return 'help';
    }
  }

  getChipColor(status: string): 'primary' | 'accent' | 'warn' {
    switch (status) {
      case 'UP': return 'accent';
      case 'DOWN': return 'warn';
      default: return 'primary';
    }
  }

  clearCache(): void {
    // Clear application cache
    if ('caches' in window) {
      caches.keys().then(names => {
        names.forEach(name => caches.delete(name));
      });
    }
    this.snackBar.open('Cache cleared', 'Close', { duration: 3000 });
  }

  downloadLogs(): void {
    this.http.get(`${this.env.apiBaseUrl}/admin/logs`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `logs-${new Date().toISOString().slice(0, 10)}.txt`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.snackBar.open('Log download not available in this environment', 'Close', { duration: 3000 });
      }
    });
  }

  restartServices(): void {
    if (!confirm('Restart all services? This may cause temporary downtime.')) return;

    this.http.post(`${this.env.apiBaseUrl}/admin/restart`, {}).subscribe({
      next: () => {
        this.snackBar.open('Restart initiated', 'Close', { duration: 3000 });
        setTimeout(() => this.checkHealth(), 5000);
      },
      error: () => {
        this.snackBar.open('Restart not available in this environment', 'Close', { duration: 3000 });
      }
    });
  }

  clearLocalStorage(): void {
    if (!confirm('Clear all local storage? This will reset your settings.')) return;

    localStorage.clear();
    this.snackBar.open('Local storage cleared', 'Close', { duration: 3000 });
  }

  getStorageUsage(): string {
    let total = 0;
    for (const key in localStorage) {
      if (localStorage.hasOwnProperty(key)) {
        total += localStorage[key].length * 2;
      }
    }
    return this.formatBytes(total);
  }

  private formatBytes(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
}
