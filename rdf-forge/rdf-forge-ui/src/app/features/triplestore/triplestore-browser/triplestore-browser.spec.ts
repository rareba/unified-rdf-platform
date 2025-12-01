import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TriplestoreBrowser } from './triplestore-browser';

describe('TriplestoreBrowser', () => {
  let component: TriplestoreBrowser;
  let fixture: ComponentFixture<TriplestoreBrowser>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TriplestoreBrowser]
    }).compileComponents();

    fixture = TestBed.createComponent(TriplestoreBrowser);
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