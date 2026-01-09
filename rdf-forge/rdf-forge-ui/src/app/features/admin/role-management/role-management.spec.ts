import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RoleManagement } from './role-management';
import { environment } from '../../../../environments/environment';

describe('RoleManagement', () => {
  let component: RoleManagement;
  let fixture: ComponentFixture<RoleManagement>;
  let httpMock: HttpTestingController;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let originalAuthEnabled: boolean;

  beforeEach(async () => {
    originalAuthEnabled = environment.auth.enabled;
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [RoleManagement, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    environment.auth.enabled = originalAuthEnabled;
    httpMock.verify();
  });

  describe('in offline mode', () => {
    beforeEach(() => {
      environment.auth.enabled = false;
      fixture = TestBed.createComponent(RoleManagement);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should load demo roles in offline mode', () => {
      expect(component.roles().length).toBe(3);
      expect(component.roles()[0].name).toBe('admin');
    });

    it('should have admin role with all permissions', () => {
      const adminRole = component.roles().find(r => r.name === 'admin');
      expect(adminRole).toBeTruthy();
      expect(adminRole!.permissions).toContain('admin');
      expect(adminRole!.permissions).toContain('manage_users');
    });

    it('should have viewer role as default', () => {
      const viewerRole = component.roles().find(r => r.name === 'viewer');
      expect(viewerRole?.isDefault).toBeTrue();
    });

    it('should return empty keycloakAdminUrl when auth disabled', () => {
      expect(component.keycloakAdminUrl).toBe('');
    });
  });

  describe('in online mode', () => {
    const mockRoles = [
      { name: 'admin', description: 'Admin role', permissions: ['all'], userCount: 2, isDefault: false },
      { name: 'user', description: 'User role', permissions: ['read'], userCount: 10, isDefault: true }
    ];

    beforeEach(() => {
      environment.auth.enabled = true;
      fixture = TestBed.createComponent(RoleManagement);
      component = fixture.componentInstance;
    });

    it('should load roles from API', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/roles`);
      expect(req.request.method).toBe('GET');
      req.flush(mockRoles);

      tick();

      expect(component.roles().length).toBe(2);
      expect(component.loading()).toBeFalse();
    }));

    it('should handle API error', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/roles`);
      req.error(new ProgressEvent('error'));

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Failed to load roles from Keycloak',
        'Close',
        { duration: 3000 }
      );
      expect(component.loading()).toBeFalse();
    }));

    it('should refresh roles when loadRoles is called', fakeAsync(() => {
      fixture.detectChanges();

      // Initial load
      httpMock.expectOne(`${environment.apiBaseUrl}/admin/roles`).flush(mockRoles);
      tick();

      // Refresh
      component.loadRoles();
      expect(component.loading()).toBeTrue();

      httpMock.expectOne(`${environment.apiBaseUrl}/admin/roles`).flush(mockRoles);
      tick();

      expect(component.loading()).toBeFalse();
    }));
  });
});
