import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CubeConstraintsValidationReportComponent } from './cube-constraints-validation-report.component';

describe('CubeConstraintsValidationReportComponent', () => {
  let component: CubeConstraintsValidationReportComponent;
  let fixture: ComponentFixture<CubeConstraintsValidationReportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeConstraintsValidationReportComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CubeConstraintsValidationReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
