import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
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
