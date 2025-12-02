import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ValidationReportComponent } from './validation-report.component';

describe('ValidationReportComponent', () => {
  let component: ValidationReportComponent;
  let fixture: ComponentFixture<ValidationReportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ValidationReportComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ValidationReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
