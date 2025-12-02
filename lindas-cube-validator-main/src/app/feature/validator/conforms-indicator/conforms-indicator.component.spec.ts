import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConformsIndicatorComponent } from './conforms-indicator.component';

describe('ConformsIndicatorComponent', () => {
  let component: ConformsIndicatorComponent;
  let fixture: ComponentFixture<ConformsIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConformsIndicatorComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ConformsIndicatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
