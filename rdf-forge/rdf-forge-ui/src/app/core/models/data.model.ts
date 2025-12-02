export type DataFormat = 'csv' | 'json' | 'xlsx' | 'parquet' | 'xml' | 'tsv';
export type ColumnType = 'string' | 'integer' | 'decimal' | 'date' | 'datetime' | 'boolean';

export interface DataSource {
  id: string;
  name: string;
  originalFilename: string;
  format: DataFormat;
  sizeBytes: number;
  rowCount: number;
  columnCount: number;
  storagePath: string;
  metadata?: {
    columns?: ColumnInfo[];
    encoding?: string;
    delimiter?: string;
  };
  uploadedBy: string;
  uploadedAt: Date;
}

export interface ColumnInfo {
  name: string;
  type: ColumnType;
  nullable: boolean;
  nullCount: number;
  uniqueCount: number;
  sampleValues: string[];
}

export interface DataPreview {
  columns: string[];
  data: Record<string, unknown>[];
  schema: ColumnInfo[];
  totalRows: number;
}

export interface UploadOptions {
  encoding?: string;
  delimiter?: string;
  hasHeader?: boolean;
  analyze?: boolean;
}

export interface FormatDetectionResult {
  format: string;
  encoding: string;
  delimiter?: string;
}
