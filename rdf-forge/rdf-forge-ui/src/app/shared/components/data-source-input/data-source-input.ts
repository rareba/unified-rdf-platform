import { Component, EventEmitter, Input, Output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';

export interface DataSourceConfig {
  type: 'file' | 'url' | 'sparql' | 'existing';
  value: string;
  format?: string;
  options?: DataSourceOptions;
}

export interface DataSourceOptions {
  // CSV options
  delimiter?: string;
  encoding?: string;
  hasHeader?: boolean;
  skipRows?: number;
  // URL options
  headers?: Record<string, string>;
  method?: 'GET' | 'POST';
  // SPARQL options
  endpoint?: string;
  query?: string;
  graphUri?: string;
}

export interface ExistingDataSource {
  id: string;
  name: string;
  format: string;
  size?: number;
  uploadedAt?: string;
}

@Component({
  selector: 'app-data-source-input',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTabsModule,
    MatProgressBarModule,
    MatChipsModule,
    MatTooltipModule,
    MatExpansionModule
  ],
  template: `
    <div class="data-source-input">
      <mat-tab-group [(selectedIndex)]="selectedTab" (selectedTabChange)="onTabChange($event)">
        <!-- Existing Data Sources Tab -->
        <mat-tab label="Existing">
          <ng-template matTabLabel>
            <mat-icon>folder</mat-icon>
            <span>Existing</span>
          </ng-template>
          <div class="tab-content">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Select Data Source</mat-label>
              <mat-select [(ngModel)]="selectedExisting" (selectionChange)="onExistingSelected()">
                @for (source of existingDataSources(); track source.id) {
                  <mat-option [value]="source.id">
                    <div class="source-option">
                      <mat-icon>{{ getFormatIcon(source.format) }}</mat-icon>
                      <span>{{ source.name }}</span>
                      <span class="format-badge">{{ source.format }}</span>
                    </div>
                  </mat-option>
                }
                @if (existingDataSources().length === 0) {
                  <mat-option disabled>No data sources uploaded yet</mat-option>
                }
              </mat-select>
              <mat-hint>Choose from previously uploaded data sources</mat-hint>
            </mat-form-field>
          </div>
        </mat-tab>

        <!-- File Upload Tab -->
        <mat-tab label="Upload">
          <ng-template matTabLabel>
            <mat-icon>upload_file</mat-icon>
            <span>Upload</span>
          </ng-template>
          <div class="tab-content">
            <div
              class="drop-zone"
              [class.drag-over]="isDragOver()"
              (dragover)="onDragOver($event)"
              (dragleave)="onDragLeave($event)"
              (drop)="onDrop($event)"
              (click)="fileInput.click()">
              <input
                #fileInput
                type="file"
                [accept]="acceptedFormats"
                (change)="onFileSelected($event)"
                hidden>

              @if (selectedFile()) {
                <div class="selected-file">
                  <mat-icon>description</mat-icon>
                  <div class="file-info">
                    <span class="file-name">{{ selectedFile()!.name }}</span>
                    <span class="file-size">{{ formatFileSize(selectedFile()!.size) }}</span>
                  </div>
                  <button mat-icon-button (click)="clearFile($event)" matTooltip="Remove file">
                    <mat-icon>close</mat-icon>
                  </button>
                </div>
              } @else {
                <mat-icon class="upload-icon">cloud_upload</mat-icon>
                <p>Drag & drop a file here or click to browse</p>
                <p class="hint">Supported: CSV, JSON, XML, Parquet, TSV</p>
              }
            </div>

            @if (selectedFile() && showOptions()) {
              <mat-expansion-panel class="options-panel">
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <mat-icon>settings</mat-icon>
                    Parsing Options
                  </mat-panel-title>
                </mat-expansion-panel-header>

                <div class="options-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Delimiter</mat-label>
                    <mat-select [(ngModel)]="csvOptions.delimiter">
                      <mat-option value=",">Comma (,)</mat-option>
                      <mat-option value=";">Semicolon (;)</mat-option>
                      <mat-option value="\t">Tab</mat-option>
                      <mat-option value="|">Pipe (|)</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Encoding</mat-label>
                    <mat-select [(ngModel)]="csvOptions.encoding">
                      <mat-option value="UTF-8">UTF-8</mat-option>
                      <mat-option value="ISO-8859-1">ISO-8859-1</mat-option>
                      <mat-option value="Windows-1252">Windows-1252</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Skip Rows</mat-label>
                    <input matInput type="number" [(ngModel)]="csvOptions.skipRows" min="0">
                  </mat-form-field>

                  <div class="checkbox-field">
                    <label>
                      <input type="checkbox" [(ngModel)]="csvOptions.hasHeader">
                      First row is header
                    </label>
                  </div>
                </div>
              </mat-expansion-panel>
            }
          </div>
        </mat-tab>

        <!-- URL Tab -->
        <mat-tab label="URL">
          <ng-template matTabLabel>
            <mat-icon>link</mat-icon>
            <span>URL</span>
          </ng-template>
          <div class="tab-content">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Data URL</mat-label>
              <input matInput
                     [(ngModel)]="urlValue"
                     placeholder="https://example.org/data.csv"
                     (ngModelChange)="onUrlChange()">
              <mat-icon matPrefix>http</mat-icon>
              <mat-hint>Enter HTTP/HTTPS URL to fetch data from</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Format (auto-detected)</mat-label>
              <mat-select [(ngModel)]="urlFormat">
                <mat-option value="auto">Auto-detect</mat-option>
                <mat-option value="csv">CSV</mat-option>
                <mat-option value="json">JSON</mat-option>
                <mat-option value="xml">XML</mat-option>
                <mat-option value="turtle">Turtle (RDF)</mat-option>
                <mat-option value="jsonld">JSON-LD</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-expansion-panel class="options-panel">
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <mat-icon>vpn_key</mat-icon>
                  Headers & Authentication
                </mat-panel-title>
              </mat-expansion-panel-header>

              <div class="headers-section">
                @for (header of urlHeaders; track header.key; let i = $index) {
                  <div class="header-row">
                    <mat-form-field appearance="outline">
                      <mat-label>Header Name</mat-label>
                      <input matInput [(ngModel)]="header.key" placeholder="Authorization">
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Value</mat-label>
                      <input matInput [(ngModel)]="header.value" placeholder="Bearer token...">
                    </mat-form-field>
                    <button mat-icon-button color="warn" (click)="removeHeader(i)">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                }
                <button mat-stroked-button (click)="addHeader()">
                  <mat-icon>add</mat-icon>
                  Add Header
                </button>
              </div>
            </mat-expansion-panel>

            @if (urlValue) {
              <button mat-raised-button color="primary" (click)="fetchUrlPreview()" [disabled]="isLoading()">
                @if (isLoading()) {
                  <mat-icon class="spin">refresh</mat-icon>
                  Fetching...
                } @else {
                  <mat-icon>visibility</mat-icon>
                  Preview Data
                }
              </button>
            }
          </div>
        </mat-tab>

        <!-- SPARQL Tab -->
        <mat-tab label="SPARQL">
          <ng-template matTabLabel>
            <mat-icon>query_stats</mat-icon>
            <span>SPARQL</span>
          </ng-template>
          <div class="tab-content">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>SPARQL Endpoint</mat-label>
              <input matInput
                     [(ngModel)]="sparqlEndpoint"
                     placeholder="http://localhost:7200/repositories/rdf-forge">
              <mat-icon matPrefix>dns</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Graph URI (optional)</mat-label>
              <input matInput [(ngModel)]="sparqlGraph" placeholder="https://example.org/graph/default">
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width sparql-query">
              <mat-label>SPARQL Query</mat-label>
              <textarea matInput
                        [(ngModel)]="sparqlQuery"
                        rows="8"
                        placeholder="SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100"
                        (ngModelChange)="onSparqlChange()"></textarea>
              <mat-hint>Enter a SELECT or CONSTRUCT query</mat-hint>
            </mat-form-field>

            @if (sparqlEndpoint && sparqlQuery) {
              <button mat-raised-button color="primary" (click)="executeSparqlPreview()" [disabled]="isLoading()">
                @if (isLoading()) {
                  <mat-icon class="spin">refresh</mat-icon>
                  Executing...
                } @else {
                  <mat-icon>play_arrow</mat-icon>
                  Test Query
                }
              </button>
            }
          </div>
        </mat-tab>
      </mat-tab-group>

      @if (isLoading()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      }

      @if (previewData()) {
        <div class="preview-section">
          <h4>Data Preview</h4>
          <div class="preview-stats">
            <mat-chip>{{ previewData()!.rowCount }} rows</mat-chip>
            <mat-chip>{{ previewData()!.columnCount }} columns</mat-chip>
            @if (previewData()!.format) {
              <mat-chip>{{ previewData()!.format }}</mat-chip>
            }
          </div>
          <div class="preview-table">
            <table>
              <thead>
                <tr>
                  @for (col of previewData()!.columns; track col) {
                    <th>{{ col }}</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (row of previewData()!.rows | slice:0:5; track $index) {
                  <tr>
                    @for (col of previewData()!.columns; track col) {
                      <td>{{ row[col] }}</td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .data-source-input {
      width: 100%;
    }

    .tab-content {
      padding: 16px 0;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .full-width {
      width: 100%;
    }

    .drop-zone {
      border: 2px dashed var(--mat-sys-outline);
      border-radius: 8px;
      padding: 32px;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s ease;
      background: var(--mat-sys-surface-container-low);
    }

    .drop-zone:hover, .drop-zone.drag-over {
      border-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }

    .upload-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mat-sys-primary);
    }

    .drop-zone p {
      margin: 8px 0;
      color: var(--mat-sys-on-surface-variant);
    }

    .drop-zone .hint {
      font-size: 12px;
      color: var(--mat-sys-outline);
    }

    .selected-file {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px;
      background: var(--mat-sys-surface-container);
      border-radius: 8px;
    }

    .selected-file mat-icon {
      color: var(--mat-sys-primary);
      font-size: 32px;
      width: 32px;
      height: 32px;
    }

    .file-info {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    .file-name {
      font-weight: 500;
    }

    .file-size {
      font-size: 12px;
      color: var(--mat-sys-outline);
    }

    .options-panel {
      margin-top: 8px;
    }

    .options-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;
      padding: 16px 0;
    }

    .checkbox-field {
      display: flex;
      align-items: center;
    }

    .checkbox-field label {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
    }

    .headers-section {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .header-row {
      display: flex;
      gap: 8px;
      align-items: flex-start;
    }

    .header-row mat-form-field {
      flex: 1;
    }

    .sparql-query textarea {
      font-family: 'Fira Code', 'Consolas', monospace;
      font-size: 13px;
    }

    .preview-section {
      margin-top: 16px;
      padding: 16px;
      background: var(--mat-sys-surface-container);
      border-radius: 8px;
    }

    .preview-section h4 {
      margin: 0 0 12px 0;
    }

    .preview-stats {
      display: flex;
      gap: 8px;
      margin-bottom: 12px;
    }

    .preview-table {
      overflow-x: auto;
    }

    .preview-table table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    .preview-table th, .preview-table td {
      padding: 8px 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      text-align: left;
    }

    .preview-table th {
      background: var(--mat-sys-surface-container-high);
      font-weight: 500;
    }

    .source-option {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .format-badge {
      font-size: 11px;
      padding: 2px 6px;
      background: var(--mat-sys-secondary-container);
      border-radius: 4px;
      margin-left: auto;
    }

    mat-tab-group ::ng-deep .mat-mdc-tab-labels {
      background: var(--mat-sys-surface-container-low);
    }

    mat-tab ::ng-deep .mat-mdc-tab-label-content {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class DataSourceInputComponent {
  @Input() acceptedFormats = '.csv,.json,.xml,.parquet,.tsv,.xlsx';
  @Input() showOptions = signal(true);
  @Input() existingDataSources = signal<ExistingDataSource[]>([]);

  @Output() sourceSelected = new EventEmitter<DataSourceConfig>();
  @Output() fileUploaded = new EventEmitter<File>();
  @Output() previewRequested = new EventEmitter<DataSourceConfig>();

  selectedTab = 0;
  selectedExisting = '';
  selectedFile = signal<File | null>(null);
  urlValue = '';
  urlFormat = 'auto';
  urlHeaders: { key: string; value: string }[] = [];
  sparqlEndpoint = '';
  sparqlGraph = '';
  sparqlQuery = '';

  isDragOver = signal(false);
  isLoading = signal(false);
  previewData = signal<{
    columns: string[];
    rows: Record<string, any>[];
    rowCount: number;
    columnCount: number;
    format?: string;
  } | null>(null);

  csvOptions: DataSourceOptions = {
    delimiter: ',',
    encoding: 'UTF-8',
    hasHeader: true,
    skipRows: 0
  };

  onTabChange(event: any) {
    this.previewData.set(null);
  }

  onExistingSelected() {
    if (this.selectedExisting) {
      const source = this.existingDataSources().find(s => s.id === this.selectedExisting);
      if (source) {
        this.sourceSelected.emit({
          type: 'existing',
          value: source.id,
          format: source.format
        });
      }
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFile(files[0]);
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
    }
  }

  handleFile(file: File) {
    this.selectedFile.set(file);
    this.fileUploaded.emit(file);

    const format = this.detectFormat(file.name);
    this.sourceSelected.emit({
      type: 'file',
      value: file.name,
      format,
      options: format === 'csv' ? this.csvOptions : undefined
    });
  }

  clearFile(event: Event) {
    event.stopPropagation();
    this.selectedFile.set(null);
    this.previewData.set(null);
  }

  onUrlChange() {
    if (this.urlValue) {
      const format = this.urlFormat === 'auto' ? this.detectFormatFromUrl(this.urlValue) : this.urlFormat;
      this.sourceSelected.emit({
        type: 'url',
        value: this.urlValue,
        format,
        options: {
          headers: this.urlHeaders.reduce((acc, h) => {
            if (h.key) acc[h.key] = h.value;
            return acc;
          }, {} as Record<string, string>)
        }
      });
    }
  }

  onSparqlChange() {
    if (this.sparqlEndpoint && this.sparqlQuery) {
      this.sourceSelected.emit({
        type: 'sparql',
        value: this.sparqlQuery,
        options: {
          endpoint: this.sparqlEndpoint,
          query: this.sparqlQuery,
          graphUri: this.sparqlGraph || undefined
        }
      });
    }
  }

  addHeader() {
    this.urlHeaders.push({ key: '', value: '' });
  }

  removeHeader(index: number) {
    this.urlHeaders.splice(index, 1);
  }

  async fetchUrlPreview() {
    this.isLoading.set(true);
    this.previewRequested.emit({
      type: 'url',
      value: this.urlValue,
      format: this.urlFormat,
      options: {
        headers: this.urlHeaders.reduce((acc, h) => {
          if (h.key) acc[h.key] = h.value;
          return acc;
        }, {} as Record<string, string>)
      }
    });
    // Preview data would be set by parent component
    setTimeout(() => this.isLoading.set(false), 2000);
  }

  async executeSparqlPreview() {
    this.isLoading.set(true);
    this.previewRequested.emit({
      type: 'sparql',
      value: this.sparqlQuery,
      options: {
        endpoint: this.sparqlEndpoint,
        query: this.sparqlQuery,
        graphUri: this.sparqlGraph || undefined
      }
    });
    setTimeout(() => this.isLoading.set(false), 2000);
  }

  detectFormat(filename: string): string {
    const ext = filename.split('.').pop()?.toLowerCase();
    const formatMap: Record<string, string> = {
      csv: 'csv',
      tsv: 'tsv',
      json: 'json',
      xml: 'xml',
      parquet: 'parquet',
      xlsx: 'xlsx',
      ttl: 'turtle',
      rdf: 'rdfxml',
      jsonld: 'jsonld',
      nt: 'ntriples'
    };
    return formatMap[ext || ''] || 'unknown';
  }

  detectFormatFromUrl(url: string): string {
    try {
      const pathname = new URL(url).pathname;
      return this.detectFormat(pathname);
    } catch {
      return 'unknown';
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getFormatIcon(format: string): string {
    const icons: Record<string, string> = {
      csv: 'table_chart',
      json: 'data_object',
      xml: 'code',
      parquet: 'storage',
      turtle: 'hub',
      rdfxml: 'hub',
      jsonld: 'data_object'
    };
    return icons[format?.toLowerCase()] || 'description';
  }
}
