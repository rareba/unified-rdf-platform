import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-settings',
  imports: [CommonModule, CardModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  readonly env = environment;

  get authStatus(): string {
    return this.env.auth.enabled ? 'Enabled' : 'Disabled (Offline Mode)';
  }
}
