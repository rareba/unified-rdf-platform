<template>
  <div class="triplestore-browser">
    <div class="header">
      <h1>Triplestore Browser</h1>
      <div class="connection-selector">
        <Dropdown
          v-model="selectedConnection"
          :options="connections"
          optionLabel="name"
          optionValue="id"
          placeholder="Select connection"
          class="connection-dropdown"
        />
        <Tag
          :value="connectionStatus"
          :severity="connectionStatus === 'Connected' ? 'success' : 'danger'"
          :icon="connectionStatus === 'Connected' ? 'pi pi-check-circle' : 'pi pi-times-circle'"
        />
        <Button icon="pi pi-cog" class="p-button-text" @click="showSettingsDialog = true" v-tooltip="'Manage connections'" />
      </div>
    </div>

    <div class="browser-content">
      <div class="sidebar">
        <Panel header="Graphs">
          <div class="graph-list">
            <div
              v-for="graph in graphs"
              :key="graph.uri"
              :class="['graph-item', { selected: selectedGraph === graph.uri }]"
              @click="selectGraph(graph.uri)"
            >
              <i class="pi pi-folder" />
              <div class="graph-info">
                <span class="graph-name">{{ getGraphName(graph.uri) }}</span>
                <span class="graph-count">{{ formatNumber(graph.tripleCount) }} triples</span>
              </div>
            </div>
          </div>
        </Panel>

        <Panel header="Namespaces" :toggleable="true" :collapsed="true">
          <div class="namespace-list">
            <div v-for="ns in namespaces" :key="ns.prefix" class="namespace-item">
              <code class="ns-prefix">{{ ns.prefix }}:</code>
              <span class="ns-uri" :title="ns.uri">{{ truncateUri(ns.uri) }}</span>
            </div>
          </div>
        </Panel>
      </div>

      <div class="main-panel">
        <TabView>
          <TabPanel header="SPARQL Query">
            <div class="query-toolbar">
              <Dropdown
                v-model="selectedQueryTemplate"
                :options="queryTemplates"
                optionLabel="name"
                placeholder="Query templates"
                @change="applyTemplate"
                class="template-dropdown"
              />
              <div class="spacer" />
              <Button label="Execute" icon="pi pi-play" @click="executeQuery" :loading="executing" />
              <Button icon="pi pi-save" class="p-button-text" @click="saveQuery" v-tooltip="'Save query'" />
              <Button icon="pi pi-history" class="p-button-text" @click="showHistoryDialog = true" v-tooltip="'History'" />
            </div>

            <div class="query-editor">
              <Textarea
                v-model="sparqlQuery"
                :rows="10"
                placeholder="Enter SPARQL query..."
                class="w-full code-textarea"
              />
            </div>

            <div class="query-results" v-if="queryResults">
              <div class="results-header">
                <span>{{ queryResults.bindings.length }} results ({{ queryResults.executionTime }}ms)</span>
                <div class="results-actions">
                  <Button icon="pi pi-download" class="p-button-text" @click="exportResults('csv')" v-tooltip="'Export CSV'" />
                  <Button icon="pi pi-code" class="p-button-text" @click="exportResults('json')" v-tooltip="'Export JSON'" />
                </div>
              </div>
              <DataTable
                :value="queryResults.bindings"
                :rows="20"
                :paginator="queryResults.bindings.length > 20"
                class="p-datatable-sm"
                responsiveLayout="scroll"
              >
                <Column v-for="col in queryResults.variables" :key="col" :field="col" :header="col">
                  <template #body="{ data }">
                    <span v-if="isUri(data[col])" class="uri-value" @click="browseUri(data[col])">
                      {{ formatUri(data[col]) }}
                    </span>
                    <span v-else-if="isLiteral(data[col])" class="literal-value">
                      "{{ data[col].value }}"
                      <sup v-if="data[col].datatype" class="datatype">{{ getLocalName(data[col].datatype) }}</sup>
                    </span>
                    <span v-else>{{ data[col] }}</span>
                  </template>
                </Column>
              </DataTable>
            </div>

            <div class="query-error" v-if="queryError">
              <i class="pi pi-times-circle" />
              <span>{{ queryError }}</span>
            </div>
          </TabPanel>

          <TabPanel header="Graph Browser">
            <div class="graph-browser" v-if="selectedGraph">
              <div class="resource-search">
                <span class="p-input-icon-left">
                  <i class="pi pi-search" />
                  <InputText v-model="resourceSearch" placeholder="Search resources..." @keyup.enter="searchResources" />
                </span>
                <Button label="Search" @click="searchResources" />
              </div>

              <div class="resource-viewer" v-if="selectedResource">
                <div class="resource-header">
                  <h3>{{ formatUri(selectedResource.uri) }}</h3>
                  <div class="resource-types">
                    <Tag v-for="type in selectedResource.types" :key="type" :value="getLocalName(type)" />
                  </div>
                </div>

                <div class="properties-table">
                  <DataTable :value="selectedResource.properties" class="p-datatable-sm">
                    <Column field="predicate" header="Property" style="width: 200px">
                      <template #body="{ data }">
                        <span class="predicate" @click="browseUri(data.predicate)">{{ formatUri(data.predicate) }}</span>
                      </template>
                    </Column>
                    <Column field="object" header="Value">
                      <template #body="{ data }">
                        <span v-if="data.objectType === 'uri'" class="uri-value" @click="browseUri(data.object)">
                          {{ formatUri(data.object) }}
                        </span>
                        <span v-else class="literal-value">
                          "{{ data.object }}"
                          <sup v-if="data.datatype" class="datatype">{{ getLocalName(data.datatype) }}</sup>
                          <sup v-if="data.language" class="lang">@{{ data.language }}</sup>
                        </span>
                      </template>
                    </Column>
                  </DataTable>
                </div>
              </div>

              <div class="resource-list" v-else>
                <DataTable :value="graphResources" :rows="20" :paginator="true" class="p-datatable-sm" @row-click="viewResource">
                  <Column field="uri" header="Subject">
                    <template #body="{ data }">
                      <span class="uri-value">{{ formatUri(data.uri) }}</span>
                    </template>
                  </Column>
                  <Column field="type" header="Type">
                    <template #body="{ data }">
                      <Tag v-if="data.type" :value="getLocalName(data.type)" />
                    </template>
                  </Column>
                  <Column field="label" header="Label" />
                </DataTable>
              </div>
            </div>

            <div class="no-graph-selected" v-else>
              <i class="pi pi-folder-open" style="font-size: 3rem; color: var(--text-color-secondary)" />
              <p>Select a graph from the sidebar to browse its contents</p>
            </div>
          </TabPanel>

          <TabPanel header="Upload">
            <div class="upload-section">
              <h3>Upload RDF Data</h3>
              <p class="upload-description">Upload RDF data to a named graph in the triplestore.</p>

              <div class="upload-form">
                <div class="field">
                  <label for="targetGraph">Target Graph URI</label>
                  <InputText id="targetGraph" v-model="uploadForm.graphUri" class="w-full" placeholder="http://example.org/graph/my-data" />
                </div>

                <div class="field">
                  <label>RDF Format</label>
                  <Dropdown v-model="uploadForm.format" :options="rdfFormats" optionLabel="label" optionValue="value" class="w-full" />
                </div>

                <div class="field">
                  <label>Upload Method</label>
                  <div class="upload-method-options">
                    <div :class="['method-option', { selected: uploadForm.method === 'file' }]" @click="uploadForm.method = 'file'">
                      <i class="pi pi-upload" />
                      <span>File Upload</span>
                    </div>
                    <div :class="['method-option', { selected: uploadForm.method === 'paste' }]" @click="uploadForm.method = 'paste'">
                      <i class="pi pi-file-edit" />
                      <span>Paste Content</span>
                    </div>
                  </div>
                </div>

                <FileUpload
                  v-if="uploadForm.method === 'file'"
                  mode="basic"
                  accept=".ttl,.rdf,.nt,.nq,.jsonld,.json"
                  :maxFileSize="100000000"
                  @select="onRdfFileSelect"
                  chooseLabel="Select RDF File"
                />

                <div class="field" v-if="uploadForm.method === 'paste'">
                  <label>RDF Content</label>
                  <Textarea v-model="uploadForm.content" :rows="15" class="w-full code-textarea" placeholder="Paste RDF content here..." />
                </div>

                <Button label="Upload to Graph" icon="pi pi-cloud-upload" @click="uploadRdf" :loading="uploading" :disabled="!canUpload" />
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>
    </div>

    <!-- Connection Settings Dialog -->
    <Dialog v-model:visible="showSettingsDialog" header="Manage Connections" :style="{ width: '600px' }" :modal="true">
      <div class="connections-manager">
        <DataTable :value="connections" class="p-datatable-sm">
          <Column field="name" header="Name" />
          <Column field="type" header="Type">
            <template #body="{ data }">
              <Tag :value="data.type" />
            </template>
          </Column>
          <Column field="url" header="URL">
            <template #body="{ data }">
              <code>{{ truncateUrl(data.url) }}</code>
            </template>
          </Column>
          <Column style="width: 100px">
            <template #body="{ data }">
              <Button icon="pi pi-pencil" class="p-button-rounded p-button-text" @click="editConnection(data)" />
              <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text" @click="deleteConnection(data)" />
            </template>
          </Column>
        </DataTable>
        <Button label="Add Connection" icon="pi pi-plus" class="p-button-outlined mt-3" @click="addConnection" />
      </div>
    </Dialog>

    <!-- Query History Dialog -->
    <Dialog v-model:visible="showHistoryDialog" header="Query History" :style="{ width: '700px' }" :modal="true">
      <DataTable :value="queryHistory" :rows="10" :paginator="true" class="p-datatable-sm">
        <Column field="query" header="Query">
          <template #body="{ data }">
            <code class="query-preview">{{ truncateQuery(data.query) }}</code>
          </template>
        </Column>
        <Column field="executedAt" header="Executed" style="width: 150px">
          <template #body="{ data }">
            {{ formatDate(data.executedAt) }}
          </template>
        </Column>
        <Column style="width: 80px">
          <template #body="{ data }">
            <Button icon="pi pi-replay" class="p-button-rounded p-button-text" @click="replayQuery(data.query)" />
          </template>
        </Column>
      </DataTable>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import Tag from 'primevue/tag'
import Panel from 'primevue/panel'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import { useTriplestoreStore } from '@/stores/triplestore'

interface TriplestoreConnection {
  id: string
  name: string
  type: 'fuseki' | 'stardog' | 'graphdb' | 'neptune'
  url: string
  defaultGraph?: string
}

interface Graph {
  uri: string
  tripleCount: number
}

interface QueryResult {
  variables: string[]
  bindings: Record<string, any>[]
  executionTime: number
}

const route = useRoute()
const triplestoreStore = useTriplestoreStore()

const connections = ref<TriplestoreConnection[]>([])
const selectedConnection = ref<string>('')
const connectionStatus = ref('Disconnected')
const graphs = ref<Graph[]>([])
const selectedGraph = ref<string | null>(null)
const namespaces = ref<{ prefix: string; uri: string }[]>([])

const sparqlQuery = ref(`SELECT ?s ?p ?o
WHERE {
  ?s ?p ?o
}
LIMIT 100`)

const selectedQueryTemplate = ref(null)
const queryTemplates = [
  { name: 'Count all triples', query: 'SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }' },
  { name: 'List types', query: 'SELECT DISTINCT ?type (COUNT(?s) as ?count) WHERE { ?s a ?type } GROUP BY ?type ORDER BY DESC(?count)' },
  { name: 'List predicates', query: 'SELECT DISTINCT ?p (COUNT(*) as ?count) WHERE { ?s ?p ?o } GROUP BY ?p ORDER BY DESC(?count)' },
  { name: 'Sample data', query: 'SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100' }
]

const queryResults = ref<QueryResult | null>(null)
const queryError = ref<string | null>(null)
const executing = ref(false)
const queryHistory = ref<{ query: string; executedAt: Date }[]>([])

const resourceSearch = ref('')
const graphResources = ref<any[]>([])
const selectedResource = ref<any>(null)

const uploadForm = ref({
  graphUri: '',
  format: 'turtle',
  method: 'file' as 'file' | 'paste',
  content: '',
  file: null as File | null
})
const uploading = ref(false)

const rdfFormats = [
  { label: 'Turtle', value: 'turtle' },
  { label: 'RDF/XML', value: 'rdfxml' },
  { label: 'N-Triples', value: 'ntriples' },
  { label: 'JSON-LD', value: 'jsonld' }
]

const showSettingsDialog = ref(false)
const showHistoryDialog = ref(false)

const canUpload = computed(() => {
  return uploadForm.value.graphUri && (uploadForm.value.file || uploadForm.value.content)
})

onMounted(async () => {
  await loadConnections()
  const graphFromRoute = route.query.graph as string
  if (graphFromRoute) {
    selectedGraph.value = graphFromRoute
  }
})

async function loadConnections() {
  connections.value = await triplestoreStore.fetchConnections()
  if (connections.value.length > 0) {
    selectedConnection.value = connections.value[0].id
    await connectToStore()
  }
}

async function connectToStore() {
  try {
    await triplestoreStore.connect(selectedConnection.value)
    connectionStatus.value = 'Connected'
    await loadGraphs()
    loadNamespaces()
  } catch (e) {
    connectionStatus.value = 'Disconnected'
  }
}

async function loadGraphs() {
  graphs.value = await triplestoreStore.fetchGraphs(selectedConnection.value)
}

function loadNamespaces() {
  namespaces.value = [
    { prefix: 'rdf', uri: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#' },
    { prefix: 'rdfs', uri: 'http://www.w3.org/2000/01/rdf-schema#' },
    { prefix: 'xsd', uri: 'http://www.w3.org/2001/XMLSchema#' },
    { prefix: 'owl', uri: 'http://www.w3.org/2002/07/owl#' },
    { prefix: 'sh', uri: 'http://www.w3.org/ns/shacl#' },
    { prefix: 'cube', uri: 'https://cube.link/' }
  ]
}

async function selectGraph(uri: string) {
  selectedGraph.value = uri
  selectedResource.value = null
  await loadGraphResources()
}

async function loadGraphResources() {
  if (!selectedGraph.value) return
  graphResources.value = await triplestoreStore.fetchGraphResources(selectedConnection.value, selectedGraph.value)
}

function applyTemplate(event: { value: any }) {
  if (event.value) {
    sparqlQuery.value = event.value.query
  }
}

async function executeQuery() {
  executing.value = true
  queryError.value = null
  queryResults.value = null
  try {
    const result = await triplestoreStore.executeSparql(selectedConnection.value, sparqlQuery.value, selectedGraph.value)
    queryResults.value = result
    queryHistory.value.unshift({ query: sparqlQuery.value, executedAt: new Date() })
  } catch (e: any) {
    queryError.value = e.message
  } finally {
    executing.value = false
  }
}

function saveQuery() {
  console.log('Save query')
}

function replayQuery(query: string) {
  sparqlQuery.value = query
  showHistoryDialog.value = false
  executeQuery()
}

function exportResults(format: 'csv' | 'json') {
  if (!queryResults.value) return
  let content: string
  let mimeType: string
  let filename: string

  if (format === 'csv') {
    const header = queryResults.value.variables.join(',')
    const rows = queryResults.value.bindings.map(b => 
      queryResults.value!.variables.map(v => JSON.stringify(b[v]?.value || b[v] || '')).join(',')
    )
    content = [header, ...rows].join('\n')
    mimeType = 'text/csv'
    filename = 'query-results.csv'
  } else {
    content = JSON.stringify(queryResults.value.bindings, null, 2)
    mimeType = 'application/json'
    filename = 'query-results.json'
  }

  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
}

async function searchResources() {
  if (!resourceSearch.value) return
  graphResources.value = await triplestoreStore.searchResources(selectedConnection.value, selectedGraph.value!, resourceSearch.value)
}

function viewResource(event: { data: any }) {
  selectedResource.value = event.data
}

function browseUri(uri: string) {
  resourceSearch.value = uri
  searchResources()
}

function onRdfFileSelect(event: { files: File[] }) {
  uploadForm.value.file = event.files[0]
}

async function uploadRdf() {
  uploading.value = true
  try {
    const content = uploadForm.value.method === 'file' && uploadForm.value.file
      ? await uploadForm.value.file.text()
      : uploadForm.value.content
    await triplestoreStore.uploadRdf(selectedConnection.value, uploadForm.value.graphUri, content, uploadForm.value.format)
    await loadGraphs()
    uploadForm.value = { graphUri: '', format: 'turtle', method: 'file', content: '', file: null }
  } finally {
    uploading.value = false
  }
}

function addConnection() {
  console.log('Add connection')
}

function editConnection(connection: TriplestoreConnection) {
  console.log('Edit connection', connection)
}

function deleteConnection(connection: TriplestoreConnection) {
  console.log('Delete connection', connection)
}

function getGraphName(uri: string): string {
  const parts = uri.split('/')
  return parts[parts.length - 1] || uri
}

function getLocalName(uri: string): string {
  const hashIndex = uri.lastIndexOf('#')
  const slashIndex = uri.lastIndexOf('/')
  const index = Math.max(hashIndex, slashIndex)
  return index >= 0 ? uri.substring(index + 1) : uri
}

function formatUri(uri: string): string {
  for (const ns of namespaces.value) {
    if (uri.startsWith(ns.uri)) {
      return `${ns.prefix}:${uri.substring(ns.uri.length)}`
    }
  }
  return getLocalName(uri)
}

function truncateUri(uri: string): string {
  return uri.length > 40 ? uri.substring(0, 40) + '...' : uri
}

function truncateUrl(url: string): string {
  return url.length > 30 ? url.substring(0, 30) + '...' : url
}

function truncateQuery(query: string): string {
  return query.length > 80 ? query.substring(0, 80) + '...' : query
}

function isUri(value: any): boolean {
  return value && typeof value === 'object' && value.type === 'uri'
}

function isLiteral(value: any): boolean {
  return value && typeof value === 'object' && value.type === 'literal'
}

function formatNumber(num: number): string {
  return num.toLocaleString()
}

function formatDate(date: Date): string {
  return new Date(date).toLocaleString()
}
</script>

<style scoped>
.triplestore-browser {
  display: flex;
  flex-direction: column;
  height: 100%;
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

.connection-selector {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.connection-dropdown {
  width: 250px;
}

.browser-content {
  display: flex;
  gap: 1.5rem;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  overflow: auto;
}

.main-panel {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.graph-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.graph-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem;
  border-radius: 4px;
  cursor: pointer;
}

.graph-item:hover {
  background: var(--surface-100);
}

.graph-item.selected {
  background: var(--primary-color-alpha);
}

.graph-info {
  display: flex;
  flex-direction: column;
}

.graph-name {
  font-weight: 500;
  font-size: 0.875rem;
}

.graph-count {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.namespace-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.namespace-item {
  display: flex;
  gap: 0.5rem;
  font-size: 0.75rem;
}

.ns-prefix {
  color: var(--primary-color);
  font-weight: 600;
}

.ns-uri {
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
}

.query-toolbar {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 1rem;
}

.template-dropdown {
  width: 200px;
}

.spacer {
  flex: 1;
}

.query-editor {
  margin-bottom: 1rem;
}

.code-textarea {
  font-family: monospace;
  font-size: 0.875rem;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
  color: var(--text-color-secondary);
}

.results-actions {
  display: flex;
  gap: 0.25rem;
}

.uri-value {
  color: var(--primary-color);
  cursor: pointer;
}

.uri-value:hover {
  text-decoration: underline;
}

.literal-value {
  color: var(--green-700);
}

.datatype,
.lang {
  font-size: 0.625rem;
  color: var(--text-color-secondary);
  margin-left: 0.25rem;
}

.query-error {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: 4px;
  color: var(--red-700);
}

.resource-search {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.resource-header {
  margin-bottom: 1rem;
}

.resource-header h3 {
  margin-bottom: 0.5rem;
}

.resource-types {
  display: flex;
  gap: 0.5rem;
}

.predicate {
  color: var(--primary-color);
  cursor: pointer;
}

.predicate:hover {
  text-decoration: underline;
}

.no-graph-selected {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 1rem;
  color: var(--text-color-secondary);
}

.upload-section {
  max-width: 600px;
}

.upload-description {
  color: var(--text-color-secondary);
  margin-bottom: 1.5rem;
}

.upload-form .field {
  margin-bottom: 1.5rem;
}

.upload-form .field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.upload-method-options {
  display: flex;
  gap: 1rem;
}

.method-option {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.method-option:hover {
  border-color: var(--primary-color);
}

.method-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-color-alpha);
}

.method-option i {
  font-size: 1.5rem;
  color: var(--primary-color);
}

.query-preview {
  font-size: 0.75rem;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
