import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShapeEditor } from './shape-editor';

describe('ShapeEditor', () => {
  let component: ShapeEditor;
  let fixture: ComponentFixture<ShapeEditor>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShapeEditor]
    }).compileComponents();

    fixture = TestBed.createComponent(ShapeEditor);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the component', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled).toBeTruthy();
  });
});