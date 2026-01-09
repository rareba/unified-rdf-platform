import { TestBed } from '@angular/core/testing';
import { SettingsService, DEFAULT_SETTINGS, BUILTIN_PREFIXES } from './settings.service';

describe('SettingsService', () => {
  let service: SettingsService;
  let localStorageSpy: jasmine.SpyObj<Storage>;

  beforeEach(() => {
    // Mock localStorage
    localStorageSpy = jasmine.createSpyObj('Storage', ['getItem', 'setItem', 'removeItem']);
    spyOn(localStorage, 'getItem').and.callFake(localStorageSpy.getItem);
    spyOn(localStorage, 'setItem').and.callFake(localStorageSpy.setItem);
    spyOn(localStorage, 'removeItem').and.callFake(localStorageSpy.removeItem);

    TestBed.configureTestingModule({
      providers: [SettingsService]
    });
    service = TestBed.inject(SettingsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('initialization', () => {
    it('should have default settings before initialization', () => {
      expect(service.settings()).toEqual(DEFAULT_SETTINGS);
    });

    it('should have builtin prefixes', () => {
      expect(service.prefixes().length).toBe(BUILTIN_PREFIXES.length);
    });

    it('should load settings from localStorage on initialize', () => {
      const savedSettings = JSON.stringify({
        settings: { ...DEFAULT_SETTINGS, theme: 'dark' },
        prefixes: []
      });
      localStorageSpy.getItem.and.returnValue(savedSettings);

      service.initialize();

      expect(service.initialized()).toBeTrue();
      expect(service.theme()).toBe('dark');
    });

    it('should not reinitialize if already initialized', () => {
      service.initialize();
      const callCount = localStorageSpy.getItem.calls.count();

      service.initialize();
      expect(localStorageSpy.getItem.calls.count()).toBe(callCount);
    });
  });

  describe('getSetting()', () => {
    it('should return a specific setting value', () => {
      expect(service.getSetting('theme')).toBe('light');
      expect(service.getSetting('pageSize')).toBe(20);
    });
  });

  describe('updateSetting()', () => {
    it('should update a single setting', (done) => {
      service.updateSetting('pageSize', 50);
      expect(service.pageSize()).toBe(50);

      // Wait for debounced auto-save
      setTimeout(() => {
        expect(localStorageSpy.setItem).toHaveBeenCalled();
        done();
      }, 600);
    });

    it('should apply theme when theme is updated', () => {
      const applyThemeSpy = spyOn(service, 'applyTheme');
      service.updateSetting('theme', 'dark');
      expect(applyThemeSpy).toHaveBeenCalled();
    });
  });

  describe('updateSettings()', () => {
    it('should update multiple settings at once', () => {
      service.updateSettings({ pageSize: 100, autoRefresh: true });
      expect(service.pageSize()).toBe(100);
      expect(service.autoRefresh()).toBeTrue();
    });

    it('should apply theme when theme is included in update', () => {
      const applyThemeSpy = spyOn(service, 'applyTheme');
      service.updateSettings({ theme: 'dark' });
      expect(applyThemeSpy).toHaveBeenCalled();
    });
  });

  describe('resetSettings()', () => {
    it('should reset to default settings', () => {
      service.updateSettings({ pageSize: 100, theme: 'dark' });
      service.resetSettings();

      expect(service.settings()).toEqual(DEFAULT_SETTINGS);
      expect(localStorageSpy.removeItem).toHaveBeenCalledWith('rdf-forge-settings');
    });

    it('should reset prefixes to builtin only', () => {
      service.addPrefix('custom', 'http://custom.example.org/');
      service.resetSettings();

      expect(service.prefixes().length).toBe(BUILTIN_PREFIXES.length);
      expect(service.customPrefixes().length).toBe(0);
    });
  });

  describe('prefix management', () => {
    it('should add a custom prefix', () => {
      const result = service.addPrefix('ex', 'http://example.org/');
      expect(result).toBeTrue();
      expect(service.prefixes().find(p => p.prefix === 'ex')).toBeTruthy();
    });

    it('should not add duplicate prefix', () => {
      service.addPrefix('ex', 'http://example.org/');
      const result = service.addPrefix('ex', 'http://other.org/');
      expect(result).toBeFalse();
    });

    it('should remove a custom prefix', () => {
      service.addPrefix('ex', 'http://example.org/');
      const result = service.removePrefix('ex');
      expect(result).toBeTrue();
      expect(service.prefixes().find(p => p.prefix === 'ex')).toBeFalsy();
    });

    it('should not remove builtin prefix', () => {
      const result = service.removePrefix('rdf');
      expect(result).toBeFalse();
      expect(service.prefixes().find(p => p.prefix === 'rdf')).toBeTruthy();
    });

    it('should not remove non-existent prefix', () => {
      const result = service.removePrefix('nonexistent');
      expect(result).toBeFalse();
    });
  });

  describe('URI handling', () => {
    it('should get URI for a prefix', () => {
      const uri = service.getUriForPrefix('rdf');
      expect(uri).toBe('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    });

    it('should return null for unknown prefix', () => {
      const uri = service.getUriForPrefix('unknown');
      expect(uri).toBeNull();
    });

    it('should expand prefixed URI', () => {
      const expanded = service.expandUri('rdf:type');
      expect(expanded).toBe('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');
    });

    it('should return original if prefix not found', () => {
      const result = service.expandUri('unknown:value');
      expect(result).toBe('unknown:value');
    });

    it('should return original if no colon', () => {
      const result = service.expandUri('justAString');
      expect(result).toBe('justAString');
    });

    it('should compact full URI to prefixed form', () => {
      const compacted = service.compactUri('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');
      expect(compacted).toBe('rdf:type');
    });

    it('should return original URI if no matching prefix', () => {
      const result = service.compactUri('http://unknown.example.org/value');
      expect(result).toBe('http://unknown.example.org/value');
    });
  });

  describe('import/export', () => {
    it('should export settings to JSON', () => {
      service.updateSettings({ pageSize: 100 });
      const exported = service.exportSettings();
      const parsed = JSON.parse(exported);

      expect(parsed.settings.pageSize).toBe(100);
      expect(parsed.version).toBe('1.0');
      expect(parsed.exportedAt).toBeTruthy();
    });

    it('should import settings from JSON', () => {
      const importData = JSON.stringify({
        settings: { ...DEFAULT_SETTINGS, pageSize: 200 },
        prefixes: [{ prefix: 'ex', uri: 'http://example.org/', builtin: false }]
      });

      const result = service.importSettings(importData);

      expect(result).toBeTrue();
      expect(service.pageSize()).toBe(200);
      expect(service.prefixes().find(p => p.prefix === 'ex')).toBeTruthy();
    });

    it('should return false for invalid JSON', () => {
      const result = service.importSettings('invalid json');
      expect(result).toBeFalse();
    });
  });

  describe('timeout calculations', () => {
    it('should get pipeline timeout in milliseconds', () => {
      service.updateSetting('pipelineTimeout', 300);
      expect(service.getPipelineTimeoutMs()).toBe(300000);
    });

    it('should get SPARQL timeout in milliseconds', () => {
      service.updateSetting('sparqlTimeout', 60);
      expect(service.getSparqlTimeoutMs()).toBe(60000);
    });

    it('should get timeout by operation type', () => {
      service.updateSettings({ pipelineTimeout: 300, sparqlTimeout: 60 });

      expect(service.getTimeoutMs('pipeline')).toBe(300000);
      expect(service.getTimeoutMs('sparql')).toBe(60000);
      expect(service.getTimeoutMs('default')).toBe(30000);
    });
  });

  describe('computed signals', () => {
    it('should have customPrefixes computed signal', () => {
      expect(service.customPrefixes().length).toBe(0);
      service.addPrefix('ex', 'http://example.org/');
      expect(service.customPrefixes().length).toBe(1);
    });

    it('should have allPrefixes computed signal', () => {
      const initialCount = service.allPrefixes().length;
      service.addPrefix('ex', 'http://example.org/');
      expect(service.allPrefixes().length).toBe(initialCount + 1);
    });
  });
});
