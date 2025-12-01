import { get, post, del, upload } from './client'

export interface DataSource {
  id: string
  name: string
  originalFilename: string
  format: 'csv' | 'json' | 'xlsx' | 'parquet' | 'xml' | 'tsv'
  sizeBytes: number
  rowCount: number
  columnCount: number
  storagePath: string
  metadata?: {
    columns?: ColumnInfo[]
    encoding?: string
    delimiter?: string
  }
  uploadedBy: string
  uploadedAt: Date
}

export interface ColumnInfo {
  name: string
  type: 'string' | 'integer' | 'decimal' | 'date' | 'datetime' | 'boolean'
  nullable: boolean
  nullCount: number
  uniqueCount: number
  sampleValues: string[]
}

export interface DataPreview {
  columns: string[]
  data: Record<string, any>[]
  schema: ColumnInfo[]
  totalRows: number
}

export interface UploadOptions {
  encoding?: string
  delimiter?: string
  hasHeader?: boolean
  analyze?: boolean
}

export async function fetchDataSources(params?: { search?: string; format?: string }): Promise<DataSource[]> {
  return get<DataSource[]>('/data', params)
}

export async function fetchDataSource(id: string): Promise<DataSource> {
  return get<DataSource>(`/data/${id}`)
}

export async function uploadDataSource(file: File, options?: UploadOptions): Promise<DataSource> {
  return upload<DataSource>('/data/upload', file, options)
}

export async function deleteDataSource(id: string): Promise<void> {
  return del(`/data/${id}`)
}

export async function previewDataSource(id: string, params?: { rows?: number; offset?: number }): Promise<DataPreview> {
  return get<DataPreview>(`/data/${id}/preview`, params)
}

export async function downloadDataSource(id: string): Promise<void> {
  const response = await fetch(`/api/v1/data/${id}/download`, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem('access_token')}`
    }
  })
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (response.headers.get('Content-Disposition')?.match(/filename="(.+)"/) || [])[1] || 'download'
  a.click()
  URL.revokeObjectURL(url)
}

export async function analyzeDataSource(id: string): Promise<{ columns: ColumnInfo[]; rowCount: number }> {
  return post(`/data/${id}/analyze`)
}

export async function detectFormat(file: File): Promise<{ format: string; encoding: string; delimiter?: string }> {
  const formData = new FormData()
  formData.append('file', file.slice(0, 10000))
  const response = await fetch('/api/v1/data/detect-format', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('access_token')}`
    },
    body: formData
  })
  return response.json()
}
