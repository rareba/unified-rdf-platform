import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { ExtensionDescriptor, EXTENSION_KIND_LABELS } from '../../core/models';

/**
 * Dialog showing the full descriptor for a single extension.
 * Opened from the catalog table; renders parameters, capabilities,
 * a doc link when present, and a copyable example-config snippet.
 */
@Component({
  selector: 'app-extension-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatChipsModule,
    MatIconModule,
    MatDividerModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>{{ iconFor(ext.kind) }}</mat-icon>
      {{ ext.name }}
      <span class="kind">{{ kindLabel }}</span>
    </h2>

    <mat-dialog-content>
      <p class="desc">{{ ext.description || 'No description provided.' }}</p>

      <mat-divider></mat-divider>

      <dl>
        <dt>Id</dt><dd><code>{{ ext.id }}</code></dd>
        <dt>Version</dt><dd>{{ ext.version }}</dd>
        <dt>Provided by</dt><dd><code>{{ ext.providedBy }}</code></dd>
        <dt>Availability</dt>
        <dd>
          @if (ext.available) {
            <mat-chip class="ok">Available</mat-chip>
          } @else {
            <mat-chip class="unavailable">Coming soon</mat-chip>
          }
        </dd>
        @if (ext.docUrl) {
          <dt>Documentation</dt>
          <dd>
            <a [href]="ext.docUrl" target="_blank" rel="noopener">
              {{ ext.docUrl }}
              <mat-icon inline="true">open_in_new</mat-icon>
            </a>
          </dd>
        }
      </dl>

      @if (ext.capabilities.length) {
        <h3>Capabilities</h3>
        <div class="chips">
          @for (cap of ext.capabilities; track cap) {
            <mat-chip>{{ cap }}</mat-chip>
          }
        </div>
      }

      @if (paramEntries.length) {
        <h3>Parameters</h3>
        <table class="params">
          <thead><tr><th>Name</th><th>Type / description</th></tr></thead>
          <tbody>
            @for (p of paramEntries; track p.name) {
              <tr>
                <td><code>{{ p.name }}</code></td>
                <td>{{ p.value }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      <h3>Example config</h3>
      <pre><code>{{ exampleConfig }}</code></pre>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host { display: block; min-width: 480px; }
    h2 { display: flex; align-items: center; gap: .5rem; }
    h2 .kind { font-size: .8rem; color: #666; font-weight: 400; }
    .desc { margin-top: 0; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: .25rem 1rem; margin: 1rem 0; }
    dt { font-weight: 600; color: #555; }
    .chips { display: flex; flex-wrap: wrap; gap: .25rem; }
    table.params { border-collapse: collapse; width: 100%; margin-top: .5rem; }
    table.params th, table.params td { border: 1px solid #eee; padding: .25rem .5rem; text-align: left; }
    pre { background: #f5f5f5; padding: .5rem; border-radius: 4px; overflow: auto; }
    mat-chip.ok { background-color: #c8e6c9; }
    mat-chip.unavailable { background-color: #fff9c4; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExtensionDetail {
  readonly ext: ExtensionDescriptor;
  readonly kindLabel: string;
  readonly paramEntries: { name: string; value: string }[];
  readonly exampleConfig: string;

  constructor(
    private readonly ref: MatDialogRef<ExtensionDetail>,
    @Inject(MAT_DIALOG_DATA) data: ExtensionDescriptor
  ) {
    this.ext = data;
    this.kindLabel = EXTENSION_KIND_LABELS[data.kind] ?? data.kind;
    this.paramEntries = Object.entries(data.parameters ?? {}).map(
      ([name, value]) => ({ name, value })
    );
    this.exampleConfig = JSON.stringify(
      {
        id: data.id,
        kind: data.kind,
        parameters: Object.fromEntries(
          Object.keys(data.parameters ?? {}).map(k => [k, '<value>'])
        )
      },
      null,
      2
    );
  }

  close(): void { this.ref.close(); }

  iconFor(kind: ExtensionDescriptor['kind']): string {
    switch (kind) {
      case 'OPERATION': return 'bolt';
      case 'FORMAT': return 'table_chart';
      case 'STORAGE_PROVIDER': return 'cloud';
      case 'DESTINATION': return 'output';
      case 'TRIPLESTORE_PROVIDER': return 'dns';
      case 'MATCHER': return 'compare_arrows';
      case 'VALIDATOR': return 'rule';
      case 'CUBE_PROFILE': return 'view_in_ar';
      default: return 'extension';
    }
  }
}
