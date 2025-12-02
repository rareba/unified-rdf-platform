import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CubeValidationComponent } from './cube-validation.component';

describe('CubeValidationComponent', () => {
  let component: CubeValidationComponent;
  let fixture: ComponentFixture<CubeValidationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeValidationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CubeValidationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
