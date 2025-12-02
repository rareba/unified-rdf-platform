import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlaygroundLinkComponent } from './playground-link.component';

describe('PlaygroundLinkComponent', () => {
  let component: PlaygroundLinkComponent;
  let fixture: ComponentFixture<PlaygroundLinkComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlaygroundLinkComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PlaygroundLinkComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
