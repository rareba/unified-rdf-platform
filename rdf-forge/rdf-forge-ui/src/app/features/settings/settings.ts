import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { DividerModule } from 'primeng/divider';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { AccordionModule } from 'primeng/accordion';
import { MessageService, ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';

interface AppSettings {
  theme: string;
  language: string;
  dateFormat: string;
  pageSize: number;
  autoRefresh: boolean;
  refreshInterval: number;
  defaultBaseUri: string;
  defaultFormat: string;
  showPrefixes: boolean;
  pipelineTimeout: number;
  maxParallelJobs: number;
  autoRetryFailed: boolean;
  retryAttempts: number;
  sparqlTimeout: number;
  sparqlResultLimit: number;
  defaultTriplestoreId: string | null;
}

interface PrefixMapping {
  prefix: string;
  uri: string;
  builtin: boolean;
}

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

const DEFAULT_SETTINGS: AppSettings = {
  theme: 'light',
  language: 'en',
  dateFormat: 'MMM dd, yyyy',
  pageSize: 20,
  autoRefresh: false,
  refreshInterval: 30,
  defaultBaseUri: 'http://example.org/',
  defaultFormat: 'turtle',
  showPrefixes: true,
  pipelineTimeout: 300,
  maxParallelJobs: 3,
  autoRetryFailed: false,
  retryAttempts: 3,
  sparqlTimeout: 60,
  sparqlResultLimit: 1000,
  defaultTriplestoreId: null
};

const BUILTIN_PREFIXES: PrefixMapping[] = [
  { prefix: 'rdf', uri: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#', builtin: true },
  { prefix: 'rdfs', uri: 'http://www.w3.org/2000/01/rdf-schema#', builtin: true },
  { prefix: 'owl', uri: 'http://www.w3.org/2002/07/owl#', builtin: true },
  { prefix: 'xsd', uri: 'http://www.w3.org/2001/XMLSchema#', builtin: true },
  { prefix: 'skos', uri: 'http://www.w3.org/2004/02/skos/core#', builtin: true },
  { prefix: 'dct', uri: 'http://purl.org/dc/terms/', builtin: true },
  { prefix: 'foaf', uri: 'http://xmlns.com/foaf/0.1/', builtin: true },
  { prefix: 'schema', uri: 'https://schema.org/', builtin: true },
  { prefix: 'qb', uri: 'http://purl.org/linked-data/cube#', builtin: true },
  { prefix: 'sh', uri: 'http://www.w3.org/ns/shacl#', builtin: true }
];

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
    CardModule,
    InputTextModule,
    InputNumberModule,
    ButtonModule,
    SelectModule,
    CheckboxModule,
    ToastModule,
    TooltipModule,
    ConfirmDialogModule,
    DialogModule,
    TextareaModule,
    DividerModule,
    TagModule,
    ProgressBarModule,
    TableModule,
    TabsModule,
    AccordionModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly authService = inject(AuthService);
  private readonly http = inject(HttpClient);

  readonly env = environment;

  // Settings
  settings = signal<AppSettings>({ ...DEFAULT_SETTINGS });
  prefixes = signal<PrefixMapping[]>([...BUILTIN_PREFIXES]);
  shortcuts = KEYBOARD_SHORTCUTS;

  // UI State
  loading = signal(true);
  saving = signal(false);
  checkingHealth = signal(false);
  activeTab = signal(0);

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
    this.loadSettings();
    this.checkServicesHealth();
  }

  loadSettings(): void {
    this.loading.set(true);
    try {
      const saved = localStorage.getItem('rdf-forge-settings');
      if (saved) {
        const parsed = JSON.parse(saved);
        this.settings.set({ ...DEFAULT_SETTINGS, ...parsed.settings });
        if (parsed.prefixes) {
          this.prefixes.set([
            ...BUILTIN_PREFIXES,
            ...parsed.prefixes.filter((p: PrefixMapping) => !p.builtin)
          ]);
        }
      }
      this.applyTheme(this.settings().theme);
    } catch (e) {
      console.error('Failed to load settings', e);
    }
    this.loading.set(false);
  }

  saveSettings(): void {
    this.saving.set(true);
    try {
      const data = {
        settings: this.settings(),
        prefixes: this.customPrefixes()
      };
      localStorage.setItem('rdf-forge-settings', JSON.stringify(data));
      this.applyTheme(this.settings().theme);
      this.messageService.add({
        severity: 'success',
        summary: 'Saved',
        detail: 'Settings saved successfully'
      });
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save settings'
      });
    }
    this.saving.set(false);
  }

  applyTheme(theme: string): void {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark-theme');
      root.classList.remove('light-theme');
    } else if (theme === 'light') {
      root.classList.remove('dark-theme');
      root.classList.add('light-theme');
    } else {
      // System preference
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      if (prefersDark) {
        root.classList.add('dark-theme');
        root.classList.remove('light-theme');
      } else {
        root.classList.remove('dark-theme');
        root.classList.add('light-theme');
      }
    }
  }

  updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]): void {
    this.settings.update(s => ({ ...s, [key]: value }));
  }

  // Prefix Management
  openPrefixDialog(): void {
    this.newPrefix.set({ prefix: '', uri: '', builtin: false });
    this.prefixDialogVisible.set(true);
  }

  addPrefix(): void {
    const prefix = this.newPrefix();
    if (!prefix.prefix || !prefix.uri) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Warning',
        detail: 'Prefix and URI are required'
      });
      return;
    }

    // Check for duplicates
    if (this.prefixes().some(p => p.prefix === prefix.prefix)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Warning',
        detail: 'Prefix already exists'
      });
      return;
    }

    this.prefixes.update(list => [...list, { ...prefix, builtin: false }]);
    this.prefixDialogVisible.set(false);
    this.messageService.add({
      severity: 'success',
      summary: 'Added',
      detail: 'Prefix mapping added'
    });
  }

  removePrefix(prefix: PrefixMapping): void {
    if (prefix.builtin) return;
    this.prefixes.update(list => list.filter(p => p.prefix !== prefix.prefix));
    this.messageService.add({
      severity: 'info',
      summary: 'Removed',
      detail: 'Prefix mapping removed'
    });
  }

  copyPrefix(prefix: PrefixMapping): void {
    navigator.clipboard.writeText(`PREFIX ${prefix.prefix}: <${prefix.uri}>`);
    this.messageService.add({
      severity: 'info',
      summary: 'Copied',
      detail: 'SPARQL PREFIX declaration copied'
    });
  }

  // Import/Export
  openExportDialog(): void {
    const data = {
      settings: this.settings(),
      prefixes: this.customPrefixes(),
      exportedAt: new Date().toISOString(),
      version: '1.0'
    };
    this.exportData.set(JSON.stringify(data, null, 2));
    this.exportDialogVisible.set(true);
  }

  copyExport(): void {
    navigator.clipboard.writeText(this.exportData());
    this.messageService.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Settings copied to clipboard'
    });
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
    try {
      const data = JSON.parse(this.importData());
      if (data.settings) {
        this.settings.set({ ...DEFAULT_SETTINGS, ...data.settings });
      }
      if (data.prefixes) {
        this.prefixes.set([
          ...BUILTIN_PREFIXES,
          ...data.prefixes.filter((p: PrefixMapping) => !p.builtin)
        ]);
      }
      this.importDialogVisible.set(false);
      this.saveSettings();
      this.messageService.add({
        severity: 'success',
        summary: 'Imported',
        detail: 'Settings imported successfully'
      });
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Invalid settings format'
      });
    }
  }

  // Reset
  confirmReset(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to reset all settings to defaults? This cannot be undone.',
      header: 'Reset Settings',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.resetSettings();
      }
    });
  }

  resetSettings(): void {
    this.settings.set({ ...DEFAULT_SETTINGS });
    this.prefixes.set([...BUILTIN_PREFIXES]);
    localStorage.removeItem('rdf-forge-settings');
    this.applyTheme('light');
    this.messageService.add({
      severity: 'success',
      summary: 'Reset',
      detail: 'Settings reset to defaults'
    });
  }

  // Cache
  confirmClearCache(): void {
    this.confirmationService.confirm({
      message: 'Clear all cached data? This will not affect your settings.',
      header: 'Clear Cache',
      icon: 'pi pi-info-circle',
      accept: () => {
        this.clearCache();
      }
    });
  }

  clearCache(): void {
    // Clear any cached data (keeping settings)
    const settings = localStorage.getItem('rdf-forge-settings');
    localStorage.clear();
    if (settings) {
      localStorage.setItem('rdf-forge-settings', settings);
    }
    this.messageService.add({
      severity: 'success',
      summary: 'Cleared',
      detail: 'Cache cleared successfully'
    });
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
}
