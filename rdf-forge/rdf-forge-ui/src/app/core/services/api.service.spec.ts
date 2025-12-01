import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiService } from './api.service';
import { environment } from '../../../environments/environment';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('get()', () => {
    it('should make a GET request to the correct URL', () => {
      const testData = { id: 1, name: 'Test' };
      
      service.get<typeof testData>('/test').subscribe(data => {
        expect(data).toEqual(testData);
      });

      const req = httpMock.expectOne(`${baseUrl}/test`);
      expect(req.request.method).toBe('GET');
      req.flush(testData);
    });

    it('should handle query parameters', () => {
      const testData = [{ id: 1 }, { id: 2 }];
      const params = { page: 1, limit: 10 };
      
      service.get<typeof testData>('/items', params).subscribe(data => {
        expect(data).toEqual(testData);
      });

      const req = httpMock.expectOne(`${baseUrl}/items?page=1&limit=10`);
      expect(req.request.method).toBe('GET');
      req.flush(testData);
    });

    it('should ignore null and undefined params', () => {
      const testData = { result: 'success' };
      const params = { active: true, deleted: null, archived: undefined };
      
      service.get<typeof testData>('/items', params).subscribe(data => {
        expect(data).toEqual(testData);
      });

      const req = httpMock.expectOne(`${baseUrl}/items?active=true`);
      expect(req.request.method).toBe('GET');
      req.flush(testData);
    });

    it('should handle empty params object', () => {
      const testData = { data: [] };
      
      service.get<typeof testData>('/empty', {}).subscribe(data => {
        expect(data).toEqual(testData);
      });

      const req = httpMock.expectOne(`${baseUrl}/empty`);
      expect(req.request.method).toBe('GET');
      req.flush(testData);
    });

    it('should handle no params', () => {
      const testData = { data: 'test' };
      
      service.get<typeof testData>('/no-params').subscribe(data => {
        expect(data).toEqual(testData);
      });

      const req = httpMock.expectOne(`${baseUrl}/no-params`);
      expect(req.request.method).toBe('GET');
      req.flush(testData);
    });
  });

  describe('post()', () => {
    it('should make a POST request with data', () => {
      const requestData = { name: 'New Item', value: 100 };
      const responseData = { id: 1, ...requestData };
      
      service.post<typeof responseData>('/items', requestData).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/items`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(requestData);
      req.flush(responseData);
    });

    it('should handle empty body', () => {
      const responseData = { success: true };
      
      service.post<typeof responseData>('/action', {}).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/action`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(responseData);
    });
  });

  describe('put()', () => {
    it('should make a PUT request with data', () => {
      const requestData = { id: 1, name: 'Updated Item' };
      const responseData = { ...requestData, updated: true };
      
      service.put<typeof responseData>('/items/1', requestData).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/items/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(requestData);
      req.flush(responseData);
    });
  });

  describe('delete()', () => {
    it('should make a DELETE request', () => {
      const responseData = { deleted: true };
      
      service.delete<typeof responseData>('/items/1').subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/items/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(responseData);
    });
  });

  describe('upload()', () => {
    it('should upload a file with FormData', () => {
      const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
      const responseData = { fileId: 'abc123', filename: 'test.txt' };
      
      service.upload<typeof responseData>('/upload', file).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/upload`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTrue();
      expect(req.request.body.get('file')).toBeTruthy();
      req.flush(responseData);
    });

    it('should include additional options in FormData', () => {
      const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
      const options = { description: 'Test file', category: 'documents' };
      const responseData = { fileId: 'abc123' };
      
      service.upload<typeof responseData>('/upload', file, options).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/upload`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTrue();
      expect(req.request.body.get('file')).toBeTruthy();
      expect(req.request.body.get('description')).toBe('Test file');
      expect(req.request.body.get('category')).toBe('documents');
      req.flush(responseData);
    });

    it('should handle numeric options', () => {
      const file = new File(['data'], 'data.json', { type: 'application/json' });
      const options = { version: 2, priority: 1 };
      const responseData = { uploaded: true };
      
      service.upload<typeof responseData>('/upload', file, options).subscribe(data => {
        expect(data).toEqual(responseData);
      });

      const req = httpMock.expectOne(`${baseUrl}/upload`);
      expect(req.request.body.get('version')).toBe('2');
      expect(req.request.body.get('priority')).toBe('1');
      req.flush(responseData);
    });
  });

  describe('error handling', () => {
    it('should propagate HTTP errors on GET', () => {
      let errorResponse: HttpErrorResponse | undefined;
      
      service.get('/error').subscribe({
        next: () => fail('should have failed'),
        error: (error: HttpErrorResponse) => {
          errorResponse = error;
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/error`);
      req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

      expect(errorResponse?.status).toBe(500);
    });

    it('should propagate HTTP errors on POST', () => {
      let errorResponse: HttpErrorResponse | undefined;
      
      service.post('/error', {}).subscribe({
        next: () => fail('should have failed'),
        error: (error: HttpErrorResponse) => {
          errorResponse = error;
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/error`);
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });

      expect(errorResponse?.status).toBe(400);
    });
  });
});