import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PipelineDesigner } from './pipeline-designer';

describe('PipelineDesigner', () => {
  let component: PipelineDesigner;
  let fixture: ComponentFixture<PipelineDesigner>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineDesigner]
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineDesigner);
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