import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MappingService } from '../../core/services/mapping.service';
import { Mapping } from '../../core/models/mapping.model';
import { MappingCreateDialog } from './mapping-create-dialog';

/**
 * Project-scoped list of Mapping entities. Rendered inside the Project
 * Workspace "Mapping" tab, or reachable as a standalone page at
 * {@code /projects/:id/mapping}. Accepts {@code [projectId]} as an Angular
 * signal input so it can also be embedded outside a router outlet (e.g. from
 * the mapping tab wrapper) with a direct binding.
 */
@Component({
  selector: 'app-mapping-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatMenuModule,
    MatSnackBarModule,
    MatDialogModule
  ],
  template: `
    <div class="mapping-list">
      <div class="header">
        <div>
          <h2>Mappings</h2>
          <p class="subtitle">
            Author source-to-RDF mappings. Each mapping produces the triples your
            pipeline emits at run time.
          </p>
        </div>
        <button mat-raised-button color="primary"
                [disabled]="!projectId()"
                (click)="openCreateDialog()">
          <mat-icon>add</mat-icon>
          New Mapping
        </button>
      </div>

      @if (loading()) {
        <div class="centered">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (mappings().length === 0) {
        <mat-card class="empty">
          <mat-card-content>
            <mat-icon class="empty-icon">transform</mat-icon>
            <h3>No mappings yet</h3>
            <p>Create your first mapping to generate RDF from a source file.</p>
            <button mat-raised-button color="primary"
                    [disabled]="!projectId()"
                    (click)="openCreateDialog()">
              <mat-icon>add</mat-icon>
              Create Mapping
            </button>
          </mat-card-content>
        </mat-card>
      } @else {
        <mat-card>
          <mat-card-content>
            <table mat-table [dataSource]="mappings()" class="full-width">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let m">
                  <a [routerLink]="['/mappings', m.id]" class="mapping-link">{{ m.name }}</a>
                </td>
              </ng-container>

              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let m">
                  <mat-chip [class.type-cube]="m.mappingType === 'CUBE'">{{ m.mappingType }}</mat-chip>
                </td>
              </ng-container>

              <ng-container matColumnDef="source">
                <th mat-header-cell *matHeaderCellDef>Source</th>
                <td mat-cell *matCellDef="let m">{{ m.sourceType }}</td>
              </ng-container>

              <ng-container matColumnDef="rules">
                <th mat-header-cell *matHeaderCellDef>Rules</th>
                <td mat-cell *matCellDef="let m">{{ m.rules.length }}</td>
              </ng-container>

              <ng-container matColumnDef="updated">
                <th mat-header-cell *matHeaderCellDef>Updated</th>
                <td mat-cell *matCellDef="let m">{{ m.updatedAt | date:'short' }}</td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let m">
                  <button mat-icon-button [matMenuTriggerFor]="menu"
                          (click)="$event.stopPropagation()"
                          aria-label="mapping actions">
                    <mat-icon>more_vert</mat-icon>
                  </button>
                  <mat-menu #menu>
                    <a mat-menu-item [routerLink]="['/mappings', m.id]">
                      <mat-icon>edit</mat-icon> Open
                    </a>
                    <button mat-menu-item (click)="remove(m)">
                      <mat-icon>delete</mat-icon> Delete
                    </button>
                  </mat-menu>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"
                  class="clickable-row"
                  (click)="open(row)"></tr>
            </table>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .mapping-list { padding: 16px; }
    .header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 16px; gap: 16px;
    }
    .header h2 { margin: 0 0 4px 0; }
    .subtitle { color: var(--rdf-text-secondary); margin: 0; font-size: 0.9rem; max-width: 640px; }
    .centered { display: flex; justify-content: center; padding: 48px; }
    .empty {
      text-align: center; padding: 48px 16px;
      mat-card-content { display: flex; flex-direction: column; align-items: center; gap: 12px; }
      .empty-icon { font-size: 64px; width: 64px; height: 64px; color: var(--rdf-text-secondary); }
      h3 { margin: 0; }
      p { margin: 0; color: var(--rdf-text-secondary); }
    }
    .full-width { width: 100%; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: rgba(0,0,0,0.03); }
    .mapping-link { font-weight: 500; text-decoration: none; color: var(--mat-sys-primary); }
    .type-cube { background: var(--mat-sys-tertiary-container) !important; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MappingList implements OnInit {
  private readonly svc = inject(MappingService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);

  /** Required — the project whose mappings we list. */
  readonly projectId = input.required<string>();

  readonly mappings = signal<Mapping[]>([]);
  readonly loading = signal(false);
  readonly columns = ['name', 'type', 'source', 'rules', 'updated', 'actions'];

  constructor() {
    // Reload when the project id changes. This covers both route navigations
    // and embedded usage where the parent passes a new projectId signal.
    effect(() => {
      const pid = this.projectId();
      if (pid) this.reload(pid);
    });
  }

  ngOnInit(): void { /* effect handles initial load */ }

  reload(projectId: string): void {
    this.loading.set(true);
    this.svc.listByProject(projectId).subscribe({
      next: list => { this.mappings.set(list); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        this.snack.open('Failed to load mappings: ' + (err?.message ?? err), 'Dismiss',
          { duration: 4000 });
      }
    });
  }

  openCreateDialog(): void {
    const pid = this.projectId();
    if (!pid) return;
    const ref = this.dialog.open(MappingCreateDialog, {
      width: '520px',
      data: { projectId: pid }
    });
    ref.afterClosed().subscribe((created: Mapping | undefined) => {
      if (created) {
        this.router.navigate(['/mappings', created.id]);
      }
    });
  }

  open(m: Mapping): void {
    this.router.navigate(['/mappings', m.id]);
  }

  remove(m: Mapping): void {
    if (!confirm(`Delete mapping "${m.name}"?`)) return;
    this.svc.delete(m.id).subscribe({
      next: () => {
        const pid = this.projectId();
        if (pid) this.reload(pid);
      },
      error: err => this.snack.open('Delete failed: ' + (err?.message ?? err), 'Dismiss',
        { duration: 4000 })
    });
  }
}
