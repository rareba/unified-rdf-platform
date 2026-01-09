import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SkeletonLoaderComponent } from './skeleton-loader';

describe('SkeletonLoaderComponent', () => {
  let component: SkeletonLoaderComponent;
  let fixture: ComponentFixture<SkeletonLoaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonLoaderComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(SkeletonLoaderComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should default to text type', () => {
    expect(component.type).toBe('text');
  });

  it('should render text skeleton by default', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const skeletonText = element.querySelector('.skeleton-text');
    expect(skeletonText).toBeTruthy();
  }));

  it('should render circle skeleton', fakeAsync(() => {
    component.type = 'circle';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const circle = element.querySelector('.skeleton-circle');
    expect(circle).toBeTruthy();
  }));

  it('should render rectangle skeleton', fakeAsync(() => {
    component.type = 'rectangle';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const rectangle = element.querySelector('.skeleton-rectangle');
    expect(rectangle).toBeTruthy();
  }));

  it('should render card skeleton', fakeAsync(() => {
    component.type = 'card';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const card = element.querySelector('.skeleton-card');
    expect(card).toBeTruthy();
  }));

  it('should render table-row skeleton', fakeAsync(() => {
    component.type = 'table-row';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const tableRow = element.querySelector('.skeleton-table-row');
    expect(tableRow).toBeTruthy();
  }));

  it('should render list-item skeleton', fakeAsync(() => {
    component.type = 'list-item';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const listItem = element.querySelector('.skeleton-list-item');
    expect(listItem).toBeTruthy();
  }));

  it('should render multiple lines when count > 1', fakeAsync(() => {
    component.type = 'text';
    component.count = 3;
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const lines = element.querySelectorAll('.skeleton-text');
    expect(lines.length).toBe(3);
  }));

  it('should have animate class by default', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const skeleton = element.querySelector('.skeleton');
    expect(skeleton.classList.contains('animate')).toBeTrue();
  }));

  it('should apply container class', fakeAsync(() => {
    component.containerClass = 'custom-class';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const container = element.querySelector('.skeleton-container');
    expect(container.classList.contains('custom-class')).toBeTrue();
  }));

  it('should vary line widths for natural look', () => {
    component.count = 4;
    const widths = [
      component.getLineWidth(0),
      component.getLineWidth(1),
      component.getLineWidth(2),
      component.getLineWidth(3)
    ];

    // Last line should be shorter
    expect(widths[3]).toBe('70%');
  });

  it('should generate lines array based on count', () => {
    component.count = 5;
    expect(component.lines.length).toBe(5);
  });

  it('should accept custom width', () => {
    component.width = '200px';
    expect(component.width).toBe('200px');
  });

  it('should accept custom height', () => {
    component.height = '100px';
    expect(component.height).toBe('100px');
  });

  it('should accept animate flag', () => {
    component.animate = false;
    expect(component.animate).toBeFalse();
  });

  it('should accept showAvatar flag', () => {
    component.showAvatar = false;
    expect(component.showAvatar).toBeFalse();
  });

  it('should accept columns config', () => {
    component.columns = [{ flex: 1 }, { flex: 2 }];
    expect(component.columns.length).toBe(2);
  });
});
