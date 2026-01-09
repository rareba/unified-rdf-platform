import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { KeyboardShortcutsService } from './keyboard-shortcuts.service';

describe('KeyboardShortcutsService', () => {
  let service: KeyboardShortcutsService;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    routerSpy.navigate.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        KeyboardShortcutsService,
        { provide: Router, useValue: routerSpy }
      ]
    });

    service = TestBed.inject(KeyboardShortcutsService);
  });

  afterEach(() => {
    service.ngOnDestroy();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should register default navigation shortcuts', () => {
    const shortcuts = service.getShortcutsByCategory();
    const navShortcuts = shortcuts.get('Navigation');

    expect(navShortcuts).toBeTruthy();
    expect(navShortcuts!.length).toBeGreaterThan(0);
  });

  it('should register custom shortcut', () => {
    const actionSpy = jasmine.createSpy('action');

    service.register('custom-test', {
      key: 'x',
      ctrlKey: true,
      description: 'Test shortcut',
      action: actionSpy
    });

    const shortcuts = service.getShortcutsByCategory();
    const otherShortcuts = shortcuts.get('Other');

    expect(otherShortcuts?.some(s => s.id === 'custom-test')).toBeTrue();
  });

  it('should unregister shortcut', () => {
    service.register('to-remove', {
      key: 'r',
      description: 'To be removed',
      action: () => {}
    });

    service.unregister('to-remove');

    const shortcuts = service.getShortcutsByCategory();
    let found = false;
    shortcuts.forEach(list => {
      if (list.some(s => s.id === 'to-remove')) found = true;
    });

    expect(found).toBeFalse();
  });

  it('should disable specific shortcut', () => {
    const actionSpy = jasmine.createSpy('action');

    service.register('disable-test', {
      key: 'q',
      description: 'Disable test',
      action: actionSpy
    });

    service.setEnabled('disable-test', false);

    // Simulate keydown
    const event = new KeyboardEvent('keydown', { key: 'q' });
    document.dispatchEvent(event);

    expect(actionSpy).not.toHaveBeenCalled();
  });

  it('should disable all shortcuts globally', () => {
    const actionSpy = jasmine.createSpy('action');

    service.register('global-test', {
      key: 'g',
      description: 'Global test',
      action: actionSpy
    });

    service.setGlobalEnabled(false);

    const event = new KeyboardEvent('keydown', { key: 'g' });
    document.dispatchEvent(event);

    expect(actionSpy).not.toHaveBeenCalled();
  });

  it('should format shortcut correctly', () => {
    expect(service.formatShortcut({
      key: 's',
      ctrlKey: true,
      description: 'Save',
      action: () => {}
    })).toBe('Ctrl+S');

    expect(service.formatShortcut({
      key: 'p',
      altKey: true,
      description: 'Print',
      action: () => {}
    })).toBe('Alt+P');

    expect(service.formatShortcut({
      key: 'z',
      ctrlKey: true,
      shiftKey: true,
      description: 'Redo',
      action: () => {}
    })).toBe('Ctrl+Shift+Z');
  });

  it('should format special keys correctly', () => {
    expect(service.formatShortcut({
      key: 'Escape',
      description: 'Close',
      action: () => {}
    })).toBe('Esc');

    expect(service.formatShortcut({
      key: ' ',
      description: 'Space',
      action: () => {}
    })).toBe('Space');
  });

  it('should trigger navigation shortcut', fakeAsync(() => {
    // Alt+H should navigate to dashboard
    const event = new KeyboardEvent('keydown', {
      key: 'h',
      altKey: true
    });

    document.dispatchEvent(event);
    tick();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  }));

  it('should trigger pipelines navigation', fakeAsync(() => {
    const event = new KeyboardEvent('keydown', {
      key: 'p',
      altKey: true
    });

    document.dispatchEvent(event);
    tick();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pipelines']);
  }));

  it('should not trigger shortcuts when typing in input', () => {
    const actionSpy = jasmine.createSpy('action');

    service.register('input-test', {
      key: 'a',
      description: 'Test',
      action: actionSpy
    });

    // Create input element
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    const event = new KeyboardEvent('keydown', { key: 'a', bubbles: true });
    Object.defineProperty(event, 'target', { value: input });
    document.dispatchEvent(event);

    document.body.removeChild(input);

    // Should not trigger because target is input
    expect(actionSpy).not.toHaveBeenCalled();
  });

  it('should allow Escape in input elements', fakeAsync(() => {
    // Create input element
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    const event = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true
    });
    Object.defineProperty(event, 'target', { value: input });
    document.dispatchEvent(event);
    tick();

    document.body.removeChild(input);

    // Escape should still be processed
    // (the default escape handler tries to close dialogs)
  }));

  it('should emit shortcutActivated when shortcut triggered', fakeAsync(() => {
    const activatedSpy = jasmine.createSpy('activated');
    service.shortcutActivated.subscribe(activatedSpy);

    const event = new KeyboardEvent('keydown', {
      key: 'h',
      altKey: true
    });

    document.dispatchEvent(event);
    tick();

    expect(activatedSpy).toHaveBeenCalledWith('nav-home');
  }));

  it('should group shortcuts by category', () => {
    const grouped = service.getShortcutsByCategory();

    expect(grouped.has('Navigation')).toBeTrue();
    expect(grouped.has('Help')).toBeTrue();
    expect(grouped.has('General')).toBeTrue();
  });
});
