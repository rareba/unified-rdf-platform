import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { ProjectContextService } from '../../services/project-context.service';

interface CountCard {
  key: string;
  label: string;
  icon: string;
  value: number;
}

const COUNT_META: Record<string, { label: string; icon: string }> = {
  pipelines:    { label: 'Pipelines',     icon: 'account_tree' },
  dataSources:  { label: 'Data Sources',  icon: 'storage' },
  shapes:       { label: 'SHACL Shapes',  icon: 'verified' },
  dimensions:   { label: 'Dimensions',    icon: 'share' },
  cubes:        { label: 'Cubes',         icon: 'view_in_ar' },
  jobs:         { label: 'Jobs',          icon: 'sync' },
  triplestores: { label: 'Triplestores',  icon: 'dns' }
};

@Component({
  selector: 'app-overview-tab',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatButtonModule
  ],
  templateUrl: './overview-tab.html',
  styleUrl: './overview-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OverviewTab {
  private readonly context = inject(ProjectContextService);

  readonly summary = this.context.currentSummary;

  readonly countCards = computed<CountCard[]>(() => {
    const counts = this.summary()?.counts ?? {};
    // Show all known keys, filling with 0 if missing. Also include extra keys
    // the backend might send (future-proof).
    const knownKeys = Object.keys(COUNT_META);
    const extraKeys = Object.keys(counts).filter(k => !knownKeys.includes(k));
    const allKeys = [...knownKeys, ...extraKeys];
    return allKeys.map(key => ({
      key,
      label: COUNT_META[key]?.label ?? this.humanize(key),
      icon: COUNT_META[key]?.icon ?? 'extension',
      value: counts[key] ?? 0
    }));
  });

  private humanize(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, c => c.toUpperCase())
      .trim();
  }
}
