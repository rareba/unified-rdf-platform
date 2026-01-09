import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ChangeDetectorRef } from '@angular/core';

describe('EmptyStateComponent', () => {
  let component: EmptyStateComponent;
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should default to no-data type', () => {
    expect(component.type).toBe('no-data');
  });

  it('should show default title for no-data type', () => {
    expect(component.getDefaultTitle()).toBe('No data found');
  });

  it('should show default title for error type', () => {
    component.type = 'error';
    expect(component.getDefaultTitle()).toBe('Something went wrong');
  });

  it('should show default title for search type', () => {
    component.type = 'search';
    expect(component.getDefaultTitle()).toBe('No results found');
  });

  it('should show default title for filter type', () => {
    component.type = 'filter';
    expect(component.getDefaultTitle()).toBe('No matching items');
  });

  it('should show custom title when provided', fakeAsync(() => {
    component.title = 'Custom Title';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const titleElement = element.querySelector('.empty-state-title');
    expect(titleElement.textContent).toContain('Custom Title');
  }));

  it('should show description when provided', fakeAsync(() => {
    component.description = 'This is a description';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const descElement = element.querySelector('.empty-state-description');
    expect(descElement).toBeTruthy();
    expect(descElement.textContent).toContain('This is a description');
  }));

  it('should not show description when not provided', fakeAsync(() => {
    component.description = '';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const element = fixture.nativeElement;
    const descElement = element.querySelector('.empty-state-description');
    expect(descElement).toBeFalsy();
  }));

  it('should emit primaryActionClick', () => {
    const emitSpy = spyOn(component.primaryActionClick, 'emit');
    component.primaryActionClick.emit();
    expect(emitSpy).toHaveBeenCalled();
  });

  it('should emit secondaryActionClick', () => {
    const emitSpy = spyOn(component.secondaryActionClick, 'emit');
    component.secondaryActionClick.emit();
    expect(emitSpy).toHaveBeenCalled();
  });

  it('should emit retryClick', () => {
    const emitSpy = spyOn(component.retryClick, 'emit');
    component.retryClick.emit();
    expect(emitSpy).toHaveBeenCalled();
  });

  it('should accept custom icon', () => {
    component.icon = 'cloud_off';
    expect(component.icon).toBe('cloud_off');
  });

  it('should accept primary action text', () => {
    component.primaryAction = 'Create New';
    expect(component.primaryAction).toBe('Create New');
  });

  it('should accept secondary action text', () => {
    component.secondaryAction = 'Learn More';
    expect(component.secondaryAction).toBe('Learn More');
  });

  it('should accept showRetry flag', () => {
    component.showRetry = true;
    expect(component.showRetry).toBeTrue();
  });

  it('should apply container class', () => {
    component.containerClass = 'compact';
    expect(component.containerClass).toBe('compact');
  });

  it('should accept primary action icon', () => {
    component.primaryActionIcon = 'add';
    expect(component.primaryActionIcon).toBe('add');
  });

  it('should accept secondary action icon', () => {
    component.secondaryActionIcon = 'info';
    expect(component.secondaryActionIcon).toBe('info');
  });
});
