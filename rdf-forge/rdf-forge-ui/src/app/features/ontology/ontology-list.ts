import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OntologyService } from '../../core/services/ontology.service';
import { Ontology } from '../../core/models';
import { OntologyImport } from './ontology-import';

/**
 * Lists ontologies for a given project. Used both at the dedicated
 * /projects/:id/ontology tab (where projectId comes from context) and inline
 * in workspace tabs.
 */
@Component({
  selector: 'app-ontology-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatSnackBarModule
  ],
  template: `
    <mat-card class="ontology-list-card">
      <mat-card-header>
        <mat-card-title>
          <mat-icon>schema</mat-icon>
          Ontologies
        </mat-card-title>
        <mat-card-subtitle>
          @if (ontologies().length === 0 && !loading()) {
            No ontologies yet — import one to start describing your domain.
          } @else {
            {{ ontologies().length }} ontolog{{ ontologies().length === 1 ? 'y' : 'ies' }} in this project
          }
        </mat-card-subtitle>
        <span class="spacer"></span>
        <button mat-raised-button color="primary"
                [disabled]="!projectId()"
                (click)="openImport()">
          <mat-icon>upload_file</mat-icon>
          Import Ontology
        </button>
      </mat-card-header>

      <mat-card-content>
        @if (loading()) {
          <div class="center"><mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner></div>
        } @else if (error()) {
          <div class="error">
            <mat-icon>error</mat-icon>
            <span>{{ error() }}</span>
          </div>
        } @else if (ontologies().length === 0) {
          <div class="empty">
            <mat-icon>inventory_2</mat-icon>
            <p>No ontologies imported yet.</p>
          </div>
        } @else {
          <table mat-table [dataSource]="ontologies()" class="ontology-table">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let o">
                <a [routerLink]="['/ontologies', o.id]">{{ o.name }}</a>
                @if (o.description) {
                  <div class="description">{{ o.description }}</div>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="namespace">
              <th mat-header-cell *matHeaderCellDef>Namespace</th>
              <td mat-cell *matCellDef="let o">
                <code class="ns">{{ o.namespace }}</code>
                @if (o.prefix) {
                  <mat-chip class="prefix-chip">{{ o.prefix }}</mat-chip>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="format">
              <th mat-header-cell *matHeaderCellDef>Format</th>
              <td mat-cell *matCellDef="let o">
                <mat-chip>{{ o.format }}</mat-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="stats">
              <th mat-header-cell *matHeaderCellDef>Content</th>
              <td mat-cell *matCellDef="let o">
                <span class="stat" matTooltip="Triples">
                  <mat-icon>data_object</mat-icon>{{ tripleCount(o) }}
                </span>
                <span class="stat" matTooltip="Classes">
                  <mat-icon>class</mat-icon>{{ classCount(o) }}
                </span>
                <span class="stat" matTooltip="Properties">
                  <mat-icon>tune</mat-icon>{{ propCount(o) }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="version">
              <th mat-header-cell *matHeaderCellDef>Version</th>
              <td mat-cell *matCellDef="let o">v{{ o.version }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let o">
                <button mat-icon-button matTooltip="Open"
                        [routerLink]="['/ontologies', o.id]">
                  <mat-icon>open_in_new</mat-icon>
                </button>
                <button mat-icon-button matTooltip="Delete" color="warn"
                        (click)="remove(o)">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .ontology-list-card {
      width: 100%;
    }
    mat-card-header {
      align-items: center;
      gap: 8px;
    }
    mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .spacer {
      flex: 1 1 auto;
    }
    .center, .empty, .error {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px;
      color: var(--rdf-text-secondary);
      gap: 8px;
    }
    .error {
      color: #b91c1c;
      flex-direction: row;
    }
    .ontology-table {
      width: 100%;
    }
    .description {
      font-size: 0.85em;
      color: var(--rdf-text-secondary);
    }
    .ns {
      font-size: 0.85em;
      word-break: break-all;
    }
    .prefix-chip {
      margin-left: 8px;
    }
    .stat {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-right: 12px;
      font-size: 0.85em;
      color: var(--rdf-text-secondary);
    }
    .stat mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OntologyList implements OnInit, OnChanges {
  private readonly ontologyService = inject(OntologyService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  @Input() projectIdInput: string | null = null;

  readonly projectId = signal<string | null>(null);
  readonly ontologies = signal<Ontology[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly columns = ['name', 'namespace', 'format', 'stats', 'version', 'actions'];

  /** Imperative setter — tab components use this in addition to the Input. */
  setProjectId(id: string | null): void {
    this.projectId.set(id);
    if (id) {
      this.reload();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectIdInput']) {
      this.setProjectId(this.projectIdInput);
    }
  }

  ngOnInit(): void {
    if (this.projectIdInput && !this.projectId()) {
      this.setProjectId(this.projectIdInput);
    }
    if (this.projectId()) {
      this.reload();
    }
  }

  reload(): void {
    const pid = this.projectId();
    if (!pid) return;
    this.loading.set(true);
    this.error.set(null);
    this.ontologyService.list(pid).subscribe({
      next: list => {
        this.ontologies.set(list ?? []);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err?.error?.detail ?? err?.message ?? 'Failed to load ontologies');
        this.loading.set(false);
      }
    });
  }

  openImport(): void {
    const pid = this.projectId();
    if (!pid) return;
    const ref = this.dialog.open(OntologyImport, {
      width: '640px',
      data: { projectId: pid }
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snack.open('Ontology imported', 'Close', { duration: 3000 });
        this.reload();
      }
    });
  }

  remove(o: Ontology): void {
    if (!confirm(`Delete ontology "${o.name}"? This cannot be undone.`)) return;
    this.ontologyService.delete(o.id).subscribe({
      next: () => {
        this.snack.open('Ontology deleted', 'Close', { duration: 2500 });
        this.reload();
      },
      error: err => {
        this.snack.open(
          'Failed to delete: ' + (err?.error?.detail ?? err?.message ?? 'unknown error'),
          'Close',
          { duration: 5000 }
        );
      }
    });
  }

  tripleCount = (o: Ontology) => this.statOf(o, 'tripleCount');
  classCount = (o: Ontology) => this.statOf(o, 'classCount');
  propCount = (o: Ontology) => this.statOf(o, 'propertyCount');

  private statOf(o: Ontology, key: string): string {
    const v = o.metadata?.[key];
    if (typeof v === 'number') return String(v);
    return '-';
  }
}
