import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ObservationValidationComponent } from './observation-validation.component';

describe('ObservationValidationComponent', () => {
  let component: ObservationValidationComponent;
  let fixture: ComponentFixture<ObservationValidationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ObservationValidationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ObservationValidationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
