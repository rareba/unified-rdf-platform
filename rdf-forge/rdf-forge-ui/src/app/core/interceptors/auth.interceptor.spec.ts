import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthService', ['getToken', 'login']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add Authorization header when token exists', () => {
    authService.getToken.and.returnValue('test-token');

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeTrue();
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  it('should not add Authorization header when token is undefined', () => {
    authService.getToken.and.returnValue(undefined);

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should call login when receiving 401 response', () => {
    authService.getToken.and.returnValue('expired-token');

    httpClient.get('/api/protected').subscribe({
      next: () => fail('should have failed'),
      error: () => {
        expect(authService.login).toHaveBeenCalled();
      }
    });

    const req = httpMock.expectOne('/api/protected');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
  });

  it('should not call login for non-401 errors', () => {
    authService.getToken.and.returnValue('test-token');

    httpClient.get('/api/test').subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(authService.login).not.toHaveBeenCalled();
        expect(error.status).toBe(500);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('should pass through other errors', () => {
    authService.getToken.and.returnValue('test-token');
    let errorReceived = false;

    httpClient.get('/api/test').subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        errorReceived = true;
        expect(error.status).toBe(404);
      }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Not Found', { status: 404, statusText: 'Not Found' });

    expect(errorReceived).toBeTrue();
  });

  it('should propagate error to subscriber after 401', () => {
    authService.getToken.and.returnValue('test-token');
    let errorReceived = false;

    httpClient.get('/api/protected').subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        errorReceived = true;
        expect(error.status).toBe(401);
      }
    });

    const req = httpMock.expectOne('/api/protected');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(errorReceived).toBeTrue();
    expect(authService.login).toHaveBeenCalled();
  });

  it('should work with POST requests', () => {
    authService.getToken.and.returnValue('test-token');
    const postData = { name: 'test' };

    httpClient.post('/api/items', postData).subscribe();

    const req = httpMock.expectOne('/api/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({ id: 1, ...postData });
  });

  it('should work with PUT requests', () => {
    authService.getToken.and.returnValue('test-token');
    const putData = { id: 1, name: 'updated' };

    httpClient.put('/api/items/1', putData).subscribe();

    const req = httpMock.expectOne('/api/items/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush(putData);
  });

  it('should work with DELETE requests', () => {
    authService.getToken.and.returnValue('test-token');

    httpClient.delete('/api/items/1').subscribe();

    const req = httpMock.expectOne('/api/items/1');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  describe('when auth is disabled', () => {
    const originalEnvAuth = { ...environment.auth };

    beforeAll(() => {
      environment.auth.enabled = false;
    });

    afterAll(() => {
      environment.auth = originalEnvAuth;
    });

    it('should not add Authorization header', () => {
      authService.getToken.and.returnValue('test-token');

      httpClient.get('/api/test').subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
    });

    it('should not call login on 401', () => {
      authService.getToken.and.returnValue('test-token');

      httpClient.get('/api/protected').subscribe({
        error: () => {
          // Expected error
        }
      });

      const req = httpMock.expectOne('/api/protected');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      expect(authService.login).not.toHaveBeenCalled();
    });
  });
});