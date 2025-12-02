import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Main application component.
 *
 * Oblique Migration Status:
 * - Oblique framework dependencies added (package.json)
 * - Oblique styles configured (angular.json)
 * - Swiss Federal colors integrated (styles.scss)
 * - Component-level migration: IN PROGRESS
 *
 * Migration Path:
 * 1. Current: Hybrid mode (PrimeNG + Oblique infrastructure)
 * 2. Next: Replace layout with ob-master-layout
 * 3. Then: Migrate individual feature components to Material/Oblique
 *
 * See: https://oblique.bit.admin.ch for Oblique documentation
 */
@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly sidebarCollapsed = signal(false);

  // Application branding
  readonly appTitle = 'Cube Creator X';
  readonly appVersion = '1.0.0';

  toggleSidebar(): void {
    this.sidebarCollapsed.update((v) => !v);
  }

  readonly currentPageTitle = computed(() => {
    return this.appTitle;
  });
}
