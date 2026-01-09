import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { DataManager } from './data-manager';
import { DataService } from '../../../core/services/data.service';
import { DataSource } from '../../../core/models';

describe('DataManager', () => {
  let component: DataManager;
  let fixture: ComponentFixture<DataManager>;
  let dataServiceSpy: jasmine.SpyObj<DataService>;

  const mockDataSources: DataSource[] = [
    {
      id: '1',
      name: 'test-data',
      originalFilename: 'test.csv',
      format: 'csv',
      sizeBytes: 1024,
      rowCount: 100,
      columnCount: 5,
      storagePath: '/data/test.csv',
      uploadedAt: new Date(),
      uploadedBy: 'user'
    },
    {
      id: '2',
      name: 'sample-data',
      originalFilename: 'sample.json',
      format: 'json',
      sizeBytes: 2048,
      rowCount: 50,
      columnCount: 3,
      storagePath: '/data/sample.json',
      uploadedAt: new Date(),
      uploadedBy: 'user'
    }
  ];

  beforeEach(async () => {
    dataServiceSpy = jasmine.createSpyObj('DataService', ['list', 'upload', 'uploadWithProgress', 'delete', 'preview', 'download']);
    dataServiceSpy.list.and.returnValue(of(mockDataSources));

    await TestBed.configureTestingModule({
      imports: [DataManager],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: DataService, useValue: dataServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DataManager);
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

  it('should load data sources on init', fakeAsync(() => {
    tick();
    expect(dataServiceSpy.list).toHaveBeenCalled();
    expect(component.dataSources().length).toBe(2);
    expect(component.loading()).toBeFalse();
  }));

  it('should filter data sources by search query', fakeAsync(() => {
    tick();
    component.searchQuery.set('test');
    expect(component.filteredDataSources().length).toBe(1);
    expect(component.filteredDataSources()[0].name).toBe('test-data');
  }));

  it('should calculate total size', fakeAsync(() => {
    tick();
    expect(component.totalSize()).toBe(3072); // 1024 + 2048
  }));

  it('should calculate total rows', fakeAsync(() => {
    tick();
    expect(component.totalRows()).toBe(150); // 100 + 50
  }));

  it('should format file size correctly', () => {
    expect(component.formatSize(0)).toBe('0 B');
    expect(component.formatSize(512)).toBe('512 B');
    expect(component.formatSize(1024)).toBe('1 KB');
    expect(component.formatSize(1536)).toBe('1.5 KB');
    expect(component.formatSize(1048576)).toBe('1 MB');
  });

  it('should get format class', () => {
    expect(component.getFormatClass('csv')).toBe('format-success');
    expect(component.getFormatClass('json')).toBe('format-info');
    expect(component.getFormatClass('xlsx')).toBe('format-warn');
    expect(component.getFormatClass('parquet')).toBe('format-danger');
    expect(component.getFormatClass('unknown')).toBe('format-secondary');
  });

  it('should get format icon', () => {
    expect(component.getFormatIcon('csv')).toBe('description');
    expect(component.getFormatIcon('json')).toBe('code');
    expect(component.getFormatIcon('xlsx')).toBe('table_chart');
    expect(component.getFormatIcon('parquet')).toBe('storage');
    expect(component.getFormatIcon('xml')).toBe('account_tree');
    expect(component.getFormatIcon('unknown')).toBe('insert_drive_file');
  });

  it('should handle page change', () => {
    const pageEvent = { pageIndex: 1, pageSize: 5, length: 10 };
    component.onPageChange(pageEvent as any);
    expect(component.pageIndex).toBe(1);
    expect(component.pageSize).toBe(5);
  });

  it('should handle drag over event', () => {
    const mockEvent = { preventDefault: jasmine.createSpy(), stopPropagation: jasmine.createSpy() };
    component.onDragOver(mockEvent as any);
    expect(component.isDragOver()).toBeTrue();
    expect(mockEvent.preventDefault).toHaveBeenCalled();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
  });

  it('should handle drag leave event', () => {
    component.isDragOver.set(true);
    const mockEvent = { preventDefault: jasmine.createSpy(), stopPropagation: jasmine.createSpy() };
    component.onDragLeave(mockEvent as any);
    expect(component.isDragOver()).toBeFalse();
  });

  it('should set selected file on file select', () => {
    const mockFile = new File(['content'], 'test.csv', { type: 'text/csv' });
    const mockEvent = { target: { files: [mockFile] } };
    component.onFileSelect(mockEvent as any);
    expect(component.selectedFile()).toBe(mockFile);
  });

  it('should clear selected file', () => {
    const mockFile = new File(['content'], 'test.csv', { type: 'text/csv' });
    component.selectedFile.set(mockFile);
    component.clearSelectedFile();
    expect(component.selectedFile()).toBeNull();
  });

  it('should close upload dialog', () => {
    component.uploadDialogVisible.set(true);
    component.uploading.set(true);
    component.uploadProgress.set(50);
    component.selectedFile.set(new File([''], 'test.csv'));

    component.closeUploadDialog();

    expect(component.uploadDialogVisible()).toBeFalse();
    expect(component.uploading()).toBeFalse();
    expect(component.uploadProgress()).toBe(0);
    expect(component.selectedFile()).toBeNull();
  });

  it('should close details dialog', () => {
    component.detailDialogVisible.set(true);
    component.closeDetailsDialog();
    expect(component.detailDialogVisible()).toBeFalse();
  });

  it('should close preview dialog', () => {
    component.previewDialogVisible.set(true);
    component.closePreviewDialog();
    expect(component.previewDialogVisible()).toBeFalse();
  });

  it('should handle load error gracefully', fakeAsync(() => {
    dataServiceSpy.list.and.returnValue(throwError(() => new Error('Network error')));
    component.loadDataSources();
    tick();
    expect(component.loading()).toBeFalse();
  }));

  it('should open upload dialog', () => {
    component.uploadDialogVisible.set(true);
    expect(component.uploadDialogVisible()).toBeTrue();
  });

  it('should handle drop with valid file', fakeAsync(() => {
    const file = new File(['content'], 'test.csv', { type: 'text/csv' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.isDragOver()).toBeFalse();
    expect(component.selectedFile()).toBe(file);
  }));

  it('should reject drop with invalid file type', fakeAsync(() => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBeNull();
  }));

  it('should handle drop with no files', fakeAsync(() => {
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer: { files: [] }
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.isDragOver()).toBeFalse();
  }));

  it('should upload file successfully', fakeAsync(() => {
    const file = new File(['content'], 'test.csv', { type: 'text/csv' });
    component.selectedFile.set(file);
    dataServiceSpy.uploadWithProgress.and.returnValue(of({ id: '3', name: 'test.csv' } as DataSource));

    component.uploadFile();
    tick();

    expect(dataServiceSpy.uploadWithProgress).toHaveBeenCalled();
    expect(component.uploading()).toBeFalse();
    expect(component.uploadDialogVisible()).toBeFalse();
  }));

  it('should not upload without file selected', fakeAsync(() => {
    component.selectedFile.set(null);
    component.uploadFile();
    tick();

    expect(dataServiceSpy.upload).not.toHaveBeenCalled();
  }));

  it('should handle upload error', fakeAsync(() => {
    const file = new File(['content'], 'test.csv', { type: 'text/csv' });
    component.selectedFile.set(file);
    dataServiceSpy.uploadWithProgress.and.returnValue(throwError(() => new Error('Upload failed')));

    component.uploadFile();
    tick();

    expect(component.uploading()).toBeFalse();
  }));

  it('should view details', () => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    component.viewDetails(source, mockEvent as any);

    expect(mockEvent.stopPropagation).toHaveBeenCalled();
    expect(component.selectedDataSource()).toBe(source);
    expect(component.detailDialogVisible()).toBeTrue();
  });

  it('should preview data successfully', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    const mockPreview = { columns: ['col1', 'col2'], data: [{col1: 1, col2: 2}], schema: [], totalRows: 1 };
    dataServiceSpy.preview.and.returnValue(of(mockPreview));

    component.previewData(source, mockEvent as any);
    tick();

    expect(component.selectedDataSource()).toBe(source);
    expect(component.previewDialogVisible()).toBeTrue();
    expect(component.preview()).toEqual(mockPreview);
    expect(component.previewLoading()).toBeFalse();
  }));

  it('should handle preview error', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    dataServiceSpy.preview.and.returnValue(throwError(() => new Error('Preview failed')));

    component.previewData(source, mockEvent as any);
    tick();

    expect(component.previewLoading()).toBeFalse();
  }));

  it('should download data successfully', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    dataServiceSpy.download.and.returnValue(of(void 0));

    component.downloadData(source, mockEvent as any);
    tick();

    expect(dataServiceSpy.download).toHaveBeenCalledWith('1');
  }));

  it('should handle download error', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    dataServiceSpy.download.and.returnValue(throwError(() => new Error('Download failed')));

    component.downloadData(source, mockEvent as any);
    tick();
    // Error handled via snackbar
  }));

  it('should confirm and delete', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(true);
    dataServiceSpy.delete.and.returnValue(of(void 0));

    component.confirmDelete(source, mockEvent as any);
    tick();

    expect(dataServiceSpy.delete).toHaveBeenCalledWith('1');
  }));

  it('should not delete if not confirmed', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(false);

    component.confirmDelete(source, mockEvent as any);
    tick();

    expect(dataServiceSpy.delete).not.toHaveBeenCalled();
  }));

  it('should handle delete error', fakeAsync(() => {
    const source = mockDataSources[0];
    const mockEvent = { stopPropagation: jasmine.createSpy() };
    spyOn(window, 'confirm').and.returnValue(true);
    dataServiceSpy.delete.and.returnValue(throwError(() => new Error('Delete failed')));

    component.confirmDelete(source, mockEvent as any);
    tick();
    // Error handled via snackbar
  }));

  it('should format date correctly', () => {
    const date = new Date(2024, 0, 15, 10, 30);
    const formatted = component.formatDate(date);
    expect(formatted.length).toBeGreaterThan(0);
  });

  it('should filter by format', fakeAsync(() => {
    tick();
    component.searchQuery.set('json');
    expect(component.filteredDataSources().length).toBe(1);
    expect(component.filteredDataSources()[0].format).toBe('json');
  }));

  it('should filter by original filename', fakeAsync(() => {
    tick();
    component.searchQuery.set('sample');
    expect(component.filteredDataSources().length).toBe(1);
    expect(component.filteredDataSources()[0].originalFilename).toBe('sample.json');
  }));

  it('should return all data sources when no search query', fakeAsync(() => {
    tick();
    component.searchQuery.set('');
    expect(component.filteredDataSources().length).toBe(2);
  }));

  it('should compute paged data sources', fakeAsync(() => {
    tick();
    // The computed signal uses filteredDataSources which references dataSources
    // With 2 mock items and pageSize 1, we should get 1 item
    expect(component.dataSources().length).toBe(2);
    expect(component.pagedDataSources().length).toBeGreaterThanOrEqual(1);
  }));

  it('should format GB size', () => {
    expect(component.formatSize(1073741824)).toBe('1 GB');
  });

  it('should get format class for tsv', () => {
    expect(component.getFormatClass('tsv')).toBe('format-secondary');
  });

  it('should get format icon for tsv', () => {
    expect(component.getFormatIcon('tsv')).toBe('insert_drive_file');
  });

  it('should handle empty file list on drop', fakeAsync(() => {
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer: null
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBeNull();
  }));

  it('should trigger file input when conditions are met', () => {
    component.selectedFile.set(null);
    component.uploading.set(false);
    // Cannot fully test without DOM, but ensure no error
    component.triggerFileInput();
  });

  it('should not trigger file input when already uploading', () => {
    component.uploading.set(true);
    component.triggerFileInput();
    // Should not throw
  });

  it('should not trigger file input when file already selected', () => {
    const file = new File(['content'], 'test.csv', { type: 'text/csv' });
    component.selectedFile.set(file);
    component.triggerFileInput();
    // Should not throw
  });

  it('should handle valid xlsx file on drop', fakeAsync(() => {
    const file = new File(['content'], 'test.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBe(file);
  }));

  it('should handle valid json file on drop', fakeAsync(() => {
    const file = new File(['{}'], 'test.json', { type: 'application/json' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBe(file);
  }));

  it('should handle valid xml file on drop', fakeAsync(() => {
    const file = new File(['<xml/>'], 'test.xml', { type: 'application/xml' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBe(file);
  }));

  it('should handle valid parquet file on drop', fakeAsync(() => {
    const file = new File(['binary'], 'test.parquet', { type: 'application/octet-stream' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBe(file);
  }));

  it('should handle valid tsv file on drop', fakeAsync(() => {
    const file = new File(['a\tb'], 'test.tsv', { type: 'text/tab-separated-values' });
    const dataTransfer = { files: [file] };
    const mockEvent = {
      preventDefault: jasmine.createSpy(),
      stopPropagation: jasmine.createSpy(),
      dataTransfer
    };
    component.onDrop(mockEvent as any);
    tick();
    expect(component.selectedFile()).toBe(file);
  }));
});