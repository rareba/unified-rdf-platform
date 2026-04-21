import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OntologyService } from '../../core/services/ontology.service';
import { OntologyNamespace } from '../../core/models';

/**
 * Read-only table of namespace prefix-to-URI bindings with per-row copy.
 */
@Component({
  selector: 'app-namespace-manager',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule
  ],
  template: `
    @if (loading()) {
      <div class="center">
        <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
      </div>
    } @else if (entries().length === 0) {
      <p class="empty">No namespace declarations found.</p>
    } @else {
      <table mat-table [dataSource]="entries()" class="ns-table">
        <ng-container matColumnDef="prefix">
          <th mat-header-cell *matHeaderCellDef>Prefix</th>
          <td mat-cell *matCellDef="let e">
            <code>{{ e.prefix || '(default)' }}</code>
          </td>
        </ng-container>

        <ng-container matColumnDef="uri">
          <th mat-header-cell *matHeaderCellDef>Namespace IRI</th>
          <td mat-cell *matCellDef="let e">
            <code>{{ e.uri }}</code>
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let e">
            <button mat-icon-button matTooltip="Copy URI" (click)="copy(e.uri)">
              <mat-icon>content_copy</mat-icon>
            </button>
            <button mat-icon-button matTooltip="Copy prefix declaration" (click)="copyDeclaration(e)">
              <mat-icon>content_paste</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
  styles: [`
    .center, .empty {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 24px;
      color: var(--rdf-text-secondary);
    }
    .ns-table { width: 100%; }
    code {
      font-size: 0.85em;
      word-break: break-all;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NamespaceManager implements OnChanges {
  private readonly service = inject(OntologyService);
  private readonly snack = inject(MatSnackBar);

  @Input({ required: true }) ontologyId!: string;

  readonly entries = signal<OntologyNamespace[]>([]);
  readonly loading = signal(false);
  readonly columns = ['prefix', 'uri', 'actions'];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['ontologyId']?.currentValue) {
      this.reload();
    }
  }

  reload(): void {
    if (!this.ontologyId) return;
    this.loading.set(true);
    this.service.namespaces(this.ontologyId).subscribe({
      next: map => {
        this.entries.set(map?.entries ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.entries.set([]);
        this.loading.set(false);
      }
    });
  }

  copy(text: string): void {
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(text).then(
      () => this.snack.open('Copied', 'Close', { duration: 1500 }),
      () => this.snack.open('Copy failed', 'Close', { duration: 2000 })
    );
  }

  copyDeclaration(e: OntologyNamespace): void {
    const text = `@prefix ${e.prefix || ''}: <${e.uri}> .`;
    this.copy(text);
  }
}
