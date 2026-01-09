import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DataSourceInputComponent, DataSourceConfig, ExistingDataSource } from './data-source-input';
import { signal } from '@angular/core';

describe('DataSourceInputComponent', () => {
  let component: DataSourceInputComponent;
  let fixture: ComponentFixture<DataSourceInputComponent>;

  const mockExistingSources: ExistingDataSource[] = [
    { id: 'ds-1', name: 'Test CSV', format: 'csv', size: 1024 },
    { id: 'ds-2', name: 'Test JSON', format: 'json', size: 2048 }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataSourceInputComponent, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(DataSourceInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Tab changes', () => {
    it('should clear preview data on tab change', () => {
      component.previewData.set({
        columns: ['a'],
        rows: [{ a: 1 }],
        rowCount: 1,
        columnCount: 1
      });

      component.onTabChange({});

      expect(component.previewData()).toBeNull();
    });
  });

  describe('Existing data source selection', () => {
    beforeEach(() => {
      component.existingDataSources = signal(mockExistingSources);
    });

    it('should emit sourceSelected when existing source is selected', () => {
      spyOn(component.sourceSelected, 'emit');

      component.selectedExisting = 'ds-1';
      component.onExistingSelected();

      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'existing',
        value: 'ds-1',
        format: 'csv'
      });
    });

    it('should not emit if no source is selected', () => {
      spyOn(component.sourceSelected, 'emit');

      component.selectedExisting = '';
      component.onExistingSelected();

      expect(component.sourceSelected.emit).not.toHaveBeenCalled();
    });
  });

  describe('Drag and drop', () => {
    it('should set isDragOver on dragover', () => {
      const event = new DragEvent('dragover');
      spyOn(event, 'preventDefault');
      spyOn(event, 'stopPropagation');

      component.onDragOver(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(component.isDragOver()).toBeTrue();
    });

    it('should clear isDragOver on dragleave', () => {
      component.isDragOver.set(true);
      const event = new DragEvent('dragleave');
      spyOn(event, 'preventDefault');
      spyOn(event, 'stopPropagation');

      component.onDragLeave(event);

      expect(component.isDragOver()).toBeFalse();
    });

    it('should handle file drop', () => {
      spyOn(component.fileUploaded, 'emit');
      spyOn(component.sourceSelected, 'emit');

      const file = new File(['content'], 'test.csv', { type: 'text/csv' });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(file);

      const event = new DragEvent('drop', { dataTransfer });
      spyOn(event, 'preventDefault');
      spyOn(event, 'stopPropagation');

      component.onDrop(event);

      expect(component.isDragOver()).toBeFalse();
      expect(component.selectedFile()).toBe(file);
      expect(component.fileUploaded.emit).toHaveBeenCalledWith(file);
    });

    it('should not handle drop without files', () => {
      spyOn(component, 'handleFile');

      const event = new DragEvent('drop');
      spyOn(event, 'preventDefault');
      spyOn(event, 'stopPropagation');

      component.onDrop(event);

      expect(component.handleFile).not.toHaveBeenCalled();
    });
  });

  describe('File selection', () => {
    it('should handle file selection from input', () => {
      spyOn(component, 'handleFile');

      const file = new File(['content'], 'test.csv', { type: 'text/csv' });
      const event = { target: { files: [file] } } as unknown as Event;

      component.onFileSelected(event);

      expect(component.handleFile).toHaveBeenCalledWith(file);
    });

    it('should not handle selection without files', () => {
      spyOn(component, 'handleFile');

      const event = { target: { files: [] } } as unknown as Event;

      component.onFileSelected(event);

      expect(component.handleFile).not.toHaveBeenCalled();
    });
  });

  describe('handleFile', () => {
    it('should set selected file and emit events', () => {
      spyOn(component.fileUploaded, 'emit');
      spyOn(component.sourceSelected, 'emit');

      const file = new File(['content'], 'data.json', { type: 'application/json' });

      component.handleFile(file);

      expect(component.selectedFile()).toBe(file);
      expect(component.fileUploaded.emit).toHaveBeenCalledWith(file);
      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'file',
        value: 'data.json',
        format: 'json',
        options: undefined
      });
    });

    it('should include CSV options for CSV files', () => {
      spyOn(component.sourceSelected, 'emit');

      const file = new File(['content'], 'data.csv', { type: 'text/csv' });

      component.handleFile(file);

      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'file',
        value: 'data.csv',
        format: 'csv',
        options: component.csvOptions
      });
    });
  });

  describe('clearFile', () => {
    it('should clear selected file and preview data', () => {
      component.selectedFile.set(new File([''], 'test.csv'));
      component.previewData.set({
        columns: ['a'],
        rows: [],
        rowCount: 0,
        columnCount: 1
      });

      const event = { stopPropagation: jasmine.createSpy() } as unknown as Event;

      component.clearFile(event);

      expect(component.selectedFile()).toBeNull();
      expect(component.previewData()).toBeNull();
      expect(event.stopPropagation).toHaveBeenCalled();
    });
  });

  describe('URL handling', () => {
    it('should emit sourceSelected when URL changes', () => {
      spyOn(component.sourceSelected, 'emit');

      component.urlValue = 'https://example.com/data.csv';
      component.urlFormat = 'auto';
      component.onUrlChange();

      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'url',
        value: 'https://example.com/data.csv',
        format: 'csv',
        options: { headers: {} }
      });
    });

    it('should not emit when URL is empty', () => {
      spyOn(component.sourceSelected, 'emit');

      component.urlValue = '';
      component.onUrlChange();

      expect(component.sourceSelected.emit).not.toHaveBeenCalled();
    });

    it('should include headers in URL config', () => {
      spyOn(component.sourceSelected, 'emit');

      component.urlValue = 'https://example.com/data.json';
      component.urlHeaders = [
        { key: 'Authorization', value: 'Bearer token' }
      ];
      component.onUrlChange();

      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'url',
        value: 'https://example.com/data.json',
        format: 'json',
        options: { headers: { 'Authorization': 'Bearer token' } }
      });
    });
  });

  describe('SPARQL handling', () => {
    it('should emit sourceSelected when SPARQL changes', () => {
      spyOn(component.sourceSelected, 'emit');

      component.sparqlEndpoint = 'http://localhost:7200/repositories/test';
      component.sparqlQuery = 'SELECT * WHERE { ?s ?p ?o }';
      component.sparqlGraph = 'http://example.org/graph';
      component.onSparqlChange();

      expect(component.sourceSelected.emit).toHaveBeenCalledWith({
        type: 'sparql',
        value: 'SELECT * WHERE { ?s ?p ?o }',
        options: {
          endpoint: 'http://localhost:7200/repositories/test',
          query: 'SELECT * WHERE { ?s ?p ?o }',
          graphUri: 'http://example.org/graph'
        }
      });
    });

    it('should not emit when endpoint is empty', () => {
      spyOn(component.sourceSelected, 'emit');

      component.sparqlEndpoint = '';
      component.sparqlQuery = 'SELECT * WHERE { ?s ?p ?o }';
      component.onSparqlChange();

      expect(component.sourceSelected.emit).not.toHaveBeenCalled();
    });

    it('should not emit when query is empty', () => {
      spyOn(component.sourceSelected, 'emit');

      component.sparqlEndpoint = 'http://localhost:7200/repositories/test';
      component.sparqlQuery = '';
      component.onSparqlChange();

      expect(component.sourceSelected.emit).not.toHaveBeenCalled();
    });
  });

  describe('Header management', () => {
    it('should add header', () => {
      expect(component.urlHeaders.length).toBe(0);

      component.addHeader();

      expect(component.urlHeaders.length).toBe(1);
      expect(component.urlHeaders[0]).toEqual({ key: '', value: '' });
    });

    it('should remove header', () => {
      component.urlHeaders = [
        { key: 'A', value: '1' },
        { key: 'B', value: '2' }
      ];

      component.removeHeader(0);

      expect(component.urlHeaders.length).toBe(1);
      expect(component.urlHeaders[0].key).toBe('B');
    });
  });

  describe('Preview requests', () => {
    it('should emit previewRequested for URL', fakeAsync(() => {
      spyOn(component.previewRequested, 'emit');

      component.urlValue = 'https://example.com/data.csv';
      component.fetchUrlPreview();

      expect(component.isLoading()).toBeTrue();
      expect(component.previewRequested.emit).toHaveBeenCalled();

      tick(2000);

      expect(component.isLoading()).toBeFalse();
    }));

    it('should emit previewRequested for SPARQL', fakeAsync(() => {
      spyOn(component.previewRequested, 'emit');

      component.sparqlEndpoint = 'http://localhost:7200';
      component.sparqlQuery = 'SELECT * WHERE { ?s ?p ?o }';
      component.executeSparqlPreview();

      expect(component.isLoading()).toBeTrue();
      expect(component.previewRequested.emit).toHaveBeenCalled();

      tick(2000);

      expect(component.isLoading()).toBeFalse();
    }));
  });

  describe('Format detection', () => {
    it('should detect CSV format', () => {
      expect(component.detectFormat('data.csv')).toBe('csv');
    });

    it('should detect TSV format', () => {
      expect(component.detectFormat('data.tsv')).toBe('tsv');
    });

    it('should detect JSON format', () => {
      expect(component.detectFormat('data.json')).toBe('json');
    });

    it('should detect XML format', () => {
      expect(component.detectFormat('data.xml')).toBe('xml');
    });

    it('should detect Turtle format', () => {
      expect(component.detectFormat('data.ttl')).toBe('turtle');
    });

    it('should detect Parquet format', () => {
      expect(component.detectFormat('data.parquet')).toBe('parquet');
    });

    it('should return unknown for unrecognized format', () => {
      expect(component.detectFormat('data.xyz')).toBe('unknown');
    });

    it('should detect format from URL', () => {
      expect(component.detectFormatFromUrl('https://example.com/data.csv')).toBe('csv');
    });

    it('should return unknown for invalid URL', () => {
      expect(component.detectFormatFromUrl('not-a-url')).toBe('unknown');
    });
  });

  describe('formatFileSize', () => {
    it('should format bytes', () => {
      expect(component.formatFileSize(500)).toBe('500 B');
    });

    it('should format kilobytes', () => {
      expect(component.formatFileSize(1500)).toBe('1.5 KB');
    });

    it('should format megabytes', () => {
      expect(component.formatFileSize(1500000)).toBe('1.4 MB');
    });
  });

  describe('getFormatIcon', () => {
    it('should return table_chart for csv', () => {
      expect(component.getFormatIcon('csv')).toBe('table_chart');
    });

    it('should return data_object for json', () => {
      expect(component.getFormatIcon('json')).toBe('data_object');
    });

    it('should return code for xml', () => {
      expect(component.getFormatIcon('xml')).toBe('code');
    });

    it('should return hub for turtle', () => {
      expect(component.getFormatIcon('turtle')).toBe('hub');
    });

    it('should return description for unknown format', () => {
      expect(component.getFormatIcon('unknown')).toBe('description');
    });

    it('should handle undefined format', () => {
      expect(component.getFormatIcon(undefined as any)).toBe('description');
    });
  });
});
