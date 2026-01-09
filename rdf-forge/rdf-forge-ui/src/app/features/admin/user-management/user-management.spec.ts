import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserManagement } from './user-management';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

describe('UserManagement', () => {
  let component: UserManagement;
  let fixture: ComponentFixture<UserManagement>;
  let httpMock: HttpTestingController;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let authService: jasmine.SpyObj<AuthService>;
  let originalAuthEnabled: boolean;

  const mockUsers = [
    {
      id: '1',
      username: 'admin',
      email: 'admin@example.org',
      firstName: 'Admin',
      lastName: 'User',
      enabled: true,
      roles: ['admin'],
      createdAt: '2024-01-01T00:00:00Z',
      lastLogin: '2024-01-10T00:00:00Z'
    },
    {
      id: '2',
      username: 'user',
      email: 'user@example.org',
      firstName: 'Normal',
      lastName: 'User',
      enabled: false,
      roles: ['viewer'],
      createdAt: '2024-01-01T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    originalAuthEnabled = environment.auth.enabled;
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    authService = jasmine.createSpyObj('AuthService', ['hasRole']);

    await TestBed.configureTestingModule({
      imports: [UserManagement, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
        { provide: AuthService, useValue: authService }
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
      fixture = TestBed.createComponent(UserManagement);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should load demo users in offline mode', () => {
      expect(component.users().length).toBe(3);
      expect(component.users()[0].username).toBe('admin');
    });

    it('should compute active users count', () => {
      expect(component.activeUsersCount()).toBe(3);
    });

    it('should return all users when searchQuery is empty', () => {
      component.searchQuery = '';
      const filtered = component.filteredUsers();

      expect(filtered.length).toBe(3);
    });

    it('should have displayedColumns defined', () => {
      expect(component.displayedColumns).toContain('username');
      expect(component.displayedColumns).toContain('email');
      expect(component.displayedColumns).toContain('roles');
    });

    it('should return empty keycloakAdminUrl when auth disabled', () => {
      expect(component.keycloakAdminUrl).toBe('');
    });
  });

  describe('in online mode', () => {
    beforeEach(() => {
      environment.auth.enabled = true;
      fixture = TestBed.createComponent(UserManagement);
      component = fixture.componentInstance;
    });

    it('should load users from API', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/users`);
      expect(req.request.method).toBe('GET');
      req.flush(mockUsers);

      tick();

      expect(component.users().length).toBe(2);
      expect(component.loading()).toBeFalse();
    }));

    it('should handle API error', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/users`);
      req.error(new ProgressEvent('error'));

      tick();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Failed to load users from Keycloak',
        'Close',
        { duration: 3000 }
      );
      expect(component.loading()).toBeFalse();
    }));

    it('should compute active users count correctly', fakeAsync(() => {
      fixture.detectChanges();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/admin/users`);
      req.flush(mockUsers);

      tick();

      expect(component.activeUsersCount()).toBe(1);
    }));

    it('should refresh users when loadUsers is called', fakeAsync(() => {
      fixture.detectChanges();

      // Initial load
      httpMock.expectOne(`${environment.apiBaseUrl}/admin/users`).flush(mockUsers);
      tick();

      // Refresh
      component.loadUsers();
      expect(component.loading()).toBeTrue();

      httpMock.expectOne(`${environment.apiBaseUrl}/admin/users`).flush(mockUsers);
      tick();

      expect(component.loading()).toBeFalse();
    }));
  });
});
