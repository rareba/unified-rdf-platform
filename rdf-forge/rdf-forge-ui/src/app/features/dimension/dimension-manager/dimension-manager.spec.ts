import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DimensionManager } from './dimension-manager';

describe('DimensionManager', () => {
  let component: DimensionManager;
  let fixture: ComponentFixture<DimensionManager>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DimensionManager]
    }).compileComponents();

    fixture = TestBed.createComponent(DimensionManager);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });
});