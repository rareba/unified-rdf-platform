import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { DataService } from './data.service';
import { SettingsService } from './settings.service';
import { environment } from '../../../environments/environment';
import { DataSource, DataPreview, ColumnInfo } from '../models';

describe('DataService', () => {
  let service: DataService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;
  let settingsServiceMock: jasmine.SpyObj<SettingsService>;

  const mockDataSource: DataSource = {
    id: 'ds-1',
    name: 'test.csv',
    originalFilename: 'test.csv',
    format: 'csv',
    sizeBytes: 1024,
    rowCount: 100,
    columnCount: 5,
    storagePath: '/data/test.csv',
    uploadedAt: new Date(),
    uploadedBy: 'user'
  };

  beforeEach(() => {
    settingsServiceMock = jasmine.createSpyObj('SettingsService', [], {
      pageSize: signal(20),
      sparqlResultLimit: signal(1000),
      autoRetryFailed: signal(false),
      retryAttempts: signal(3)
    });

    TestBed.configureTestingModule({
      providers: [
        DataService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SettingsService, useValue: settingsServiceMock }
      ]
    });
    service = TestBed.inject(DataService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('should return a list of data sources', () => {
      service.list().subscribe(sources => {
        expect(sources.length).toBe(1);
        expect(sources[0].id).toBe('ds-1');
      });

      const req = httpMock.expectOne(r => r.url === `${baseUrl}/data` && r.params.has('size'));
      expect(req.request.method).toBe('GET');
      req.flush([mockDataSource]);
    });

    it('should handle list params', () => {
      service.list({ search: 'test', format: 'CSV' }).subscribe();

      const req = httpMock.expectOne(r =>
        r.url === `${baseUrl}/data` &&
        r.params.get('search') === 'test' &&
        r.params.get('format') === 'CSV'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('get()', () => {
    it('should return a single data source by id', () => {
      service.get('ds-1').subscribe(source => {
        expect(source.id).toBe('ds-1');
        expect(source.name).toBe('test.csv');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/ds-1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDataSource);
    });
  });

  describe('upload()', () => {
    it('should upload a file', () => {
      const file = new File(['test'], 'test.csv', { type: 'text/csv' });

      service.upload(file).subscribe(source => {
        expect(source.name).toBe('test.csv');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/upload`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTrue();
      req.flush(mockDataSource);
    });

    it('should upload a file with options', () => {
      const file = new File(['test'], 'test.csv', { type: 'text/csv' });
      const options = { delimiter: ',', hasHeader: true };

      service.upload(file, options).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/data/upload`);
      expect(req.request.body.get('delimiter')).toBe(',');
      expect(req.request.body.get('hasHeader')).toBe('true');
      req.flush(mockDataSource);
    });
  });

  describe('delete()', () => {
    it('should delete a data source', () => {
      service.delete('ds-1').subscribe();

      const req = httpMock.expectOne(`${baseUrl}/data/ds-1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('preview()', () => {
    it('should return data preview', () => {
      const preview: DataPreview = {
        columns: ['col1', 'col2'],
        data: [{ col1: 'a', col2: 'b' }, { col1: 'c', col2: 'd' }],
        schema: [],
        totalRows: 2
      };

      service.preview('ds-1').subscribe(p => {
        expect(p.columns.length).toBe(2);
        expect(p.data.length).toBe(2);
      });

      const req = httpMock.expectOne(`${baseUrl}/data/ds-1/preview`);
      expect(req.request.method).toBe('GET');
      req.flush(preview);
    });

    it('should handle preview params', () => {
      service.preview('ds-1', { rows: 10, offset: 5 }).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/data/ds-1/preview?rows=10&offset=5`);
      expect(req.request.method).toBe('GET');
      req.flush({ columns: [], data: [], schema: [], totalRows: 0 });
    });
  });

  describe('analyze()', () => {
    it('should analyze data source', () => {
      const result = {
        columns: [
          { name: 'id', type: 'integer', nullable: false } as ColumnInfo
        ],
        rowCount: 100
      };

      service.analyze('ds-1').subscribe(r => {
        expect(r.columns.length).toBe(1);
        expect(r.rowCount).toBe(100);
      });

      const req = httpMock.expectOne(`${baseUrl}/data/ds-1/analyze`);
      expect(req.request.method).toBe('POST');
      req.flush(result);
    });
  });

  describe('detectFormat()', () => {
    it('should detect file format', () => {
      const file = new File(['id,name\n1,test'], 'test.csv', { type: 'text/csv' });
      const result = { format: 'CSV', delimiter: ',', hasHeader: true };

      service.detectFormat(file).subscribe(r => {
        expect(r.format).toBe('CSV');
      });

      const req = httpMock.expectOne(`${baseUrl}/data/detect-format`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTrue();
      req.flush(result);
    });
  });
});
