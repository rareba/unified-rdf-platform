import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PipelineList } from './pipeline-list';

describe('PipelineList', () => {
  let component: PipelineList;
  let fixture: ComponentFixture<PipelineList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineList]
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineList);
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