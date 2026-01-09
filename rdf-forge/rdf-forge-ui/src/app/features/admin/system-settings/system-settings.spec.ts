import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SystemSettings } from './system-settings';
import { environment } from '../../../../environments/environment';

describe('SystemSettings', () => {
  let component: SystemSettings;
  let fixture: ComponentFixture<SystemSettings>;
  let httpMock: HttpTestingController;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [SystemSettings, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SystemSettings);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests() {
    // System info request
    const infoReq = httpMock.expectOne(`${environment.apiBaseUrl}/system/info`);
    infoReq.flush({ version: '1.0.0', buildTime: '2024-01-01', environment: 'dev' });

    // Health check requests - one for each service
    const services = ['health', 'pipelines', 'data', 'shapes', 'jobs', 'dimensions', 'triplestores'];
    services.forEach(service => {
      const req = httpMock.expectOne(`${environment.apiBaseUrl}/${service}`);
      req.flush({});
    });
  }

  it('should create', fakeAsync(() => {
    fixture.detectChanges();
    flushInitialRequests();
    tick();
    expect(component).toBeTruthy();
  }));

  it('should load system info on init', fakeAsync(() => {
    fixture.detectChanges();
    flushInitialRequests();
    tick();

    expect(component.systemInfo()?.version).toBe('1.0.0');
  }));

  it('should use default system info on error', fakeAsync(() => {
    fixture.detectChanges();

    // System info error
    const infoReq = httpMock.expectOne(`${environment.apiBaseUrl}/system/info`);
    infoReq.error(new ProgressEvent('error'));

    // Health check requests
    const services = ['health', 'pipelines', 'data', 'shapes', 'jobs', 'dimensions', 'triplestores'];
    services.forEach(service => {
      const req = httpMock.expectOne(`${environment.apiBaseUrl}/${service}`);
      req.flush({});
    });

    tick();

    expect(component.systemInfo()?.version).toBe('1.0.0');
  }));

  it('should check health of all services', fakeAsync(() => {
    fixture.detectChanges();
    flushInitialRequests();
    tick();

    expect(component.services().length).toBe(7);
    expect(component.services().every(s => s.status === 'UP')).toBeTrue();
    expect(component.checking()).toBeFalse();
  }));

  it('should mark services as DOWN on error', fakeAsync(() => {
    fixture.detectChanges();

    // System info
    httpMock.expectOne(`${environment.apiBaseUrl}/system/info`).flush({});

    // Health check requests - some fail
    httpMock.expectOne(`${environment.apiBaseUrl}/health`).flush({});
    httpMock.expectOne(`${environment.apiBaseUrl}/pipelines`).error(new ProgressEvent('error'));
    httpMock.expectOne(`${environment.apiBaseUrl}/data`).flush({});
    httpMock.expectOne(`${environment.apiBaseUrl}/shapes`).error(new ProgressEvent('error'));
    httpMock.expectOne(`${environment.apiBaseUrl}/jobs`).flush({});
    httpMock.expectOne(`${environment.apiBaseUrl}/dimensions`).flush({});
    httpMock.expectOne(`${environment.apiBaseUrl}/triplestores`).flush({});

    tick();

    const downServices = component.services().filter(s => s.status === 'DOWN');
    expect(downServices.length).toBe(2);
  }));

  describe('getStatusIcon', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should return check_circle for UP', () => {
      expect(component.getStatusIcon('UP')).toBe('check_circle');
    });

    it('should return error for DOWN', () => {
      expect(component.getStatusIcon('DOWN')).toBe('error');
    });

    it('should return help for unknown', () => {
      expect(component.getStatusIcon('UNKNOWN')).toBe('help');
    });
  });

  describe('getChipColor', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should return accent for UP', () => {
      expect(component.getChipColor('UP')).toBe('accent');
    });

    it('should return warn for DOWN', () => {
      expect(component.getChipColor('DOWN')).toBe('warn');
    });

    it('should return primary for unknown', () => {
      expect(component.getChipColor('UNKNOWN')).toBe('primary');
    });
  });

  describe('clearCache', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should clear cache and show snackbar', () => {
      component.clearCache();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Cache cleared',
        'Close',
        { duration: 3000 }
      );
    });
  });

  describe('downloadLogs', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should download logs on success', fakeAsync(() => {
      const mockBlob = new Blob(['log content'], { type: 'text/plain' });
      spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
      spyOn(URL, 'revokeObjectURL');

      const mockAnchor = document.createElement('a');
      spyOn(document, 'createElement').and.returnValue(mockAnchor);
      spyOn(mockAnchor, 'click');

      component.downloadLogs();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/logs`);
      req.flush(mockBlob);

      tick();

      expect(mockAnchor.click).toHaveBeenCalled();
    }));

    it('should show error when download fails', fakeAsync(() => {
      component.downloadLogs();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/logs`);
      req.error(new ProgressEvent('error'));

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Log download not available in this environment',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('restartServices', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should not restart if not confirmed', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      component.restartServices();

      httpMock.expectNone(`${environment.apiBaseUrl}/admin/restart`);
    });

    it('should restart services when confirmed', fakeAsync(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.restartServices();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/restart`);
      req.flush({});

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Restart initiated',
        'Close',
        { duration: 3000 }
      );

      // Wait for health check timeout
      tick(5000);

      // Health checks after restart
      const services = ['health', 'pipelines', 'data', 'shapes', 'jobs', 'dimensions', 'triplestores'];
      services.forEach(service => {
        const healthReq = httpMock.expectOne(`${environment.apiBaseUrl}/${service}`);
        healthReq.flush({});
      });

      tick();
    }));

    it('should show error when restart fails', fakeAsync(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.restartServices();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/restart`);
      req.error(new ProgressEvent('error'));

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Restart not available in this environment',
        'Close',
        { duration: 3000 }
      );
    }));
  });

  describe('clearLocalStorage', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should not clear if not confirmed', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      spyOn(localStorage, 'clear');

      component.clearLocalStorage();

      expect(localStorage.clear).not.toHaveBeenCalled();
    });

    it('should clear local storage when confirmed', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(localStorage, 'clear');

      component.clearLocalStorage();

      expect(localStorage.clear).toHaveBeenCalled();
      expect(snackBar.open).toHaveBeenCalledWith(
        'Local storage cleared',
        'Close',
        { duration: 3000 }
      );
    });
  });

  describe('getStorageUsage', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should return storage usage string', () => {
      // Just verify the method runs and returns a valid format
      const result = component.getStorageUsage();
      expect(result).toBeDefined();
      expect(result).toMatch(/^\d+(\.\d+)?\s*(Bytes|KB|MB)$/);
    });
  });

  describe('checkHealth', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      flushInitialRequests();
      tick();
    }));

    it('should refresh health status', fakeAsync(() => {
      component.checkHealth();
      expect(component.checking()).toBeTrue();

      const services = ['health', 'pipelines', 'data', 'shapes', 'jobs', 'dimensions', 'triplestores'];
      services.forEach(service => {
        const req = httpMock.expectOne(`${environment.apiBaseUrl}/${service}`);
        req.flush({});
      });

      tick();

      expect(component.checking()).toBeFalse();
      expect(component.services().length).toBe(7);
    }));
  });
});
