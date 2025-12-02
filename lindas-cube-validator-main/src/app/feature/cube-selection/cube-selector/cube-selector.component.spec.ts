import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CubeSelectorComponent } from './cube-selector.component';

describe('ValidatorComponent', () => {
  let component: CubeSelectorComponent;
  let fixture: ComponentFixture<CubeSelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeSelectorComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(CubeSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
