import { Injectable, inject, NgZone, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

export interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  description: string;
  action: () => void;
  category?: string;
  enabled?: boolean;
}

interface ShortcutRegistration {
  id: string;
  shortcut: KeyboardShortcut;
}

@Injectable({
  providedIn: 'root'
})
export class KeyboardShortcutsService implements OnDestroy {
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  private shortcuts: Map<string, ShortcutRegistration> = new Map();
  private enabled = true;
  private listener: ((event: KeyboardEvent) => void) | null = null;

  // Subject for notifying about shortcut activations
  readonly shortcutActivated = new Subject<string>();

  constructor() {
    this.setupGlobalListener();
    this.registerDefaultShortcuts();
  }

  ngOnDestroy(): void {
    if (this.listener) {
      document.removeEventListener('keydown', this.listener);
    }
    this.shortcutActivated.complete();
  }

  private setupGlobalListener(): void {
    this.listener = (event: KeyboardEvent) => {
      if (!this.enabled) return;

      // Don't trigger shortcuts when typing in inputs
      const target = event.target as HTMLElement;
      if (this.isInputElement(target)) {
        // Allow Escape in inputs
        if (event.key !== 'Escape') return;
      }

      const matchedShortcut = this.findMatchingShortcut(event);
      if (matchedShortcut) {
        event.preventDefault();
        event.stopPropagation();

        this.zone.run(() => {
          matchedShortcut.shortcut.action();
          this.shortcutActivated.next(matchedShortcut.id);
        });
      }
    };

    document.addEventListener('keydown', this.listener);
  }

  private isInputElement(element: HTMLElement | null): boolean {
    if (!element || !element.tagName) {
      return false;
    }
    const tagName = element.tagName.toLowerCase();
    const isInput = ['input', 'textarea', 'select'].includes(tagName);
    const isContentEditable = element.isContentEditable;
    return isInput || isContentEditable;
  }

  private findMatchingShortcut(event: KeyboardEvent): ShortcutRegistration | undefined {
    for (const registration of this.shortcuts.values()) {
      const s = registration.shortcut;
      if (s.enabled === false) continue;

      const keyMatch = event.key.toLowerCase() === s.key.toLowerCase();
      const ctrlMatch = !!s.ctrlKey === (event.ctrlKey || event.metaKey);
      const shiftMatch = !!s.shiftKey === event.shiftKey;
      const altMatch = !!s.altKey === event.altKey;

      if (keyMatch && ctrlMatch && shiftMatch && altMatch) {
        return registration;
      }
    }
    return undefined;
  }

  private registerDefaultShortcuts(): void {
    // Navigation shortcuts
    this.register('nav-home', {
      key: 'h',
      altKey: true,
      description: 'Go to Dashboard',
      category: 'Navigation',
      action: () => this.router.navigate(['/'])
    });

    this.register('nav-pipelines', {
      key: 'p',
      altKey: true,
      description: 'Go to Pipelines',
      category: 'Navigation',
      action: () => this.router.navigate(['/pipelines'])
    });

    this.register('nav-jobs', {
      key: 'j',
      altKey: true,
      description: 'Go to Jobs',
      category: 'Navigation',
      action: () => this.router.navigate(['/jobs'])
    });

    this.register('nav-data', {
      key: 'd',
      altKey: true,
      description: 'Go to Data Manager',
      category: 'Navigation',
      action: () => this.router.navigate(['/data'])
    });

    this.register('nav-dimensions', {
      key: 'i',
      altKey: true,
      description: 'Go to Dimensions',
      category: 'Navigation',
      action: () => this.router.navigate(['/dimensions'])
    });

    this.register('nav-shacl', {
      key: 's',
      altKey: true,
      description: 'Go to SHACL Studio',
      category: 'Navigation',
      action: () => this.router.navigate(['/shacl'])
    });

    this.register('nav-cube', {
      key: 'c',
      altKey: true,
      description: 'Go to Cube Wizard',
      category: 'Navigation',
      action: () => this.router.navigate(['/cube'])
    });

    this.register('nav-triplestores', {
      key: 't',
      altKey: true,
      description: 'Go to Triplestores',
      category: 'Navigation',
      action: () => this.router.navigate(['/triplestores'])
    });

    this.register('nav-settings', {
      key: ',',
      ctrlKey: true,
      description: 'Open Settings',
      category: 'Navigation',
      action: () => this.router.navigate(['/settings'])
    });

    // Global actions
    this.register('help', {
      key: '?',
      shiftKey: true,
      description: 'Show keyboard shortcuts',
      category: 'Help',
      action: () => this.showShortcutsHelp()
    });

    this.register('escape', {
      key: 'Escape',
      description: 'Close dialog / Cancel',
      category: 'General',
      action: () => {
        // Close any open dialogs by clicking backdrop or escape handler
        const dialog = document.querySelector('.cdk-overlay-backdrop');
        if (dialog) {
          (dialog as HTMLElement).click();
        }
      }
    });
  }

  /**
   * Register a new keyboard shortcut
   */
  register(id: string, shortcut: KeyboardShortcut): void {
    this.shortcuts.set(id, { id, shortcut });
  }

  /**
   * Unregister a keyboard shortcut
   */
  unregister(id: string): void {
    this.shortcuts.delete(id);
  }

  /**
   * Enable or disable a specific shortcut
   */
  setEnabled(id: string, enabled: boolean): void {
    const registration = this.shortcuts.get(id);
    if (registration) {
      registration.shortcut.enabled = enabled;
    }
  }

  /**
   * Enable or disable all shortcuts
   */
  setGlobalEnabled(enabled: boolean): void {
    this.enabled = enabled;
  }

  /**
   * Get all registered shortcuts grouped by category
   */
  getShortcutsByCategory(): Map<string, ShortcutRegistration[]> {
    const grouped = new Map<string, ShortcutRegistration[]>();

    for (const registration of this.shortcuts.values()) {
      const category = registration.shortcut.category || 'Other';
      const list = grouped.get(category) || [];
      list.push(registration);
      grouped.set(category, list);
    }

    return grouped;
  }

  /**
   * Get a formatted string for a shortcut (e.g., "Ctrl+S")
   */
  formatShortcut(shortcut: KeyboardShortcut): string {
    const parts: string[] = [];

    if (shortcut.ctrlKey) parts.push('Ctrl');
    if (shortcut.altKey) parts.push('Alt');
    if (shortcut.shiftKey) parts.push('Shift');

    // Format special keys
    let key = shortcut.key;
    if (key === ' ') key = 'Space';
    else if (key === 'Escape') key = 'Esc';
    else if (key.length === 1) key = key.toUpperCase();

    parts.push(key);
    return parts.join('+');
  }

  /**
   * Show a help dialog with all shortcuts
   */
  private showShortcutsHelp(): void {
    // Build help content
    const shortcuts = this.getShortcutsByCategory();
    let content = 'Keyboard Shortcuts:\n\n';

    for (const [category, items] of shortcuts) {
      content += `${category}:\n`;
      for (const item of items) {
        const formatted = this.formatShortcut(item.shortcut);
        content += `  ${formatted.padEnd(15)} - ${item.shortcut.description}\n`;
      }
      content += '\n';
    }

    console.log(content);
    // In a real app, you'd show a dialog here
    alert(content);
  }
}
