import {
  Component,
  input,
  output,
  computed,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { ColumnMapping } from '../../../../core/models/cube.model';

interface RoleBadge {
  label: string;
  color: string;
  bg: string;
}

const ROLE_BADGE: Record<string, RoleBadge> = {
  dimension: { label: 'Dimension', color: '#6a1b9a', bg: '#f3e5f5' },
  measure:   { label: 'Measure',   color: '#1565c0', bg: '#e3f2fd' },
  attribute: { label: 'Attribute', color: '#2e7d32', bg: '#e8f5e9' }
};

@Component({
  selector: 'app-output-table-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatExpansionModule,
    MatDividerModule
  ],
  template: `
    <div class="output-panel">

      <div class="output-header">
        <span class="output-title">RDF Output Mappings</span>
        <span class="mapping-count">{{ visibleMappings().length }} active</span>
      </div>

      @if (visibleMappings().length === 0) {
        <div class="empty-state">
          <mat-icon>table_chart</mat-icon>
          <p>No column mappings yet.</p>
          <p class="empty-hint">Select columns on the left and configure their roles.</p>
        </div>
      }

      <div class="mappings-list">
        @for (mapping of visibleMappings(); track mapping.name) {
          <mat-card class="mapping-card" appearance="outlined">
            <mat-card-content>
              <div class="card-top">

                <!-- Role badge -->
                <span
                  class="role-badge"
                  [style.color]="getRoleBadge(mapping.role).color"
                  [style.background]="getRoleBadge(mapping.role).bg">
                  {{ getRoleBadge(mapping.role).label }}
                </span>

                <span class="property-name" [matTooltip]="mapping.predicateUri ?? ''">
                  {{ mapping.name }}
                </span>

                <span class="spacer"></span>

                <!-- Action buttons -->
                <button
                  mat-icon-button
                  (click)="editMapping.emit(mapping)"
                  matTooltip="Edit mapping"
                  aria-label="Edit mapping for {{ mapping.name }}">
                  <mat-icon>edit</mat-icon>
                </button>

                <button
                  mat-icon-button
                  (click)="deleteMapping.emit(mapping)"
                  matTooltip="Delete mapping"
                  aria-label="Delete mapping for {{ mapping.name }}"
                  class="delete-btn">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>

              <div class="card-chips">
                @if (mapping.datatype) {
                  <span class="info-chip datatype">{{ mapping.datatype }}</span>
                }

                @if (mapping.scaleType) {
                  <span class="info-chip scale">{{ mapping.scaleType }}</span>
                }

                @if (mapping.sharedDimensionUri) {
                  <span class="info-chip linked" [matTooltip]="mapping.sharedDimensionUri">
                    <mat-icon class="chip-icon">link</mat-icon>
                    Linked
                  </span>
                }

                @if (mapping.keyDimension) {
                  <span class="info-chip key">
                    <mat-icon class="chip-icon">key</mat-icon>
                    Key
                  </span>
                }
              </div>

              @if (mapping.predicateUri) {
                <div class="predicate-uri">{{ mapping.predicateUri }}</div>
              }

            </mat-card-content>
          </mat-card>
        }
      </div>

      <!-- CSVW preview expansion panel -->
      @if (visibleMappings().length > 0) {
        <div class="csvw-section">
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>
                <mat-icon>code</mat-icon>
                View generated CSVW (Turtle)
              </mat-panel-title>
            </mat-expansion-panel-header>
            <pre class="turtle-preview">{{ turtlePreview() }}</pre>
          </mat-expansion-panel>
        </div>
      }

    </div>
  `,
  styles: [`
    .output-panel {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }

    .output-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      border-bottom: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
      flex-shrink: 0;
    }

    .output-title {
      font-weight: 600;
      font-size: 0.875rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .mapping-count {
      padding: 2px 8px;
      border-radius: 10px;
      background: #e3f2fd;
      color: #1565c0;
      font-size: 0.75rem;
      font-weight: 500;
    }

    /* Empty state */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 48px 16px;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.6));
    }

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    .empty-state p {
      margin: 0;
    }

    .empty-hint {
      font-size: 0.8rem;
    }

    /* Mappings list */
    .mappings-list {
      flex: 1;
      overflow-y: auto;
      padding: 8px 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .mapping-card {
      border-radius: 8px;
    }

    .mapping-card mat-card-content {
      padding: 10px 12px !important;
    }

    .card-top {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .role-badge {
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 600;
      flex-shrink: 0;
    }

    .property-name {
      font-weight: 500;
      font-size: 0.875rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .spacer {
      flex: 1;
    }

    .delete-btn {
      color: var(--mat-sys-error, #d32f2f);
    }

    /* Chips row */
    .card-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin-top: 6px;
    }

    .info-chip {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 1px 7px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 500;
    }

    .info-chip.datatype  { background: #f5f5f5; color: #424242; }
    .info-chip.scale     { background: #fff3e0; color: #e65100; }
    .info-chip.linked    { background: #e8eaf6; color: #283593; }
    .info-chip.key       { background: #fce4ec; color: #880e4f; }

    .chip-icon {
      font-size: 12px;
      width: 12px;
      height: 12px;
    }

    .predicate-uri {
      margin-top: 4px;
      font-size: 0.7rem;
      color: var(--mat-sys-on-surface-variant, rgba(0,0,0,.5));
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* CSVW section */
    .csvw-section {
      flex-shrink: 0;
      border-top: 1px solid var(--mat-divider-color, rgba(0,0,0,.12));
    }

    .csvw-section mat-panel-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.875rem;
    }

    .turtle-preview {
      margin: 0;
      padding: 12px;
      background: #f8f8f8;
      border-radius: 4px;
      font-size: 0.75rem;
      line-height: 1.5;
      overflow-x: auto;
      white-space: pre;
      color: #263238;
    }
  `]
})
export class OutputTablePanel {
  readonly mappings = input<ColumnMapping[]>([]);
  readonly editMapping = output<ColumnMapping>();
  readonly deleteMapping = output<ColumnMapping>();

  readonly visibleMappings = computed(() =>
    this.mappings().filter(m => m.role !== 'ignore')
  );

  readonly turtlePreview = computed(() => this.generateTurtle(this.visibleMappings()));

  getRoleBadge(role: string): RoleBadge {
    return ROLE_BADGE[role] ?? { label: role, color: '#424242', bg: '#eeeeee' };
  }

  private generateTurtle(mappings: ColumnMapping[]): string {
    const prefixes = [
      '@prefix csvw: <http://www.w3.org/ns/csvw#> .',
      '@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .',
      '@prefix ex:   <https://example.org/> .',
      ''
    ].join('\n');

    if (mappings.length === 0) return prefixes + '# No mappings defined yet.';

    const colDefs = mappings.map(m => {
      const pred = m.predicateUri
        ? `<${m.predicateUri}>`
        : `ex:${m.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}`;

      const dt = m.datatype
        ? `xsd:${m.datatype.replace('xsd:', '')}`
        : 'xsd:string';

      return [
        `  [`,
        `    csvw:name "${m.name}" ;`,
        `    csvw:propertyUrl ${pred} ;`,
        `    csvw:datatype    ${dt} ;`,
        m.scaleType ? `    ex:scaleType    "${m.scaleType}" ;` : null,
        m.unitUri ? `    ex:unitUri      <${m.unitUri}> ;` : null,
        m.sharedDimensionUri ? `    ex:dimension    <${m.sharedDimensionUri}> ;` : null,
        `  ]`
      ]
        .filter(Boolean)
        .join('\n');
    });

    return [
      prefixes,
      '[] a csvw:TableGroup ;',
      '  csvw:table [',
      '    a csvw:Table ;',
      '    csvw:tableSchema [',
      '      csvw:column (',
      colDefs.join(' ,\n'),
      '      )',
      '    ]',
      '  ] .'
    ].join('\n');
  }
}
