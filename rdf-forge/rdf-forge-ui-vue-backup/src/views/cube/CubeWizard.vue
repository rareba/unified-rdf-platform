<template>
  <div class="cube-wizard">
    <LoadingOverlay :loading="loading" message="Processing..." />
    <div class="wizard-header">
      <h1>Create RDF Cube</h1>
      <Steps :model="steps" :activeIndex="activeStep" />
    </div>

    <div class="wizard-content">
      <!-- Step 1: Upload Data -->
      <div v-if="activeStep === 0" class="step-content">
        <h2>Upload Data Source</h2>
        <p class="step-description">Upload your tabular data file (CSV, XLSX, TSV) to begin creating an RDF cube.</p>

        <FileUpload
          mode="advanced"
          :multiple="false"
          accept=".csv,.xlsx,.tsv"
          :maxFileSize="100000000"
          @upload="onFileUpload"
          @select="onFileSelect"
          :auto="false"
          chooseLabel="Select File"
          uploadLabel="Upload"
          cancelLabel="Clear"
        >
          <template #empty>
            <div class="upload-area">
              <i class="pi pi-cloud-upload" style="font-size: 3rem; color: var(--primary-color)" />
              <p>Drag and drop your file here</p>
              <span class="text-muted">Supported formats: CSV, XLSX, TSV (max 100MB)</span>
            </div>
          </template>
        </FileUpload>

        <div v-if="uploadedFile" class="file-preview">
          <h3>Preview</h3>
          <DataTable :value="previewData" :rows="5" class="p-datatable-sm">
            <Column v-for="col in previewColumns" :key="col.field" :field="col.field" :header="col.header" />
          </DataTable>
          <p class="preview-info">Showing first {{ previewData.length }} of {{ totalRows }} rows</p>
        </div>
      </div>

      <!-- Step 2: Column Mapping -->
      <div v-if="activeStep === 1" class="step-content">
        <h2>Map Columns</h2>
        <p class="step-description">Specify the role of each column in your RDF cube.</p>

        <DataTable :value="columnMappings" class="mapping-table">
          <Column field="name" header="Column" style="width: 200px">
            <template #body="{ data }">
              <div class="column-info">
                <span class="column-name">{{ data.name }}</span>
                <span class="column-type">{{ data.detectedType }}</span>
              </div>
            </template>
          </Column>

          <Column field="role" header="Role" style="width: 180px">
            <template #body="{ data }">
              <Dropdown
                v-model="data.role"
                :options="roleOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select role"
                class="w-full"
              />
            </template>
          </Column>

          <Column field="datatype" header="Datatype" style="width: 150px">
            <template #body="{ data }">
              <Dropdown
                v-model="data.datatype"
                :options="datatypeOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Datatype"
                class="w-full"
              />
            </template>
          </Column>

          <Column field="dimension" header="Dimension Mapping" style="min-width: 200px">
            <template #body="{ data }">
              <div v-if="data.role === 'dimension'" class="dimension-mapping">
                <Dropdown
                  v-model="data.dimensionUri"
                  :options="availableDimensions"
                  optionLabel="name"
                  optionValue="uri"
                  placeholder="Link to dimension"
                  :showClear="true"
                  class="w-full"
                />
                <Button
                  icon="pi pi-plus"
                  class="p-button-text"
                  @click="createNewDimension(data)"
                  v-tooltip="'Create new dimension'"
                />
              </div>
            </template>
          </Column>

          <Column field="isDimension" header="Include" style="width: 80px">
            <template #body="{ data }">
              <Checkbox v-model="data.include" :binary="true" />
            </template>
          </Column>
        </DataTable>
      </div>

      <!-- Step 3: Cube Metadata -->
      <div v-if="activeStep === 2" class="step-content">
        <h2>Cube Metadata</h2>
        <p class="step-description">Provide metadata for your RDF cube.</p>

        <div class="metadata-form">
          <div class="field">
            <label for="cubeUri">Cube URI *</label>
            <InputText id="cubeUri" v-model="cubeMetadata.uri" class="w-full" />
            <small>Unique identifier for your cube</small>
          </div>

          <div class="field">
            <label for="cubeTitle">Title *</label>
            <InputText id="cubeTitle" v-model="cubeMetadata.title" class="w-full" />
          </div>

          <div class="field">
            <label for="cubeDescription">Description</label>
            <Textarea id="cubeDescription" v-model="cubeMetadata.description" :rows="3" class="w-full" />
          </div>

          <div class="field-row">
            <div class="field">
              <label for="publisher">Publisher</label>
              <InputText id="publisher" v-model="cubeMetadata.publisher" class="w-full" />
            </div>
            <div class="field">
              <label for="contact">Contact Email</label>
              <InputText id="contact" v-model="cubeMetadata.contact" type="email" class="w-full" />
            </div>
          </div>

          <div class="field">
            <label for="license">License</label>
            <Dropdown
              id="license"
              v-model="cubeMetadata.license"
              :options="licenseOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Select license"
              class="w-full"
            />
          </div>

          <div class="field-row">
            <div class="field">
              <label for="temporalStart">Temporal Coverage Start</label>
              <Calendar id="temporalStart" v-model="cubeMetadata.temporalStart" dateFormat="yy-mm-dd" />
            </div>
            <div class="field">
              <label for="temporalEnd">Temporal Coverage End</label>
              <Calendar id="temporalEnd" v-model="cubeMetadata.temporalEnd" dateFormat="yy-mm-dd" />
            </div>
          </div>

          <div class="field">
            <label for="keywords">Keywords</label>
            <Chips id="keywords" v-model="cubeMetadata.keywords" separator="," />
          </div>
        </div>
      </div>

      <!-- Step 4: Preview & Validate -->
      <div v-if="activeStep === 3" class="step-content">
        <h2>Preview & Validate</h2>
        <p class="step-description">Review the generated RDF observations and validate the cube.</p>

        <TabView>
          <TabPanel header="Sample Observations">
            <div class="observation-preview">
              <pre>{{ sampleObservations }}</pre>
            </div>
          </TabPanel>

          <TabPanel header="Generated Shape">
            <div class="shape-preview">
              <pre>{{ generatedShape }}</pre>
            </div>
          </TabPanel>

          <TabPanel header="Validation">
            <div class="validation-results">
              <div v-for="result in validationResults" :key="result.name" class="validation-item">
                <i :class="result.valid ? 'pi pi-check-circle text-green-500' : 'pi pi-times-circle text-red-500'" />
                <span>{{ result.name }}</span>
                <span v-if="result.message" class="validation-message">{{ result.message }}</span>
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>

      <!-- Step 5: Publish -->
      <div v-if="activeStep === 4" class="step-content">
        <h2>Publish Cube</h2>
        <p class="step-description">Choose where to publish your RDF cube.</p>

        <div class="publish-options">
          <div class="summary-card">
            <h3>Summary</h3>
            <div class="summary-item"><span>Cube:</span> {{ cubeMetadata.title }}</div>
            <div class="summary-item"><span>Observations:</span> {{ totalRows }}</div>
            <div class="summary-item"><span>Dimensions:</span> {{ dimensionCount }}</div>
            <div class="summary-item"><span>Measures:</span> {{ measureCount }}</div>
          </div>

          <div class="field">
            <label>Destination Triplestore</label>
            <div class="triplestore-options">
              <div
                v-for="store in triplestores"
                :key="store.id"
                :class="['triplestore-option', { selected: selectedTriplestore === store.id }]"
                @click="selectedTriplestore = store.id"
              >
                <i class="pi pi-database" />
                <div class="store-info">
                  <span class="store-name">{{ store.name }}</span>
                  <span class="store-type">{{ store.type }}</span>
                </div>
                <i v-if="selectedTriplestore === store.id" class="pi pi-check" />
              </div>
            </div>
          </div>

          <div class="field">
            <label for="graphUri">Graph URI</label>
            <InputText id="graphUri" v-model="publishOptions.graphUri" class="w-full" />
          </div>

          <div class="publish-checkboxes">
            <div class="field-checkbox">
              <Checkbox id="savePipeline" v-model="publishOptions.savePipeline" :binary="true" />
              <label for="savePipeline">Save pipeline for future updates</label>
            </div>
            <div class="field-checkbox">
              <Checkbox id="saveShape" v-model="publishOptions.saveShape" :binary="true" />
              <label for="saveShape">Save SHACL shape to library</label>
            </div>
            <div class="field-checkbox">
              <Checkbox id="scheduleRefresh" v-model="publishOptions.scheduleRefresh" :binary="true" />
              <label for="scheduleRefresh">Schedule periodic refresh</label>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="wizard-footer">
      <Button v-if="activeStep > 0" label="Back" icon="pi pi-arrow-left" class="p-button-text" @click="prevStep" />
      <div class="spacer" />
      <Button v-if="activeStep < 4" label="Next" icon="pi pi-arrow-right" iconPos="right" @click="nextStep" :disabled="!canProceed" />
      <Button v-else label="Publish" icon="pi pi-cloud-upload" @click="publish" :loading="publishing" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Steps from 'primevue/steps'
import FileUpload from 'primevue/fileupload'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dropdown from 'primevue/dropdown'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Calendar from 'primevue/calendar'
import Chips from 'primevue/chips'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const toast = useToast()

const activeStep = ref(0)
const uploadedFile = ref<File | null>(null)
const publishing = ref(false)
const loading = ref(false)

const steps = ref([
  { label: 'Upload' },
  { label: 'Mapping' },
  { label: 'Metadata' },
  { label: 'Validate' },
  { label: 'Publish' }
])

const previewData = ref<Record<string, any>[]>([])
const previewColumns = ref<{ field: string; header: string }[]>([])
const totalRows = ref(0)

const columnMappings = ref<any[]>([])

const roleOptions = [
  { label: 'Dimension', value: 'dimension' },
  { label: 'Measure', value: 'measure' },
  { label: 'Attribute', value: 'attribute' },
  { label: 'Ignore', value: 'ignore' }
]

const datatypeOptions = [
  { label: 'String', value: 'xsd:string' },
  { label: 'Integer', value: 'xsd:integer' },
  { label: 'Decimal', value: 'xsd:decimal' },
  { label: 'Date', value: 'xsd:date' },
  { label: 'DateTime', value: 'xsd:dateTime' },
  { label: 'Boolean', value: 'xsd:boolean' }
]

const availableDimensions = ref([
  { uri: 'http://example.org/dimension/time', name: 'Time Dimension' },
  { uri: 'http://example.org/dimension/geo', name: 'Geographic Dimension' }
])

const cubeMetadata = ref({
  uri: '',
  title: '',
  description: '',
  publisher: '',
  contact: '',
  license: '',
  temporalStart: null,
  temporalEnd: null,
  keywords: [] as string[]
})

const licenseOptions = [
  { label: 'CC BY 4.0', value: 'https://creativecommons.org/licenses/by/4.0/' },
  { label: 'CC BY-SA 4.0', value: 'https://creativecommons.org/licenses/by-sa/4.0/' },
  { label: 'CC0 1.0', value: 'https://creativecommons.org/publicdomain/zero/1.0/' },
  { label: 'MIT License', value: 'https://opensource.org/licenses/MIT' }
]

const sampleObservations = ref('')
const generatedShape = ref('')
const validationResults = ref([
  { name: 'Cube Schema', valid: true, message: '' },
  { name: 'SHACL Shape', valid: true, message: '' },
  { name: 'Dimension Values', valid: true, message: '' }
])

const triplestores = ref([
  { id: '1', name: 'Apache Fuseki (local)', type: 'fuseki' },
  { id: '2', name: 'Stardog Cloud', type: 'stardog' },
  { id: '3', name: 'GraphDB (production)', type: 'graphdb' }
])

const selectedTriplestore = ref('1')

const publishOptions = ref({
  graphUri: '',
  savePipeline: true,
  saveShape: true,
  scheduleRefresh: false
})

const dimensionCount = computed(() => columnMappings.value.filter(c => c.role === 'dimension' && c.include).length)
const measureCount = computed(() => columnMappings.value.filter(c => c.role === 'measure' && c.include).length)

const canProceed = computed(() => {
  switch (activeStep.value) {
    case 0: return uploadedFile.value !== null
    case 1: return dimensionCount.value > 0 && measureCount.value > 0
    case 2: return cubeMetadata.value.uri && cubeMetadata.value.title
    case 3: return validationResults.value.every(r => r.valid)
    default: return true
  }
})

function onFileSelect(event: { files: File[] }) {
  if (event.files.length > 0) {
    uploadedFile.value = event.files[0]
    parseFilePreview(event.files[0])
  }
}

function onFileUpload(event: { files: File[] }) {
  if (event.files.length > 0) {
    uploadedFile.value = event.files[0]
  }
}

async function parseFilePreview(file: File) {
  loading.value = true
  try {
    const text = await file.text()
    const lines = text.split('\n').filter(l => l.trim())
    const headers = lines[0].split(',').map(h => h.trim().replace(/"/g, ''))
    
    previewColumns.value = headers.map(h => ({ field: h, header: h }))
    previewData.value = lines.slice(1, 6).map(line => {
      const values = line.split(',').map(v => v.trim().replace(/"/g, ''))
      const row: Record<string, string> = {}
      headers.forEach((h, i) => { row[h] = values[i] || '' })
      return row
    })
    totalRows.value = lines.length - 1

    columnMappings.value = headers.map(h => ({
      name: h,
      detectedType: detectType(previewData.value.map(r => r[h])),
      role: 'dimension',
      datatype: 'xsd:string',
      dimensionUri: null,
      include: true
    }))

    cubeMetadata.value.uri = `http://example.org/cube/${file.name.replace(/\.[^.]+$/, '')}`
    cubeMetadata.value.title = file.name.replace(/\.[^.]+$/, '').replace(/[-_]/g, ' ')
    publishOptions.value.graphUri = cubeMetadata.value.uri
    toast.add({ severity: 'success', summary: 'File Parsed', detail: `Successfully parsed ${file.name}`, life: 3000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to parse file', life: 3000 })
  } finally {
    loading.value = false
  }
}

function detectType(values: string[]): string {
  const sample = values.filter(v => v).slice(0, 10)
  if (sample.every(v => !isNaN(Number(v)) && Number.isInteger(Number(v)))) return 'integer'
  if (sample.every(v => !isNaN(Number(v)))) return 'decimal'
  if (sample.every(v => /^\d{4}-\d{2}-\d{2}$/.test(v))) return 'date'
  return 'string'
}

function createNewDimension(column: any) {
  console.log('Create new dimension for', column.name)
}

function prevStep() {
  if (activeStep.value > 0) activeStep.value--
}

async function nextStep() {
  if (activeStep.value < 4) {
    if (activeStep.value === 2) {
      await generatePreview()
    }
    activeStep.value++
  }
}

async function generatePreview() {
  loading.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 800))
    sampleObservations.value = `@prefix cube: <https://cube.link/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<${cubeMetadata.value.uri}/observation/1>
  a cube:Observation ;
  cube:observedBy <${cubeMetadata.value.uri}> ;
  # ... sample observation data
  .`

    generatedShape.value = `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix cube: <https://cube.link/> .

<${cubeMetadata.value.uri}/shape>
  a sh:NodeShape ;
  sh:targetClass cube:Observation ;
  # ... generated constraints
  .`
  } finally {
    loading.value = false
  }
}

async function publish() {
  publishing.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 2000))
    toast.add({ severity: 'success', summary: 'Published', detail: 'Cube published successfully', life: 3000 })
    router.push('/jobs')
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to publish cube', life: 3000 })
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.cube-wizard {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 1.5rem;
  position: relative;
}

.wizard-header {
  margin-bottom: 2rem;
}

.wizard-header h1 {
  margin-bottom: 1rem;
}

.wizard-content {
  flex: 1;
  overflow: auto;
}

.step-content h2 {
  margin-bottom: 0.5rem;
}

.step-description {
  color: var(--text-color-secondary);
  margin-bottom: 1.5rem;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  border: 2px dashed var(--surface-border);
  border-radius: 8px;
}

.file-preview {
  margin-top: 2rem;
}

.preview-info {
  color: var(--text-color-secondary);
  margin-top: 0.5rem;
}

.column-info {
  display: flex;
  flex-direction: column;
}

.column-name {
  font-weight: 600;
}

.column-type {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.dimension-mapping {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.metadata-form {
  max-width: 600px;
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.field small {
  color: var(--text-color-secondary);
}

.field-row {
  display: flex;
  gap: 1rem;
}

.field-row .field {
  flex: 1;
}

.observation-preview,
.shape-preview {
  background: var(--surface-ground);
  padding: 1rem;
  border-radius: 4px;
  overflow: auto;
  max-height: 400px;
}

.observation-preview pre,
.shape-preview pre {
  margin: 0;
  white-space: pre-wrap;
}

.validation-results {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.validation-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 4px;
}

.validation-message {
  color: var(--text-color-secondary);
  margin-left: auto;
}

.summary-card {
  background: var(--surface-ground);
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
}

.summary-card h3 {
  margin-top: 0;
  margin-bottom: 1rem;
}

.summary-item {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.summary-item span:first-child {
  font-weight: 600;
  min-width: 120px;
}

.triplestore-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.triplestore-option {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.triplestore-option:hover {
  border-color: var(--primary-color);
}

.triplestore-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-color-alpha);
}

.store-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.store-name {
  font-weight: 600;
}

.store-type {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.publish-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.field-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.wizard-footer {
  display: flex;
  padding-top: 1.5rem;
  border-top: 1px solid var(--surface-border);
  margin-top: 1.5rem;
}

.spacer {
  flex: 1;
}

.text-muted {
  color: var(--text-color-secondary);
}
</style>
