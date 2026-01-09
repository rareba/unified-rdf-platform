import { Injectable, signal, computed, effect } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

export interface AppSettings {
  theme: 'light' | 'dark' | 'system';
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
  // GitLab settings
  gitlabUrl: string;
  gitlabDefaultProject: string;
}

export interface PrefixMapping {
  prefix: string;
  uri: string;
  builtin: boolean;
}

export const DEFAULT_SETTINGS: AppSettings = {
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
  defaultTriplestoreId: null,
  gitlabUrl: '',
  gitlabDefaultProject: ''
};

export const BUILTIN_PREFIXES: PrefixMapping[] = [
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

const STORAGE_KEY = 'rdf-forge-settings';

/**
 * Validation constraints for timeout settings
 */
export const TIMEOUT_CONSTRAINTS = {
  min: 5,           // Minimum 5 seconds
  max: 3600,        // Maximum 1 hour
  defaultPipeline: 300,
  defaultSparql: 60,
  defaultRefresh: 30
};

export const PAGE_SIZE_CONSTRAINTS = {
  min: 5,
  max: 100,
  default: 20
};

export const RESULT_LIMIT_CONSTRAINTS = {
  min: 10,
  max: 10000,
  default: 1000
};

@Injectable({
  providedIn: 'root'
})
export class SettingsService {
  // Core settings state
  private readonly _settings = signal<AppSettings>({ ...DEFAULT_SETTINGS });
  private readonly _prefixes = signal<PrefixMapping[]>([...BUILTIN_PREFIXES]);
  private readonly _initialized = signal(false);

  // Public readonly signals
  readonly settings = this._settings.asReadonly();
  readonly prefixes = this._prefixes.asReadonly();
  readonly initialized = this._initialized.asReadonly();

  // Observable for components that need reactive updates
  readonly settings$: Observable<AppSettings> = toObservable(this._settings);

  // Computed signals for common settings access
  readonly theme = computed(() => this._settings().theme);
  readonly language = computed(() => this._settings().language);
  readonly pageSize = computed(() => this._settings().pageSize);
  readonly pipelineTimeout = computed(() => this._settings().pipelineTimeout);
  readonly sparqlTimeout = computed(() => this._settings().sparqlTimeout);
  readonly sparqlResultLimit = computed(() => this._settings().sparqlResultLimit);
  readonly maxParallelJobs = computed(() => this._settings().maxParallelJobs);
  readonly defaultTriplestoreId = computed(() => this._settings().defaultTriplestoreId);
  readonly defaultBaseUri = computed(() => this._settings().defaultBaseUri);
  readonly defaultFormat = computed(() => this._settings().defaultFormat);
  readonly autoRefresh = computed(() => this._settings().autoRefresh);
  readonly refreshInterval = computed(() => this._settings().refreshInterval);
  readonly autoRetryFailed = computed(() => this._settings().autoRetryFailed);
  readonly retryAttempts = computed(() => this._settings().retryAttempts);
  readonly gitlabUrl = computed(() => this._settings().gitlabUrl);
  readonly gitlabDefaultProject = computed(() => this._settings().gitlabDefaultProject);

  // Computed for prefixes
  readonly customPrefixes = computed(() => this._prefixes().filter(p => !p.builtin));
  readonly allPrefixes = computed(() => this._prefixes());

  // Auto-save debounce
  private autoSaveTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Set up effect to listen for system theme changes when theme is 'system'
    if (typeof window !== 'undefined') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      mediaQuery.addEventListener('change', () => {
        if (this._settings().theme === 'system') {
          this.applyTheme();
        }
      });

      // Listen for storage events from other tabs
      window.addEventListener('storage', (event) => {
        if (event.key === STORAGE_KEY && event.newValue) {
          this.loadFromStorageData(event.newValue);
        }
      });
    }
  }

  /**
   * Initialize settings from localStorage. Call this during app bootstrap.
   */
  initialize(): void {
    if (this._initialized()) return;

    this.loadSettings();
    this.applyTheme();
    this._initialized.set(true);
  }

  /**
   * Get a specific setting value
   */
  getSetting<K extends keyof AppSettings>(key: K): AppSettings[K] {
    return this._settings()[key];
  }

  /**
   * Update a single setting with validation
   */
  updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]): void {
    // Validate and constrain numeric settings
    const validatedValue = this.validateSettingValue(key, value);

    this._settings.update(s => ({ ...s, [key]: validatedValue }));

    // Apply theme immediately when changed
    if (key === 'theme') {
      this.applyTheme();
    }

    this.autoSave();
  }

  /**
   * Validate and constrain setting values
   */
  private validateSettingValue<K extends keyof AppSettings>(key: K, value: AppSettings[K]): AppSettings[K] {
    switch (key) {
      case 'pipelineTimeout':
      case 'sparqlTimeout':
      case 'refreshInterval': {
        const num = Number(value);
        if (isNaN(num) || num < TIMEOUT_CONSTRAINTS.min) {
          return TIMEOUT_CONSTRAINTS.min as AppSettings[K];
        }
        if (num > TIMEOUT_CONSTRAINTS.max) {
          return TIMEOUT_CONSTRAINTS.max as AppSettings[K];
        }
        return Math.floor(num) as AppSettings[K];
      }

      case 'pageSize': {
        const num = Number(value);
        if (isNaN(num) || num < PAGE_SIZE_CONSTRAINTS.min) {
          return PAGE_SIZE_CONSTRAINTS.min as AppSettings[K];
        }
        if (num > PAGE_SIZE_CONSTRAINTS.max) {
          return PAGE_SIZE_CONSTRAINTS.max as AppSettings[K];
        }
        return Math.floor(num) as AppSettings[K];
      }

      case 'sparqlResultLimit': {
        const num = Number(value);
        if (isNaN(num) || num < RESULT_LIMIT_CONSTRAINTS.min) {
          return RESULT_LIMIT_CONSTRAINTS.min as AppSettings[K];
        }
        if (num > RESULT_LIMIT_CONSTRAINTS.max) {
          return RESULT_LIMIT_CONSTRAINTS.max as AppSettings[K];
        }
        return Math.floor(num) as AppSettings[K];
      }

      case 'maxParallelJobs': {
        const num = Number(value);
        if (isNaN(num) || num < 1) return 1 as AppSettings[K];
        if (num > 10) return 10 as AppSettings[K];
        return Math.floor(num) as AppSettings[K];
      }

      case 'retryAttempts': {
        const num = Number(value);
        if (isNaN(num) || num < 0) return 0 as AppSettings[K];
        if (num > 10) return 10 as AppSettings[K];
        return Math.floor(num) as AppSettings[K];
      }

      default:
        return value;
    }
  }

  /**
   * Update multiple settings at once
   */
  updateSettings(partial: Partial<AppSettings>): void {
    this._settings.update(s => ({ ...s, ...partial }));

    if ('theme' in partial) {
      this.applyTheme();
    }

    this.autoSave();
  }

  /**
   * Load settings from localStorage
   */
  loadSettings(): void {
    if (typeof window === 'undefined') return;

    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        this.loadFromStorageData(saved);
      }
    } catch (e) {
      console.error('Failed to load settings from localStorage', e);
    }
  }

  private loadFromStorageData(data: string): void {
    try {
      const parsed = JSON.parse(data);
      if (parsed.settings) {
        this._settings.set({ ...DEFAULT_SETTINGS, ...parsed.settings });
      }
      if (parsed.prefixes) {
        this._prefixes.set([
          ...BUILTIN_PREFIXES,
          ...parsed.prefixes.filter((p: PrefixMapping) => !p.builtin)
        ]);
      }
    } catch (e) {
      console.error('Failed to parse settings data', e);
    }
  }

  /**
   * Save settings to localStorage
   */
  saveSettings(): void {
    if (typeof window === 'undefined') return;

    try {
      const data = {
        settings: this._settings(),
        prefixes: this.customPrefixes()
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (e) {
      console.error('Failed to save settings to localStorage', e);
    }
  }

  /**
   * Debounced auto-save
   */
  private autoSave(): void {
    if (this.autoSaveTimeout) {
      clearTimeout(this.autoSaveTimeout);
    }
    this.autoSaveTimeout = setTimeout(() => {
      this.saveSettings();
    }, 500);
  }

  /**
   * Apply the current theme to the document
   */
  applyTheme(): void {
    if (typeof document === 'undefined') return;

    const theme = this._settings().theme;
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

  /**
   * Reset settings to defaults
   */
  resetSettings(): void {
    this._settings.set({ ...DEFAULT_SETTINGS });
    this._prefixes.set([...BUILTIN_PREFIXES]);
    if (typeof window !== 'undefined') {
      localStorage.removeItem(STORAGE_KEY);
    }
    this.applyTheme();
  }

  // Prefix Management

  /**
   * Add a custom prefix mapping
   */
  addPrefix(prefix: string, uri: string): boolean {
    if (this._prefixes().some(p => p.prefix === prefix)) {
      return false; // Duplicate
    }

    this._prefixes.update(list => [...list, { prefix, uri, builtin: false }]);
    this.autoSave();
    return true;
  }

  /**
   * Remove a custom prefix mapping
   */
  removePrefix(prefix: string): boolean {
    const existing = this._prefixes().find(p => p.prefix === prefix);
    if (!existing || existing.builtin) {
      return false;
    }

    this._prefixes.update(list => list.filter(p => p.prefix !== prefix));
    this.autoSave();
    return true;
  }

  /**
   * Get URI for a prefix
   */
  getUriForPrefix(prefix: string): string | null {
    const mapping = this._prefixes().find(p => p.prefix === prefix);
    return mapping?.uri ?? null;
  }

  /**
   * Expand a prefixed URI to full URI
   */
  expandUri(prefixedUri: string): string {
    const colonIndex = prefixedUri.indexOf(':');
    if (colonIndex === -1) return prefixedUri;

    const prefix = prefixedUri.substring(0, colonIndex);
    const localPart = prefixedUri.substring(colonIndex + 1);
    const uri = this.getUriForPrefix(prefix);

    return uri ? uri + localPart : prefixedUri;
  }

  /**
   * Compact a full URI to prefixed form if possible
   */
  compactUri(fullUri: string): string {
    for (const mapping of this._prefixes()) {
      if (fullUri.startsWith(mapping.uri)) {
        return mapping.prefix + ':' + fullUri.substring(mapping.uri.length);
      }
    }
    return fullUri;
  }

  // Import/Export

  /**
   * Export settings to JSON string
   */
  exportSettings(): string {
    return JSON.stringify({
      settings: this._settings(),
      prefixes: this.customPrefixes(),
      exportedAt: new Date().toISOString(),
      version: '1.0'
    }, null, 2);
  }

  /**
   * Import settings from JSON string
   */
  importSettings(jsonString: string): boolean {
    try {
      const data = JSON.parse(jsonString);
      if (data.settings) {
        this._settings.set({ ...DEFAULT_SETTINGS, ...data.settings });
      }
      if (data.prefixes) {
        this._prefixes.set([
          ...BUILTIN_PREFIXES,
          ...data.prefixes.filter((p: PrefixMapping) => !p.builtin)
        ]);
      }
      this.saveSettings();
      this.applyTheme();
      return true;
    } catch (e) {
      console.error('Failed to import settings', e);
      return false;
    }
  }

  // Utility methods for HTTP timeout calculation

  /**
   * Get HTTP timeout in milliseconds for pipeline operations
   */
  getPipelineTimeoutMs(): number {
    return this._settings().pipelineTimeout * 1000;
  }

  /**
   * Get HTTP timeout in milliseconds for SPARQL queries
   */
  getSparqlTimeoutMs(): number {
    return this._settings().sparqlTimeout * 1000;
  }

  /**
   * Get default HTTP timeout based on operation type
   */
  getTimeoutMs(operationType: 'pipeline' | 'sparql' | 'default' = 'default'): number {
    switch (operationType) {
      case 'pipeline':
        return this.getPipelineTimeoutMs();
      case 'sparql':
        return this.getSparqlTimeoutMs();
      default:
        return 30000; // 30 seconds default
    }
  }
}
