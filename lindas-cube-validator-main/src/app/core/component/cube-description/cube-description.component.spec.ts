import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CubeDescriptionComponent } from './cube-description.component';

describe('CubeDescriptionComponent', () => {
  let component: CubeDescriptionComponent;
  let fixture: ComponentFixture<CubeDescriptionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CubeDescriptionComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CubeDescriptionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
