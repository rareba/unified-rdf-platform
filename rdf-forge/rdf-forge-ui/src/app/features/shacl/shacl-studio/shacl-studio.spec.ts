import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShaclStudio } from './shacl-studio';

describe('ShaclStudio', () => {
  let component: ShaclStudio;
  let fixture: ComponentFixture<ShaclStudio>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShaclStudio]
    }).compileComponents();

    fixture = TestBed.createComponent(ShaclStudio);
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