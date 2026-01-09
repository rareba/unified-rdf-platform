import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VirtualListComponent, VirtualTableComponent } from './virtual-list';
import { ScrollingModule } from '@angular/cdk/scrolling';

describe('VirtualListComponent', () => {
  let component: VirtualListComponent<string>;
  let fixture: ComponentFixture<VirtualListComponent<string>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VirtualListComponent, ScrollingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(VirtualListComponent<string>);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have default itemHeight of 48', () => {
    expect(component.itemHeight).toBe(48);
  });

  it('should have default containerHeight of 400', () => {
    expect(component.containerHeight).toBe(400);
  });

  it('should accept custom itemHeight', () => {
    component.itemHeight = 60;
    fixture.detectChanges();
    expect(component.itemHeight).toBe(60);
  });

  it('should accept custom containerHeight', () => {
    component.containerHeight = 500;
    fixture.detectChanges();
    expect(component.containerHeight).toBe(500);
  });

  it('should emit itemClick when item is clicked', () => {
    component.items = ['item1', 'item2', 'item3'];
    fixture.detectChanges();

    const emitSpy = spyOn(component.itemClick, 'emit');
    component.onItemClick('item2', 1);

    expect(emitSpy).toHaveBeenCalledWith({ item: 'item2', index: 1 });
  });

  it('should emit scrollIndexChange when scroll changes', () => {
    const emitSpy = spyOn(component.scrollIndexChange, 'emit');
    component.onScrollIndexChange(10);

    expect(emitSpy).toHaveBeenCalledWith(10);
  });

  it('should use default trackByFn', () => {
    const result = component.trackByFn(5, 'test');
    expect(result).toBe(5);
  });

  it('should accept custom trackByFn', () => {
    interface Item { id: number }
    const customTrackBy = (index: number, item: Item) => item.id;
    (component as unknown as VirtualListComponent<Item>).trackByFn = customTrackBy;

    const result = (component as unknown as VirtualListComponent<Item>).trackByFn(0, { id: 123 });
    expect(result).toBe(123);
  });

  it('should track selectedIndex', () => {
    component.selectedIndex = 5;
    expect(component.selectedIndex).toBe(5);
  });

  it('should render viewport element', () => {
    component.items = ['a', 'b', 'c'];
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const viewport = element.querySelector('cdk-virtual-scroll-viewport');
    expect(viewport).toBeTruthy();
  });

  it('should apply containerHeight to viewport', () => {
    component.items = ['a', 'b', 'c'];
    component.containerHeight = 300;
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const viewport = element.querySelector('cdk-virtual-scroll-viewport');
    expect(viewport.style.height).toBe('300px');
  });
});

describe('VirtualTableComponent', () => {
  let component: VirtualTableComponent<{ id: number; name: string }>;
  let fixture: ComponentFixture<VirtualTableComponent<{ id: number; name: string }>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VirtualTableComponent, ScrollingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(VirtualTableComponent<{ id: number; name: string }>);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have default rowHeight of 48', () => {
    expect(component.rowHeight).toBe(48);
  });

  it('should have default headerHeight of 56', () => {
    expect(component.headerHeight).toBe(56);
  });

  it('should have default bodyHeight of 400', () => {
    expect(component.bodyHeight).toBe(400);
  });

  it('should have striped as false by default', () => {
    expect(component.striped).toBeFalse();
  });

  it('should emit rowClick when row is clicked', () => {
    const row = { id: 1, name: 'Test' };
    const emitSpy = spyOn(component.rowClick, 'emit');

    component.onRowClick(row, 0);

    expect(emitSpy).toHaveBeenCalledWith({ row, index: 0 });
  });

  it('should toggle row selection', () => {
    expect(component.selectedRows.has(0)).toBeFalse();

    component.toggleRowSelection(0);
    expect(component.selectedRows.has(0)).toBeTrue();

    component.toggleRowSelection(0);
    expect(component.selectedRows.has(0)).toBeFalse();
  });

  it('should select all rows', () => {
    component.rows = [
      { id: 1, name: 'A' },
      { id: 2, name: 'B' },
      { id: 3, name: 'C' }
    ];

    component.selectAll();

    expect(component.selectedRows.size).toBe(3);
    expect(component.selectedRows.has(0)).toBeTrue();
    expect(component.selectedRows.has(1)).toBeTrue();
    expect(component.selectedRows.has(2)).toBeTrue();
  });

  it('should clear selection', () => {
    component.selectedRows.add(0);
    component.selectedRows.add(1);

    component.clearSelection();

    expect(component.selectedRows.size).toBe(0);
  });

  it('should render header area', () => {
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const header = element.querySelector('.virtual-table-header');
    expect(header).toBeTruthy();
  });

  it('should render body viewport', () => {
    component.rows = [{ id: 1, name: 'Test' }];
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const body = element.querySelector('.virtual-table-body');
    expect(body).toBeTruthy();
  });

  it('should apply bodyHeight to viewport', () => {
    component.bodyHeight = 350;
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const body = element.querySelector('.virtual-table-body');
    expect(body.style.height).toBe('350px');
  });
});
