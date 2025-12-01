import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DataManager } from './data-manager';

describe('DataManager', () => {
  let component: DataManager;
  let fixture: ComponentFixture<DataManager>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataManager]
    }).compileComponents();

    fixture = TestBed.createComponent(DataManager);
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