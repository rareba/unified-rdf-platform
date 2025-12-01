<template>
  <div class="data-manager">
    <div class="header">
      <h1>Data Sources</h1>
      <div class="actions">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="searchQuery" placeholder="Search..." />
        </span>
        <Dropdown v-model="formatFilter" :options="formatOptions" optionLabel="label" optionValue="value" placeholder="Format" />
        <Button label="Upload" icon="pi pi-upload" @click="showUploadDialog = true" />
      </div>
    </div>

    <div class="data-grid">
      <template v-if="loading">
        <div v-for="i in 6" :key="i" class="data-card">
          <Skeleton size="3rem" class="mr-3"></Skeleton>
          <div class="flex-1">
            <Skeleton width="60%" class="mb-2"></Skeleton>
            <Skeleton width="40%"></Skeleton>
          </div>
        </div>
      </template>
      <template v-else>
        <div
          v-for="source in filteredSources"
          :key="source.id"
          class="data-card"
          @click="selectSource(source)"
        >
          <div class="card-icon">
            <i :class="getFormatIcon(source.format)" />
          </div>
          <div class="card-content">
            <div class="card-header">
              <span class="card-name">{{ source.name }}</span>
              <Tag :value="source.format.toUpperCase()" :severity="getFormatSeverity(source.format)" />
            </div>
            <div class="card-meta">
              <span><i class="pi pi-file" /> {{ formatSize(source.sizeBytes) }}</span>
              <span><i class="pi pi-table" /> {{ formatNumber(source.rowCount) }} rows</span>
              <span><i class="pi pi-clock" /> {{ formatDate(source.uploadedAt) }}</span>
            </div>
          </div>
          <div class="card-actions">
            <Button icon="pi pi-eye" class="p-button-rounded p-button-text" @click.stop="previewSource(source)" v-tooltip="'Preview'" />
            <Button icon="pi pi-download" class="p-button-rounded p-button-text" @click.stop="downloadSource(source)" v-tooltip="'Download'" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text" @click.stop="confirmDelete(source)" v-tooltip="'Delete'" />
          </div>
        </div>
      </template>
    </div>

    <div v-if="!loading && filteredSources.length === 0" class="empty-state">
      <i class="pi pi-database" style="font-size: 4rem; color: var(--text-color-secondary)" />
      <h3>No data sources yet</h3>
      <p>Upload your first data file to get started</p>
      <Button label="Upload Data" icon="pi pi-upload" @click="showUploadDialog = true" />
    </div>

    <!-- Upload Dialog -->
    <Dialog v-model:visible="showUploadDialog" header="Upload Data Source" :style="{ width: '600px' }" :modal="true">
      <div class="upload-content">
        <FileUpload
          mode="advanced"
          :multiple="true"
          accept=".csv,.json,.xlsx,.parquet,.xml,.tsv"
          :maxFileSize="500000000"
          @upload="onUpload"
          @select="onSelect"
          :auto="false"
          chooseLabel="Select Files"
          uploadLabel="Upload"
          cancelLabel="Clear"
        >
          <template #empty>
            <div class="upload-dropzone">
              <i class="pi pi-cloud-upload" style="font-size: 3rem" />
              <p>Drag and drop files here</p>
              <span class="upload-hint">CSV, JSON, XLSX, Parquet, XML (max 500MB each)</span>
            </div>
          </template>
        </FileUpload>

        <div class="upload-options" v-if="pendingFiles.length > 0">
          <h4>Upload Options</h4>
          <div class="field">
            <label for="encoding">File Encoding</label>
            <Dropdown id="encoding" v-model="uploadOptions.encoding" :options="encodingOptions" class="w-full" />
          </div>
          <div class="field-checkbox">
            <Checkbox id="analyze" v-model="uploadOptions.analyze" :binary="true" />
            <label for="analyze">Analyze file structure</label>
          </div>
        </div>
      </div>
    </Dialog>

    <!-- Preview Dialog -->
    <Dialog v-model:visible="showPreviewDialog" :header="`Preview: ${selectedSource?.name}`" :style="{ width: '90vw' }" :modal="true">
      <div class="preview-content" v-if="selectedSource">
        <div class="preview-info">
          <span><strong>Format:</strong> {{ selectedSource.format.toUpperCase() }}</span>
          <span><strong>Size:</strong> {{ formatSize(selectedSource.sizeBytes) }}</span>
          <span><strong>Rows:</strong> {{ formatNumber(selectedSource.rowCount) }}</span>
          <span><strong>Columns:</strong> {{ selectedSource.columnCount }}</span>
        </div>

        <TabView>
          <TabPanel header="Data Preview">
            <DataTable
              :value="previewData"
              :rows="10"
              :paginator="true"
              class="p-datatable-sm"
              responsiveLayout="scroll"
            >
              <Column v-for="col in previewColumns" :key="col.field" :field="col.field" :header="col.header">
                <template #body="{ data }">
                  <span :class="{ 'null-value': data[col.field] === null }">
                    {{ data[col.field] ?? 'null' }}
                  </span>
                </template>
              </Column>
            </DataTable>
          </TabPanel>

          <TabPanel header="Schema">
            <DataTable :value="schemaInfo" class="p-datatable-sm">
              <Column field="name" header="Column" style="width: 200px" />
              <Column field="type" header="Detected Type" style="width: 150px">
                <template #body="{ data }">
                  <Tag :value="data.type" />
                </template>
              </Column>
              <Column field="nullCount" header="Nulls" style="width: 100px">
                <template #body="{ data }">
                  {{ data.nullCount }} ({{ data.nullPercent }}%)
                </template>
              </Column>
              <Column field="uniqueCount" header="Unique Values" style="width: 120px" />
              <Column field="sample" header="Sample Values">
                <template #body="{ data }">
                  <div class="sample-values">
                    <code v-for="(val, i) in data.sample.slice(0, 3)" :key="i">{{ val }}</code>
                  </div>
                </template>
              </Column>
            </DataTable>
          </TabPanel>

          <TabPanel header="Statistics">
            <div class="stats-grid">
              <div v-for="col in numericColumns" :key="col.name" class="stat-card">
                <h4>{{ col.name }}</h4>
                <div class="stat-row"><span>Min:</span><span>{{ col.min }}</span></div>
                <div class="stat-row"><span>Max:</span><span>{{ col.max }}</span></div>
                <div class="stat-row"><span>Mean:</span><span>{{ col.mean?.toFixed(2) }}</span></div>
                <div class="stat-row"><span>Median:</span><span>{{ col.median }}</span></div>
                <div class="stat-row"><span>Std Dev:</span><span>{{ col.stdDev?.toFixed(2) }}</span></div>
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>
    </Dialog>

    <!-- Delete Confirmation -->
    <Dialog v-model:visible="showDeleteDialog" header="Confirm Delete" :style="{ width: '400px' }" :modal="true">
      <div class="delete-content">
        <i class="pi pi-exclamation-triangle" style="font-size: 2rem; color: var(--yellow-500)" />
        <p>Are you sure you want to delete <strong>{{ selectedSource?.name }}</strong>?</p>
        <p class="warning-text">This action cannot be undone.</p>
      </div>
      <template #footer>
        <Button label="Cancel" class="p-button-text" @click="showDeleteDialog = false" />
        <Button label="Delete" class="p-button-danger" @click="deleteSource" :loading="deleting" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import Checkbox from 'primevue/checkbox'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Skeleton from 'primevue/skeleton'
import { useToast } from 'primevue/usetoast'
import { useDataStore } from '@/stores/data'

interface DataSource {
  id: string
  name: string
  originalFilename: string
  format: 'csv' | 'json' | 'xlsx' | 'parquet' | 'xml'
  sizeBytes: number
  rowCount: number
  columnCount: number
  uploadedAt: Date
  uploadedBy: string
}

interface SchemaColumn {
  name: string
  type: string
  nullCount: number
  nullPercent: number
  uniqueCount: number
  sample: string[]
}

const dataStore = useDataStore()
const toast = useToast()

const searchQuery = ref('')
const formatFilter = ref<string | null>(null)
const dataSources = ref<DataSource[]>([])
const loading = ref(true)

const showUploadDialog = ref(false)
const showPreviewDialog = ref(false)
const showDeleteDialog = ref(false)
const selectedSource = ref<DataSource | null>(null)
const deleting = ref(false)

const pendingFiles = ref<File[]>([])
const uploadOptions = ref({
  encoding: 'UTF-8',
  analyze: true
})

const previewData = ref<Record<string, any>[]>([])
const previewColumns = ref<{ field: string; header: string }[]>([])
const schemaInfo = ref<SchemaColumn[]>([])
const numericColumns = ref<any[]>([])

const formatOptions = [
  { label: 'All Formats', value: null },
  { label: 'CSV', value: 'csv' },
  { label: 'JSON', value: 'json' },
  { label: 'Excel', value: 'xlsx' },
  { label: 'Parquet', value: 'parquet' },
  { label: 'XML', value: 'xml' }
]

const encodingOptions = ['UTF-8', 'UTF-16', 'ISO-8859-1', 'Windows-1252']

const filteredSources = computed(() => {
  let result = dataSources.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(s => s.name.toLowerCase().includes(query))
  }
  if (formatFilter.value) {
    result = result.filter(s => s.format === formatFilter.value)
  }
  return result
})

onMounted(async () => {
  await loadDataSources()
})

async function loadDataSources() {
  loading.value = true
  try {
    // Simulate delay if needed or just fetch
    if (dataSources.value.length === 0) {
        await new Promise(resolve => setTimeout(resolve, 800))
    }
    dataSources.value = await dataStore.fetchDataSources()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load data sources', life: 3000 })
  } finally {
    loading.value = false
  }
}

function selectSource(source: DataSource) {
  selectedSource.value = source
}

async function previewSource(source: DataSource) {
  selectedSource.value = source
  try {
    const preview = await dataStore.previewDataSource(source.id)
    previewData.value = preview.data
    previewColumns.value = preview.columns.map((c: string) => ({ field: c, header: c }))
    schemaInfo.value = preview.schema
    numericColumns.value = preview.schema.filter((s: SchemaColumn) => ['integer', 'decimal', 'float'].includes(s.type))
    showPreviewDialog.value = true
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load preview', life: 3000 })
  }
}

function downloadSource(source: DataSource) {
  try {
    dataStore.downloadDataSource(source.id)
    toast.add({ severity: 'success', summary: 'Download', detail: 'Download started', life: 3000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to download file', life: 3000 })
  }
}

function confirmDelete(source: DataSource) {
  selectedSource.value = source
  showDeleteDialog.value = true
}

async function deleteSource() {
  if (!selectedSource.value) return
  deleting.value = true
  try {
    await dataStore.deleteDataSource(selectedSource.value.id)
    toast.add({ severity: 'success', summary: 'Deleted', detail: `Data source "${selectedSource.value.name}" deleted`, life: 3000 })
    await loadDataSources()
    showDeleteDialog.value = false
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete data source', life: 3000 })
  } finally {
    deleting.value = false
  }
}

function onSelect(event: { files: File[] }) {
  pendingFiles.value = event.files
}

async function onUpload(event: { files: File[] }) {
  try {
    for (const file of event.files) {
      await dataStore.uploadDataSource(file, uploadOptions.value)
      toast.add({ severity: 'success', summary: 'Uploaded', detail: `${file.name} uploaded successfully`, life: 3000 })
    }
    await loadDataSources()
    showUploadDialog.value = false
    pendingFiles.value = []
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to upload files', life: 3000 })
  }
}

function getFormatIcon(format: string): string {
  switch (format) {
    case 'csv': return 'pi pi-file'
    case 'json': return 'pi pi-code'
    case 'xlsx': return 'pi pi-file-excel'
    case 'parquet': return 'pi pi-database'
    case 'xml': return 'pi pi-file'
    default: return 'pi pi-file'
  }
}

function getFormatSeverity(format: string): string {
  switch (format) {
    case 'csv': return 'success'
    case 'json': return 'warning'
    case 'xlsx': return 'info'
    case 'parquet': return 'secondary'
    case 'xml': return 'primary'
    default: return 'info'
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function formatNumber(num: number): string {
  return num.toLocaleString()
}

function formatDate(date: Date): string {
  return new Date(date).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}
</script>

<style scoped>
.data-manager {
  padding: 1.5rem;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.header h1 {
  margin: 0;
}

.actions {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.data-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 1rem;
}

.data-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--surface-card);
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.data-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: var(--surface-100);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: var(--primary-color);
}

.card-content {
  flex: 1;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.card-name {
  font-weight: 600;
}

.card-meta {
  display: flex;
  gap: 1rem;
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.card-meta i {
  margin-right: 0.25rem;
}

.card-actions {
  display: flex;
  gap: 0.25rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  text-align: center;
}

.empty-state h3 {
  margin: 1rem 0 0.5rem;
}

.empty-state p {
  color: var(--text-color-secondary);
  margin-bottom: 1.5rem;
}

.upload-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  border: 2px dashed var(--surface-border);
  border-radius: 8px;
  color: var(--primary-color);
}

.upload-hint {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin-top: 0.5rem;
}

.upload-options {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--surface-border);
}

.upload-options h4 {
  margin-bottom: 1rem;
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.field-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.preview-info {
  display: flex;
  gap: 2rem;
  margin-bottom: 1rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.null-value {
  color: var(--text-color-secondary);
  font-style: italic;
}

.sample-values {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.sample-values code {
  background: var(--surface-200);
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.75rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-card {
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.stat-card h4 {
  margin: 0 0 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--surface-border);
}

.stat-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
}

.stat-row span:first-child {
  color: var(--text-color-secondary);
}

.delete-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.5rem;
}

.warning-text {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}
</style>
