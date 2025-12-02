import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ObservationValidationBarnard59CliCommandComponent } from './observation-validation-barnard59-cli-command.component';

describe('ObservationValidationBarnard59CliCommandComponent', () => {
  let component: ObservationValidationBarnard59CliCommandComponent;
  let fixture: ComponentFixture<ObservationValidationBarnard59CliCommandComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ObservationValidationBarnard59CliCommandComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ObservationValidationBarnard59CliCommandComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
