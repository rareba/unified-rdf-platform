import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import {
  SettingsService,
  AppSettings,
  PrefixMapping,
  BUILTIN_PREFIXES,
  DEFAULT_SETTINGS
} from '../../core/services/settings.service';

interface ServiceHealth {
  name: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  url: string;
  responseTime?: number;
}

interface KeyboardShortcut {
  action: string;
  keys: string;
  description: string;
}

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

interface RoleInfo {
  name: string;
  description: string;
  permissions: string[];
  userCount: number;
  isDefault: boolean;
}

const DEFAULT_ROLES: RoleInfo[] = [
  {
    name: 'admin',
    description: 'Full system administrator with all permissions',
    permissions: ['read', 'write', 'delete', 'admin', 'manage_users'],
    userCount: 0,
    isDefault: false
  },
  {
    name: 'editor',
    description: 'Can create and edit pipelines, shapes, and data',
    permissions: ['read', 'write', 'delete'],
    userCount: 0,
    isDefault: false
  },
  {
    name: 'viewer',
    description: 'Read-only access to all resources',
    permissions: ['read'],
    userCount: 0,
    isDefault: true
  }
];

const AVAILABLE_PERMISSIONS = [
  { key: 'read', label: 'Read', description: 'View resources and data' },
  { key: 'write', label: 'Write', description: 'Create and modify resources' },
  { key: 'delete', label: 'Delete', description: 'Remove resources' },
  { key: 'admin', label: 'Admin', description: 'System configuration' },
  { key: 'manage_users', label: 'Manage Users', description: 'User and role management' },
  { key: 'run_pipelines', label: 'Run Pipelines', description: 'Execute pipeline jobs' },
  { key: 'manage_triplestore', label: 'Manage Triplestore', description: 'Triplestore connections' }
];

// DEFAULT_SETTINGS and BUILTIN_PREFIXES are imported from SettingsService

const KEYBOARD_SHORTCUTS: KeyboardShortcut[] = [
  { action: 'Save', keys: 'Ctrl+S', description: 'Save current form or document' },
  { action: 'Search', keys: 'Ctrl+K', description: 'Focus search input' },
  { action: 'New', keys: 'Ctrl+N', description: 'Create new item' },
  { action: 'Delete', keys: 'Delete', description: 'Delete selected item' },
  { action: 'Escape', keys: 'Esc', description: 'Close dialog or cancel action' },
  { action: 'Run Query', keys: 'Ctrl+Enter', description: 'Execute SPARQL query' },
  { action: 'Format', keys: 'Ctrl+Shift+F', description: 'Format code/query' },
  { action: 'Navigate Back', keys: 'Alt+Left', description: 'Go to previous page' }
];

@Component({
  selector: 'app-settings',
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatDialogModule,
    MatDividerModule,
    MatProgressBarModule,
    MatTableModule,
    MatTabsModule,
    MatExpansionModule,
    MatIconModule,
    MatFormFieldModule
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  private readonly snackBar = inject(MatSnackBar);
  private readonly authService = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly settingsService = inject(SettingsService);

  readonly env = environment;

  // Settings - now use SettingsService as source of truth
  settings = computed(() => this.settingsService.settings());
  prefixes = computed(() => this.settingsService.prefixes());
  shortcuts = KEYBOARD_SHORTCUTS;

  // UI State
  loading = signal(true);
  saving = signal(false);
  checkingHealth = signal(false);
  activeTab = 0;

  // Dialogs
  prefixDialogVisible = signal(false);
  importDialogVisible = signal(false);
  exportDialogVisible = signal(false);

  // Forms
  newPrefix = signal<PrefixMapping>({ prefix: '', uri: '', builtin: false });
  importData = signal('');
  exportData = signal('');

  // Service health
  services = signal<ServiceHealth[]>([]);

  // User management
  users = signal<UserInfo[]>([]);
  roles = signal<RoleInfo[]>([...DEFAULT_ROLES]);
  loadingUsers = signal(false);
  selectedUser = signal<UserInfo | null>(null);
  userDialogVisible = signal(false);
  roleDialogVisible = signal(false);
  newRole = signal<RoleInfo>({ name: '', description: '', permissions: [], userCount: 0, isDefault: false });
  availablePermissions = AVAILABLE_PERMISSIONS;

  // Personal Access Token management
  personalAccessTokens = signal<PersonalAccessToken[]>([]);
  loadingTokens = signal(false);
  tokenDialogVisible = signal(false);
  newTokenDialogVisible = signal(false);
  tokenCreatedDialogVisible = signal(false);
  newToken = signal<CreateTokenRequest>({ name: '', description: '', expiration: 'ONE_MONTH' });
  createdToken = signal<string | null>(null);
  tokenExpirationOptions = TOKEN_EXPIRATION_OPTIONS;

  // Options
  themeOptions = [
    { label: 'Light', value: 'light' },
    { label: 'Dark', value: 'dark' },
    { label: 'System', value: 'system' }
  ];

  languageOptions = [
    { label: 'English', value: 'en' },
    { label: 'German', value: 'de' },
    { label: 'French', value: 'fr' },
    { label: 'Italian', value: 'it' },
    { label: 'Spanish', value: 'es' }
  ];

  dateFormatOptions = [
    { label: 'Jan 01, 2024', value: 'MMM dd, yyyy' },
    { label: '01/01/2024', value: 'MM/dd/yyyy' },
    { label: '01-01-2024', value: 'dd-MM-yyyy' },
    { label: '2024-01-01', value: 'yyyy-MM-dd' }
  ];

  pageSizeOptions = [
    { label: '10 items', value: 10 },
    { label: '20 items', value: 20 },
    { label: '50 items', value: 50 },
    { label: '100 items', value: 100 }
  ];

  formatOptions = [
    { label: 'Turtle (.ttl)', value: 'turtle' },
    { label: 'RDF/XML (.rdf)', value: 'rdfxml' },
    { label: 'N-Triples (.nt)', value: 'ntriples' },
    { label: 'JSON-LD (.jsonld)', value: 'jsonld' }
  ];

  // Computed
  userProfile = computed(() => this.authService.userProfile);
  isAuthenticated = computed(() => this.authService.isAuthenticated);
  customPrefixes = computed(() => this.prefixes().filter(p => !p.builtin));
  totalPrefixes = computed(() => this.prefixes().length);

  ngOnInit(): void {
    // Settings are loaded via APP_INITIALIZER, just update loading state
    this.loading.set(false);
    this.checkServicesHealth();
  }

  loadSettings(): void {
    // Settings are now managed by SettingsService
    // This method is kept for compatibility but delegates to the service
    this.loading.set(true);
    this.settingsService.loadSettings();
    this.loading.set(false);
  }

  saveSettings(): void {
    this.saving.set(true);
    try {
      this.settingsService.saveSettings();
      this.snackBar.open('Settings saved successfully', 'Close', { duration: 3000 });
    } catch (e) {
      this.snackBar.open('Failed to save settings', 'Close', { duration: 3000 });
    }
    this.saving.set(false);
  }

  applyTheme(theme: string): void {
    // Delegate to SettingsService
    this.settingsService.applyTheme();
  }

  updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]): void {
    // Delegate to SettingsService - it handles theme application and auto-save
    this.settingsService.updateSetting(key, value);
  }

  // Prefix Management
  openPrefixDialog(): void {
    this.newPrefix.set({ prefix: '', uri: '', builtin: false });
    this.prefixDialogVisible.set(true);
  }

  addPrefix(): void {
    const prefix = this.newPrefix();
    if (!prefix.prefix || !prefix.uri) {
      this.snackBar.open('Prefix and URI are required', 'Close', { duration: 3000 });
      return;
    }

    // Use SettingsService to add prefix
    if (!this.settingsService.addPrefix(prefix.prefix, prefix.uri)) {
      this.snackBar.open('Prefix already exists', 'Close', { duration: 3000 });
      return;
    }

    this.prefixDialogVisible.set(false);
    this.snackBar.open('Prefix mapping added', 'Close', { duration: 3000 });
  }

  removePrefix(prefix: PrefixMapping): void {
    if (prefix.builtin) return;
    this.settingsService.removePrefix(prefix.prefix);
    this.snackBar.open('Prefix mapping removed', 'Close', { duration: 3000 });
  }

  copyPrefix(prefix: PrefixMapping): void {
    navigator.clipboard.writeText(`PREFIX ${prefix.prefix}: <${prefix.uri}>`);
    this.snackBar.open('SPARQL PREFIX declaration copied', 'Close', { duration: 3000 });
  }

  // Import/Export
  openExportDialog(): void {
    // Use SettingsService for export
    this.exportData.set(this.settingsService.exportSettings());
    this.exportDialogVisible.set(true);
  }

  copyExport(): void {
    navigator.clipboard.writeText(this.exportData());
    this.snackBar.open('Settings copied to clipboard', 'Close', { duration: 3000 });
  }

  downloadExport(): void {
    const blob = new Blob([this.exportData()], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `rdf-forge-settings-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  openImportDialog(): void {
    this.importData.set('');
    this.importDialogVisible.set(true);
  }

  importSettings(): void {
    // Use SettingsService for import
    if (this.settingsService.importSettings(this.importData())) {
      this.importDialogVisible.set(false);
      this.snackBar.open('Settings imported successfully', 'Close', { duration: 3000 });
    } else {
      this.snackBar.open('Invalid settings format', 'Close', { duration: 3000 });
    }
  }

  // Reset
  confirmReset(): void {
    if (confirm('Are you sure you want to reset all settings to defaults? This cannot be undone.')) {
      this.resetSettings();
    }
  }

  resetSettings(): void {
    // Use SettingsService for reset
    this.settingsService.resetSettings();
    this.snackBar.open('Settings reset to defaults', 'Close', { duration: 3000 });
  }

  // Cache
  confirmClearCache(): void {
    if (confirm('Clear all cached data? This will not affect your settings.')) {
      this.clearCache();
    }
  }

  clearCache(): void {
    // Clear any cached data (keeping settings)
    const settings = localStorage.getItem('rdf-forge-settings');
    localStorage.clear();
    if (settings) {
      localStorage.setItem('rdf-forge-settings', settings);
    }
    this.snackBar.open('Cache cleared successfully', 'Close', { duration: 3000 });
  }

  // Service Health
  checkServicesHealth(): void {
    this.checkingHealth.set(true);
    const services: ServiceHealth[] = [
      { name: 'API Gateway', status: 'UNKNOWN', url: environment.apiBaseUrl },
      { name: 'Pipeline Service', status: 'UNKNOWN', url: `${environment.apiBaseUrl}/pipelines` },
      { name: 'Data Service', status: 'UNKNOWN', url: `${environment.apiBaseUrl}/data` },
      { name: 'SHACL Service', status: 'UNKNOWN', url: `${environment.apiBaseUrl}/shacl` },
      { name: 'Dimension Service', status: 'UNKNOWN', url: `${environment.apiBaseUrl}/dimensions` }
    ];

    // Check each service (simplified - just checking if endpoint responds)
    let completed = 0;
    services.forEach((service, index) => {
      const start = Date.now();
      this.http.get(`${service.url}`, { observe: 'response' }).subscribe({
        next: () => {
          services[index].status = 'UP';
          services[index].responseTime = Date.now() - start;
          completed++;
          if (completed === services.length) {
            this.services.set([...services]);
            this.checkingHealth.set(false);
          }
        },
        error: () => {
          services[index].status = 'DOWN';
          services[index].responseTime = Date.now() - start;
          completed++;
          if (completed === services.length) {
            this.services.set([...services]);
            this.checkingHealth.set(false);
          }
        }
      });
    });
  }

  getHealthSeverity(status: string): 'success' | 'danger' | 'warn' {
    switch (status) {
      case 'UP': return 'success';
      case 'DOWN': return 'danger';
      default: return 'warn';
    }
  }

  // Helpers
  getAuthStatus(): string {
    if (!this.env.auth.enabled) {
      return 'Disabled (Offline Mode)';
    }
    return this.isAuthenticated() ? 'Authenticated' : 'Not Authenticated';
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  getStorageUsage(): string {
    let total = 0;
    for (const key in localStorage) {
      if (localStorage.hasOwnProperty(key)) {
        total += localStorage[key].length * 2; // UTF-16 uses 2 bytes per character
      }
    }
    return this.formatBytes(total);
  }

  // Form update helpers (for Angular template compatibility)
  updateNewPrefixPrefix(value: string): void {
    this.newPrefix.update(p => ({ ...p, prefix: value }));
  }

  updateNewPrefixUri(value: string): void {
    this.newPrefix.update(p => ({ ...p, uri: value }));
  }

  // User Management Methods
  loadUsers(): void {
    if (!this.env.auth.enabled) {
      // In standalone mode, show demo users
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
      this.updateRoleCounts();
      return;
    }

    // When Keycloak is enabled, fetch from API
    this.loadingUsers.set(true);
    this.http.get<UserInfo[]>(`${this.env.apiBaseUrl}/admin/users`).subscribe({
      next: (users) => {
        this.users.set(users);
        this.updateRoleCounts();
        this.loadingUsers.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load users from Keycloak', 'Close', { duration: 3000 });
        this.loadingUsers.set(false);
      }
    });
  }

  updateRoleCounts(): void {
    const users = this.users();
    this.roles.update(roles => roles.map(role => ({
      ...role,
      userCount: users.filter(u => u.roles.includes(role.name)).length
    })));
  }

  openUserDialog(user: UserInfo): void {
    this.selectedUser.set({ ...user });
    this.userDialogVisible.set(true);
  }

  saveUser(): void {
    const user = this.selectedUser();
    if (!user) return;

    if (this.env.auth.enabled) {
      // Save to Keycloak via API
      this.http.put(`${this.env.apiBaseUrl}/admin/users/${user.id}`, user).subscribe({
        next: () => {
          this.users.update(users => users.map(u => u.id === user.id ? user : u));
          this.updateRoleCounts();
          this.userDialogVisible.set(false);
          this.snackBar.open('User updated successfully', 'Close', { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Failed to update user', 'Close', { duration: 3000 });
        }
      });
    } else {
      // Update locally for demo
      this.users.update(users => users.map(u => u.id === user.id ? user : u));
      this.updateRoleCounts();
      this.userDialogVisible.set(false);
      this.snackBar.open('User updated (demo mode)', 'Close', { duration: 3000 });
    }
  }

  toggleUserEnabled(user: UserInfo): void {
    const updatedUser = { ...user, enabled: !user.enabled };
    if (this.env.auth.enabled) {
      this.http.put(`${this.env.apiBaseUrl}/admin/users/${user.id}/enabled`, { enabled: updatedUser.enabled }).subscribe({
        next: () => {
          this.users.update(users => users.map(u => u.id === user.id ? updatedUser : u));
          this.snackBar.open(`User ${updatedUser.enabled ? 'enabled' : 'disabled'}`, 'Close', { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Failed to update user status', 'Close', { duration: 3000 });
        }
      });
    } else {
      this.users.update(users => users.map(u => u.id === user.id ? updatedUser : u));
      this.snackBar.open(`User ${updatedUser.enabled ? 'enabled' : 'disabled'} (demo mode)`, 'Close', { duration: 3000 });
    }
  }

  // Role Management Methods
  openRoleDialog(): void {
    this.newRole.set({ name: '', description: '', permissions: [], userCount: 0, isDefault: false });
    this.roleDialogVisible.set(true);
  }

  addRole(): void {
    const role = this.newRole();
    if (!role.name) {
      this.snackBar.open('Role name is required', 'Close', { duration: 3000 });
      return;
    }

    if (this.roles().some(r => r.name === role.name)) {
      this.snackBar.open('Role already exists', 'Close', { duration: 3000 });
      return;
    }

    if (this.env.auth.enabled) {
      this.http.post(`${this.env.apiBaseUrl}/admin/roles`, role).subscribe({
        next: () => {
          this.roles.update(roles => [...roles, { ...role, userCount: 0 }]);
          this.roleDialogVisible.set(false);
          this.snackBar.open('Role created successfully', 'Close', { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Failed to create role', 'Close', { duration: 3000 });
        }
      });
    } else {
      this.roles.update(roles => [...roles, { ...role, userCount: 0 }]);
      this.roleDialogVisible.set(false);
      this.snackBar.open('Role created (demo mode)', 'Close', { duration: 3000 });
    }
  }

  deleteRole(role: RoleInfo): void {
    if (role.isDefault) {
      this.snackBar.open('Cannot delete default role', 'Close', { duration: 3000 });
      return;
    }

    if (role.userCount > 0) {
      this.snackBar.open('Cannot delete role with assigned users', 'Close', { duration: 3000 });
      return;
    }

    if (confirm(`Delete role "${role.name}"?`)) {
      if (this.env.auth.enabled) {
        this.http.delete(`${this.env.apiBaseUrl}/admin/roles/${role.name}`).subscribe({
          next: () => {
            this.roles.update(roles => roles.filter(r => r.name !== role.name));
            this.snackBar.open('Role deleted', 'Close', { duration: 3000 });
          },
          error: () => {
            this.snackBar.open('Failed to delete role', 'Close', { duration: 3000 });
          }
        });
      } else {
        this.roles.update(roles => roles.filter(r => r.name !== role.name));
        this.snackBar.open('Role deleted (demo mode)', 'Close', { duration: 3000 });
      }
    }
  }

  togglePermission(permission: string): void {
    this.newRole.update(role => {
      const permissions = role.permissions.includes(permission)
        ? role.permissions.filter(p => p !== permission)
        : [...role.permissions, permission];
      return { ...role, permissions };
    });
  }

  hasPermission(permission: string): boolean {
    return this.newRole().permissions.includes(permission);
  }

  updateNewRoleName(name: string): void {
    this.newRole.update(role => ({ ...role, name }));
  }

  updateNewRoleDescription(description: string): void {
    this.newRole.update(role => ({ ...role, description }));
  }

  updateUserRole(user: UserInfo, roleName: string, add: boolean): void {
    const updatedRoles = add
      ? [...user.roles, roleName]
      : user.roles.filter(r => r !== roleName);

    const selectedUser = this.selectedUser();
    if (selectedUser && selectedUser.id === user.id) {
      this.selectedUser.update(u => u ? { ...u, roles: updatedRoles } : null);
    }
  }

  getUserRoleNames(user: UserInfo): string {
    return user.roles.join(', ') || 'No roles';
  }

  getKeycloakAdminUrl(): string {
    if (this.env.auth.enabled && this.env.auth.keycloak) {
      return `${this.env.auth.keycloak.url}/admin/${this.env.auth.keycloak.realm}/console`;
    }
    return '';
  }

  // Personal Access Token Methods
  loadPersonalAccessTokens(): void {
    this.loadingTokens.set(true);
    this.http.get<PersonalAccessToken[]>(`${this.env.apiBaseUrl}/auth/tokens`).subscribe({
      next: (tokens) => {
        this.personalAccessTokens.set(tokens);
        this.loadingTokens.set(false);
      },
      error: () => {
        // In standalone mode or if API not available, show demo tokens
        this.personalAccessTokens.set([
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
        this.loadingTokens.set(false);
      }
    });
  }

  openNewTokenDialog(): void {
    this.newToken.set({ name: '', description: '', expiration: 'ONE_MONTH' });
    this.newTokenDialogVisible.set(true);
  }

  createPersonalAccessToken(): void {
    const tokenRequest = this.newToken();
    if (!tokenRequest.name) {
      this.snackBar.open('Token name is required', 'Close', { duration: 3000 });
      return;
    }

    this.loadingTokens.set(true);
    this.http.post<CreateTokenResponse>(`${this.env.apiBaseUrl}/auth/tokens`, tokenRequest).subscribe({
      next: (response) => {
        this.personalAccessTokens.update(tokens => [response.token, ...tokens]);
        this.createdToken.set(response.plainToken);
        this.newTokenDialogVisible.set(false);
        this.tokenCreatedDialogVisible.set(true);
        this.loadingTokens.set(false);
      },
      error: () => {
        // Demo mode - simulate token creation
        const demoToken = 'ccx_' + Array(40).fill(0).map(() =>
          'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'.charAt(Math.random() * 62 | 0)
        ).join('');

        const newTokenObj: PersonalAccessToken = {
          id: 'demo-' + Date.now(),
          name: tokenRequest.name,
          description: tokenRequest.description,
          tokenPrefix: demoToken.substring(0, 12) + '...',
          scopes: [],
          createdAt: new Date().toISOString(),
          expiresAt: this.calculateExpiration(tokenRequest.expiration),
          revoked: false
        };

        this.personalAccessTokens.update(tokens => [newTokenObj, ...tokens]);
        this.createdToken.set(demoToken);
        this.newTokenDialogVisible.set(false);
        this.tokenCreatedDialogVisible.set(true);
        this.loadingTokens.set(false);
        this.snackBar.open('Token created (demo mode)', 'Close', { duration: 3000 });
      }
    });
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

  revokeToken(token: PersonalAccessToken): void {
    if (confirm(`Are you sure you want to revoke the token "${token.name}"? This action cannot be undone.`)) {
      this.http.delete(`${this.env.apiBaseUrl}/auth/tokens/${token.id}`).subscribe({
        next: () => {
          this.personalAccessTokens.update(tokens =>
            tokens.map(t => t.id === token.id ? { ...t, revoked: true } : t)
          );
          this.snackBar.open('Token revoked successfully', 'Close', { duration: 3000 });
        },
        error: () => {
          // Demo mode
          this.personalAccessTokens.update(tokens =>
            tokens.map(t => t.id === token.id ? { ...t, revoked: true } : t)
          );
          this.snackBar.open('Token revoked (demo mode)', 'Close', { duration: 3000 });
        }
      });
    }
  }

  copyToken(): void {
    const token = this.createdToken();
    if (token) {
      navigator.clipboard.writeText(token);
      this.snackBar.open('Token copied to clipboard', 'Close', { duration: 3000 });
    }
  }

  closeTokenCreatedDialog(): void {
    this.createdToken.set(null);
    this.tokenCreatedDialogVisible.set(false);
  }

  formatTokenExpiration(expiresAt?: string): string {
    if (!expiresAt) return 'Never expires';
    const expDate = new Date(expiresAt);
    const now = new Date();
    if (expDate < now) return 'Expired';
    const diffDays = Math.ceil((expDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays <= 7) return `Expires in ${diffDays} days`;
    if (diffDays <= 30) return `Expires in ${Math.ceil(diffDays / 7)} weeks`;
    return `Expires ${expDate.toLocaleDateString()}`;
  }

  formatLastUsed(lastUsedAt?: string): string {
    if (!lastUsedAt) return 'Never used';
    const lastUsed = new Date(lastUsedAt);
    const now = new Date();
    const diffMins = Math.floor((now.getTime() - lastUsed.getTime()) / (1000 * 60));
    if (diffMins < 60) return `${diffMins} minutes ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} hours ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays} days ago`;
    return lastUsed.toLocaleDateString();
  }

  updateNewTokenName(name: string): void {
    this.newToken.update(t => ({ ...t, name }));
  }

  updateNewTokenDescription(description: string): void {
    this.newToken.update(t => ({ ...t, description }));
  }

  updateNewTokenExpiration(expiration: string): void {
    this.newToken.update(t => ({ ...t, expiration }));
  }

  getActiveTokenCount(): number {
    return this.personalAccessTokens().filter(t => !t.revoked).length;
  }
}
