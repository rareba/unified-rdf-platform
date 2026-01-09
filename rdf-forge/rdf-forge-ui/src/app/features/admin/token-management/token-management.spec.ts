import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TokenManagement } from './token-management';
import { environment } from '../../../../environments/environment';

describe('TokenManagement', () => {
  let component: TokenManagement;
  let fixture: ComponentFixture<TokenManagement>;
  let httpMock: HttpTestingController;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const mockTokens = [
    {
      id: 'token-1',
      name: 'CI Token',
      description: 'CI/CD token',
      tokenPrefix: 'ccx_abc12',
      scopes: [],
      createdAt: '2024-01-01T00:00:00Z',
      expiresAt: '2025-01-01T00:00:00Z',
      revoked: false
    },
    {
      id: 'token-2',
      name: 'Dev Token',
      tokenPrefix: 'ccx_xyz98',
      scopes: [],
      createdAt: '2024-02-01T00:00:00Z',
      revoked: true
    }
  ];

  beforeEach(async () => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [TokenManagement, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TokenManagement);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush(mockTokens);
    expect(component).toBeTruthy();
  });

  it('should load tokens on init', fakeAsync(() => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTokens);

    tick();

    expect(component.tokens().length).toBe(2);
    expect(component.loading()).toBeFalse();
  }));

  it('should compute active tokens count', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush(mockTokens);
    tick();

    expect(component.activeTokensCount()).toBe(1); // Only one is not revoked
  }));

  it('should fall back to demo tokens on API error', fakeAsync(() => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`);
    req.error(new ProgressEvent('error'));

    tick();

    expect(component.tokens().length).toBe(2);
    expect(component.loading()).toBeFalse();
  }));

  it('should open new token dialog', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);

    component.openNewTokenDialog();

    expect(component.newTokenDialogVisible()).toBeTrue();
    expect(component.newToken.expiration).toBe('ONE_MONTH');
  });

  it('should not create token without name', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);

    component.newToken.name = '';
    component.createToken();

    httpMock.expectNone(`${environment.apiBaseUrl}/auth/tokens`);
  });

  describe('formatExpiration', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);
    });

    it('should return "Never" for undefined', () => {
      expect(component.formatExpiration(undefined)).toBe('Never');
    });

    it('should return "Expired" for past dates', () => {
      const pastDate = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
      expect(component.formatExpiration(pastDate)).toBe('Expired');
    });
  });

  describe('formatLastUsed', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);
    });

    it('should return "Never" for undefined', () => {
      expect(component.formatLastUsed(undefined)).toBe('Never');
    });

    it('should format recent usage as minutes ago', () => {
      const recentDate = new Date(Date.now() - 30 * 60 * 1000).toISOString();
      expect(component.formatLastUsed(recentDate)).toContain('m ago');
    });

    it('should format hours ago', () => {
      const hoursAgo = new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString();
      expect(component.formatLastUsed(hoursAgo)).toContain('h ago');
    });

    it('should format days ago', () => {
      const daysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString();
      expect(component.formatLastUsed(daysAgo)).toContain('d ago');
    });
  });

  it('should close token created dialog', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);

    component.createdToken.set('some-token');
    component.tokenCreatedDialogVisible.set(true);

    component.closeTokenCreatedDialog();

    expect(component.createdToken()).toBe('');
    expect(component.tokenCreatedDialogVisible()).toBeFalse();
  });

  it('should have displayedColumns defined', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/tokens`).flush([]);

    expect(component.displayedColumns).toContain('name');
    expect(component.displayedColumns).toContain('token');
    expect(component.displayedColumns).toContain('status');
    expect(component.displayedColumns).toContain('actions');
  });
});
