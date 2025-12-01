import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CubeWizard } from './cube-wizard';

describe('CubeWizard', () => {
  let component: CubeWizard;
  let fixture: ComponentFixture<CubeWizard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeWizard]
    }).compileComponents();

    fixture = TestBed.createComponent(CubeWizard);
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