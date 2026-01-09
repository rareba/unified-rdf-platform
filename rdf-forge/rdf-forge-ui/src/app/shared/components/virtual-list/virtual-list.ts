import {
  Component,
  Input,
  Output,
  EventEmitter,
  ContentChild,
  TemplateRef,
  TrackByFunction
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';

/**
 * A wrapper component for virtual scrolling large lists.
 * Uses Angular CDK's virtual scroll viewport for efficient rendering.
 *
 * Usage:
 * ```html
 * <app-virtual-list
 *   [items]="myLargeList"
 *   [itemHeight]="48"
 *   [containerHeight]="400"
 *   [trackByFn]="trackById">
 *   <ng-template let-item let-index="index">
 *     <div class="list-item">{{ item.name }}</div>
 *   </ng-template>
 * </app-virtual-list>
 * ```
 */
@Component({
  selector: 'app-virtual-list',
  standalone: true,
  imports: [CommonModule, ScrollingModule],
  template: `
    <cdk-virtual-scroll-viewport
      [itemSize]="itemHeight"
      [style.height.px]="containerHeight"
      class="virtual-scroll-viewport"
      (scrolledIndexChange)="onScrollIndexChange($event)">
      <div
        *cdkVirtualFor="let item of items; let i = index; trackBy: trackByFn"
        class="virtual-item"
        [style.height.px]="itemHeight"
        [class.selected]="selectedIndex === i"
        (click)="onItemClick(item, i)">
        <ng-container
          *ngTemplateOutlet="itemTemplate; context: { $implicit: item, index: i }">
        </ng-container>
      </div>
    </cdk-virtual-scroll-viewport>
  `,
  styles: [`
    .virtual-scroll-viewport {
      width: 100%;
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 4px;
    }

    .virtual-item {
      display: flex;
      align-items: center;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);
      box-sizing: border-box;
      cursor: pointer;
      transition: background-color 0.15s ease;
    }

    .virtual-item:hover {
      background-color: rgba(0, 0, 0, 0.04);
    }

    .virtual-item.selected {
      background-color: rgba(25, 118, 210, 0.08);
    }

    :host-context(.dark-theme) .virtual-scroll-viewport {
      border-color: rgba(255, 255, 255, 0.12);
    }

    :host-context(.dark-theme) .virtual-item {
      border-bottom-color: rgba(255, 255, 255, 0.06);
    }

    :host-context(.dark-theme) .virtual-item:hover {
      background-color: rgba(255, 255, 255, 0.04);
    }
  `]
})
export class VirtualListComponent<T> {
  @Input() items: T[] = [];
  @Input() itemHeight = 48;
  @Input() containerHeight = 400;
  @Input() trackByFn: TrackByFunction<T> = (index: number) => index;
  @Input() selectedIndex: number | null = null;

  @Output() itemClick = new EventEmitter<{ item: T; index: number }>();
  @Output() scrollIndexChange = new EventEmitter<number>();

  @ContentChild(TemplateRef) itemTemplate!: TemplateRef<{ $implicit: T; index: number }>;

  onItemClick(item: T, index: number): void {
    this.itemClick.emit({ item, index });
  }

  onScrollIndexChange(index: number): void {
    this.scrollIndexChange.emit(index);
  }
}

/**
 * A virtual table component for large datasets.
 * Combines virtual scrolling with table layout.
 */
@Component({
  selector: 'app-virtual-table',
  standalone: true,
  imports: [CommonModule, ScrollingModule],
  template: `
    <div class="virtual-table-container">
      <!-- Fixed Header -->
      <div class="virtual-table-header" [style.height.px]="headerHeight">
        <ng-content select="[tableHeader]"></ng-content>
      </div>

      <!-- Scrollable Body -->
      <cdk-virtual-scroll-viewport
        [itemSize]="rowHeight"
        [style.height.px]="bodyHeight"
        class="virtual-table-body">
        <div
          *cdkVirtualFor="let row of rows; let i = index; trackBy: trackByFn"
          class="virtual-table-row"
          [style.height.px]="rowHeight"
          [class.striped]="striped && i % 2 === 1"
          [class.selected]="selectedRows.has(i)"
          (click)="onRowClick(row, i)">
          <ng-container
            *ngTemplateOutlet="rowTemplate; context: { $implicit: row, index: i }">
          </ng-container>
        </div>
      </cdk-virtual-scroll-viewport>
    </div>
  `,
  styles: [`
    .virtual-table-container {
      display: flex;
      flex-direction: column;
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 4px;
      overflow: hidden;
    }

    .virtual-table-header {
      display: flex;
      align-items: center;
      background-color: #fafafa;
      border-bottom: 1px solid rgba(0, 0, 0, 0.12);
      font-weight: 500;
    }

    .virtual-table-body {
      width: 100%;
    }

    .virtual-table-row {
      display: flex;
      align-items: center;
      border-bottom: 1px solid rgba(0, 0, 0, 0.06);
      box-sizing: border-box;
      transition: background-color 0.15s ease;
    }

    .virtual-table-row:hover {
      background-color: rgba(0, 0, 0, 0.04);
    }

    .virtual-table-row.striped {
      background-color: rgba(0, 0, 0, 0.02);
    }

    .virtual-table-row.striped:hover {
      background-color: rgba(0, 0, 0, 0.06);
    }

    .virtual-table-row.selected {
      background-color: rgba(25, 118, 210, 0.08);
    }

    :host-context(.dark-theme) .virtual-table-container {
      border-color: rgba(255, 255, 255, 0.12);
    }

    :host-context(.dark-theme) .virtual-table-header {
      background-color: #2d2d2d;
      border-bottom-color: rgba(255, 255, 255, 0.12);
    }

    :host-context(.dark-theme) .virtual-table-row {
      border-bottom-color: rgba(255, 255, 255, 0.06);
    }

    :host-context(.dark-theme) .virtual-table-row.striped {
      background-color: rgba(255, 255, 255, 0.02);
    }
  `]
})
export class VirtualTableComponent<T> {
  @Input() rows: T[] = [];
  @Input() rowHeight = 48;
  @Input() headerHeight = 56;
  @Input() bodyHeight = 400;
  @Input() striped = false;
  @Input() trackByFn: TrackByFunction<T> = (index: number) => index;

  @Output() rowClick = new EventEmitter<{ row: T; index: number }>();

  @ContentChild(TemplateRef) rowTemplate!: TemplateRef<{ $implicit: T; index: number }>;

  selectedRows = new Set<number>();

  onRowClick(row: T, index: number): void {
    this.rowClick.emit({ row, index });
  }

  toggleRowSelection(index: number): void {
    if (this.selectedRows.has(index)) {
      this.selectedRows.delete(index);
    } else {
      this.selectedRows.add(index);
    }
  }

  selectAll(): void {
    for (let i = 0; i < this.rows.length; i++) {
      this.selectedRows.add(i);
    }
  }

  clearSelection(): void {
    this.selectedRows.clear();
  }
}
