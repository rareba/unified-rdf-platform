import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CubeValidationBarnard59CliCommandComponent } from './cub-validation-barnard59-cli-command.component';

describe('Barnard59CliCommandComponent', () => {
  let component: CubeValidationBarnard59CliCommandComponent;
  let fixture: ComponentFixture<CubeValidationBarnard59CliCommandComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeValidationBarnard59CliCommandComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(CubeValidationBarnard59CliCommandComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
