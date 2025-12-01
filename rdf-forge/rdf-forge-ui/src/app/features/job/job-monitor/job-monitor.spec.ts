import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JobMonitor } from './job-monitor';

describe('JobMonitor', () => {
  let component: JobMonitor;
  let fixture: ComponentFixture<JobMonitor>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobMonitor]
    }).compileComponents();

    fixture = TestBed.createComponent(JobMonitor);
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