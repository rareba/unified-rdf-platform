<template>
  <div class="settings-page">
    <div class="settings-header">
      <h1>Settings</h1>
    </div>

    <div class="settings-content">
      <div class="settings-nav">
        <div
          v-for="section in sections"
          :key="section.id"
          :class="['nav-item', { active: activeSection === section.id }]"
          @click="activeSection = section.id"
        >
          <i :class="section.icon" />
          <span>{{ section.label }}</span>
        </div>
      </div>

      <div class="settings-panel">
        <!-- General Settings -->
        <div v-if="activeSection === 'general'" class="section-content">
          <h2>General Settings</h2>

          <div class="setting-group">
            <h3>Application</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Theme</label>
                <span class="setting-description">Choose your preferred color scheme</span>
              </div>
              <Dropdown v-model="settings.theme" :options="themeOptions" optionLabel="label" optionValue="value" style="width: 200px" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Language</label>
                <span class="setting-description">Interface language</span>
              </div>
              <Dropdown v-model="settings.language" :options="languageOptions" optionLabel="label" optionValue="value" style="width: 200px" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Date Format</label>
                <span class="setting-description">How dates are displayed</span>
              </div>
              <Dropdown v-model="settings.dateFormat" :options="dateFormatOptions" optionLabel="label" optionValue="value" style="width: 200px" />
            </div>
          </div>

          <div class="setting-group">
            <h3>Notifications</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Email Notifications</label>
                <span class="setting-description">Receive email when jobs complete or fail</span>
              </div>
              <InputSwitch v-model="settings.emailNotifications" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Browser Notifications</label>
                <span class="setting-description">Show desktop notifications for job events</span>
              </div>
              <InputSwitch v-model="settings.browserNotifications" />
            </div>
          </div>
        </div>

        <!-- Pipeline Settings -->
        <div v-if="activeSection === 'pipeline'" class="section-content">
          <h2>Pipeline Settings</h2>

          <div class="setting-group">
            <h3>Default Values</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Default Base URI</label>
                <span class="setting-description">Base URI for generated RDF resources</span>
              </div>
              <InputText v-model="settings.defaultBaseUri" style="width: 300px" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Default Output Format</label>
                <span class="setting-description">Preferred RDF serialization format</span>
              </div>
              <Dropdown v-model="settings.defaultOutputFormat" :options="rdfFormatOptions" optionLabel="label" optionValue="value" style="width: 200px" />
            </div>
          </div>

          <div class="setting-group">
            <h3>Execution</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Auto-save Pipelines</label>
                <span class="setting-description">Automatically save pipelines while editing</span>
              </div>
              <InputSwitch v-model="settings.autoSavePipelines" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Validate Before Run</label>
                <span class="setting-description">Always validate pipeline before execution</span>
              </div>
              <InputSwitch v-model="settings.validateBeforeRun" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Default Batch Size</label>
                <span class="setting-description">Number of records to process in each batch</span>
              </div>
              <InputNumber v-model="settings.defaultBatchSize" :min="100" :max="100000" style="width: 150px" />
            </div>
          </div>
        </div>

        <!-- SHACL Settings -->
        <div v-if="activeSection === 'shacl'" class="section-content">
          <h2>SHACL Settings</h2>

          <div class="setting-group">
            <h3>Validation</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Default Severity</label>
                <span class="setting-description">Default severity level for new constraints</span>
              </div>
              <Dropdown v-model="settings.defaultSeverity" :options="severityOptions" optionLabel="label" optionValue="value" style="width: 200px" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Stop on First Error</label>
                <span class="setting-description">Stop validation after first violation</span>
              </div>
              <InputSwitch v-model="settings.stopOnFirstError" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Max Violations</label>
                <span class="setting-description">Maximum violations to report per validation</span>
              </div>
              <InputNumber v-model="settings.maxViolations" :min="10" :max="10000" style="width: 150px" />
            </div>
          </div>

          <div class="setting-group">
            <h3>Shape Editor</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Auto-complete</label>
                <span class="setting-description">Enable auto-complete in Turtle editor</span>
              </div>
              <InputSwitch v-model="settings.editorAutoComplete" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Syntax Highlighting</label>
                <span class="setting-description">Enable syntax highlighting for RDF</span>
              </div>
              <InputSwitch v-model="settings.syntaxHighlighting" />
            </div>
          </div>
        </div>

        <!-- Triplestore Settings -->
        <div v-if="activeSection === 'triplestore'" class="section-content">
          <h2>Triplestore Settings</h2>

          <div class="setting-group">
            <h3>Connections</h3>
            <div class="connections-table">
              <DataTable :value="triplestoreConnections" class="p-datatable-sm">
                <Column field="name" header="Name" />
                <Column field="type" header="Type">
                  <template #body="{ data }">
                    <Tag :value="data.type" />
                  </template>
                </Column>
                <Column field="url" header="URL">
                  <template #body="{ data }">
                    <code>{{ data.url }}</code>
                  </template>
                </Column>
                <Column field="isDefault" header="Default" style="width: 80px">
                  <template #body="{ data }">
                    <i v-if="data.isDefault" class="pi pi-check text-green-500" />
                  </template>
                </Column>
                <Column style="width: 120px">
                  <template #body="{ data }">
                    <Button icon="pi pi-pencil" class="p-button-rounded p-button-text" @click="editConnection(data)" />
                    <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text" @click="deleteConnection(data)" />
                  </template>
                </Column>
              </DataTable>
              <Button label="Add Connection" icon="pi pi-plus" class="p-button-outlined mt-3" @click="addConnection" />
            </div>
          </div>

          <div class="setting-group">
            <h3>Query Settings</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Query Timeout</label>
                <span class="setting-description">Maximum query execution time (seconds)</span>
              </div>
              <InputNumber v-model="settings.queryTimeout" :min="10" :max="3600" suffix=" sec" style="width: 150px" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <label>Default Limit</label>
                <span class="setting-description">Default LIMIT for SELECT queries</span>
              </div>
              <InputNumber v-model="settings.defaultQueryLimit" :min="10" :max="100000" style="width: 150px" />
            </div>
          </div>
        </div>

        <!-- API Settings -->
        <div v-if="activeSection === 'api'" class="section-content">
          <h2>API Settings</h2>

          <div class="setting-group">
            <h3>API Keys</h3>
            <div class="api-keys-list">
              <div v-for="key in apiKeys" :key="key.id" class="api-key-item">
                <div class="key-info">
                  <span class="key-name">{{ key.name }}</span>
                  <span class="key-created">Created {{ formatDate(key.createdAt) }}</span>
                </div>
                <div class="key-value">
                  <code v-if="key.visible">{{ key.key }}</code>
                  <code v-else>{{ '*'.repeat(32) }}</code>
                </div>
                <div class="key-actions">
                  <Button :icon="key.visible ? 'pi pi-eye-slash' : 'pi pi-eye'" class="p-button-rounded p-button-text" @click="toggleKeyVisibility(key)" />
                  <Button icon="pi pi-copy" class="p-button-rounded p-button-text" @click="copyKey(key)" v-tooltip="'Copy'" />
                  <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text" @click="deleteApiKey(key)" />
                </div>
              </div>
            </div>
            <Button label="Generate API Key" icon="pi pi-plus" class="p-button-outlined" @click="generateApiKey" />
          </div>

          <div class="setting-group">
            <h3>Webhooks</h3>
            <div class="setting-item">
              <div class="setting-info">
                <label>Enable Webhooks</label>
                <span class="setting-description">Send HTTP callbacks on events</span>
              </div>
              <InputSwitch v-model="settings.webhooksEnabled" />
            </div>
            <div class="setting-item" v-if="settings.webhooksEnabled">
              <div class="setting-info">
                <label>Webhook URL</label>
                <span class="setting-description">URL to receive webhook events</span>
              </div>
              <InputText v-model="settings.webhookUrl" style="width: 400px" placeholder="https://example.com/webhook" />
            </div>
          </div>
        </div>

        <!-- About -->
        <div v-if="activeSection === 'about'" class="section-content">
          <h2>About RDF Forge</h2>

          <div class="about-info">
            <div class="logo-section">
              <i class="pi pi-box" style="font-size: 4rem; color: var(--primary-color)" />
              <h3>RDF Forge</h3>
              <p>Unified RDF Data Platform</p>
            </div>

            <div class="version-info">
              <div class="info-row">
                <span>Version</span>
                <span>1.0.0</span>
              </div>
              <div class="info-row">
                <span>Build</span>
                <span>2024.01.15-abc1234</span>
              </div>
              <div class="info-row">
                <span>Engine</span>
                <span>Apache Jena 5.0 / Apache Camel 4.0</span>
              </div>
            </div>

            <div class="links-section">
              <Button label="Documentation" icon="pi pi-book" class="p-button-outlined" />
              <Button label="GitHub" icon="pi pi-github" class="p-button-outlined" />
              <Button label="Report Issue" icon="pi pi-exclamation-circle" class="p-button-outlined" />
            </div>

            <div class="license-info">
              <p>Licensed under Apache License 2.0</p>
              <p class="copyright">© 2024 RDF Forge Team</p>
            </div>
          </div>
        </div>

        <div class="settings-footer" v-if="activeSection !== 'about'">
          <Button label="Reset to Defaults" class="p-button-text" @click="resetSettings" />
          <Button label="Save Changes" icon="pi pi-check" @click="saveSettings" :loading="saving" />
        </div>
      </div>
    </div>

    <!-- Connection Dialog -->
    <Dialog v-model:visible="showConnectionDialog" :header="editingConnection ? 'Edit Connection' : 'Add Connection'" :style="{ width: '500px' }" :modal="true">
      <div class="connection-form">
        <div class="field">
          <label for="connName">Name *</label>
          <InputText id="connName" v-model="connectionForm.name" class="w-full" />
        </div>
        <div class="field">
          <label for="connType">Type *</label>
          <Dropdown id="connType" v-model="connectionForm.type" :options="triplestoreTypes" optionLabel="label" optionValue="value" class="w-full" />
        </div>
        <div class="field">
          <label for="connUrl">URL *</label>
          <InputText id="connUrl" v-model="connectionForm.url" class="w-full" placeholder="http://localhost:3030/dataset" />
        </div>
        <div class="field">
          <label for="connGraph">Default Graph</label>
          <InputText id="connGraph" v-model="connectionForm.defaultGraph" class="w-full" />
        </div>
        <div class="field">
          <label for="connAuth">Authentication</label>
          <Dropdown id="connAuth" v-model="connectionForm.authType" :options="authTypes" optionLabel="label" optionValue="value" class="w-full" />
        </div>
        <div v-if="connectionForm.authType === 'basic'" class="auth-fields">
          <div class="field">
            <label for="connUser">Username</label>
            <InputText id="connUser" v-model="connectionForm.username" class="w-full" />
          </div>
          <div class="field">
            <label for="connPass">Password</label>
            <Password id="connPass" v-model="connectionForm.password" class="w-full" :feedback="false" />
          </div>
        </div>
        <div class="field-checkbox">
          <Checkbox id="connDefault" v-model="connectionForm.isDefault" :binary="true" />
          <label for="connDefault">Set as default connection</label>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" class="p-button-text" @click="showConnectionDialog = false" />
        <Button label="Test Connection" icon="pi pi-sync" class="p-button-outlined" @click="testConnection" :loading="testing" />
        <Button label="Save" icon="pi pi-check" @click="saveConnection" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import Password from 'primevue/password'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'

interface Settings {
  theme: string
  language: string
  dateFormat: string
  emailNotifications: boolean
  browserNotifications: boolean
  defaultBaseUri: string
  defaultOutputFormat: string
  autoSavePipelines: boolean
  validateBeforeRun: boolean
  defaultBatchSize: number
  defaultSeverity: string
  stopOnFirstError: boolean
  maxViolations: number
  editorAutoComplete: boolean
  syntaxHighlighting: boolean
  queryTimeout: number
  defaultQueryLimit: number
  webhooksEnabled: boolean
  webhookUrl: string
}

interface TriplestoreConnection {
  id: string
  name: string
  type: string
  url: string
  defaultGraph?: string
  authType: string
  isDefault: boolean
}

interface ApiKey {
  id: string
  name: string
  key: string
  createdAt: Date
  visible: boolean
}

const activeSection = ref('general')
const saving = ref(false)

const sections = [
  { id: 'general', label: 'General', icon: 'pi pi-cog' },
  { id: 'pipeline', label: 'Pipeline', icon: 'pi pi-sitemap' },
  { id: 'shacl', label: 'SHACL', icon: 'pi pi-check-square' },
  { id: 'triplestore', label: 'Triplestore', icon: 'pi pi-database' },
  { id: 'api', label: 'API', icon: 'pi pi-key' },
  { id: 'about', label: 'About', icon: 'pi pi-info-circle' }
]

const settings = reactive<Settings>({
  theme: 'light',
  language: 'en',
  dateFormat: 'yyyy-MM-dd',
  emailNotifications: true,
  browserNotifications: false,
  defaultBaseUri: 'http://example.org/',
  defaultOutputFormat: 'turtle',
  autoSavePipelines: true,
  validateBeforeRun: true,
  defaultBatchSize: 1000,
  defaultSeverity: 'sh:Violation',
  stopOnFirstError: false,
  maxViolations: 1000,
  editorAutoComplete: true,
  syntaxHighlighting: true,
  queryTimeout: 60,
  defaultQueryLimit: 1000,
  webhooksEnabled: false,
  webhookUrl: ''
})

const themeOptions = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
  { label: 'System', value: 'system' }
]

const languageOptions = [
  { label: 'English', value: 'en' },
  { label: 'German', value: 'de' },
  { label: 'French', value: 'fr' }
]

const dateFormatOptions = [
  { label: 'YYYY-MM-DD', value: 'yyyy-MM-dd' },
  { label: 'DD/MM/YYYY', value: 'dd/MM/yyyy' },
  { label: 'MM/DD/YYYY', value: 'MM/dd/yyyy' }
]

const rdfFormatOptions = [
  { label: 'Turtle', value: 'turtle' },
  { label: 'N-Triples', value: 'ntriples' },
  { label: 'JSON-LD', value: 'jsonld' },
  { label: 'RDF/XML', value: 'rdfxml' }
]

const severityOptions = [
  { label: 'Violation', value: 'sh:Violation' },
  { label: 'Warning', value: 'sh:Warning' },
  { label: 'Info', value: 'sh:Info' }
]

const triplestoreTypes = [
  { label: 'Apache Fuseki', value: 'fuseki' },
  { label: 'Stardog', value: 'stardog' },
  { label: 'GraphDB', value: 'graphdb' },
  { label: 'Amazon Neptune', value: 'neptune' },
  { label: 'Virtuoso', value: 'virtuoso' }
]

const authTypes = [
  { label: 'None', value: 'none' },
  { label: 'Basic Auth', value: 'basic' },
  { label: 'API Key', value: 'apikey' },
  { label: 'OAuth2', value: 'oauth2' }
]

const triplestoreConnections = ref<TriplestoreConnection[]>([
  { id: '1', name: 'Local Fuseki', type: 'fuseki', url: 'http://localhost:3030/dataset', authType: 'none', isDefault: true },
  { id: '2', name: 'Stardog Cloud', type: 'stardog', url: 'https://cloud.stardog.com/mydb', authType: 'basic', isDefault: false }
])

const apiKeys = ref<ApiKey[]>([
  { id: '1', name: 'CLI Access', key: 'rf_sk_1234567890abcdef', createdAt: new Date('2024-01-10'), visible: false },
  { id: '2', name: 'CI/CD Pipeline', key: 'rf_sk_abcdef1234567890', createdAt: new Date('2024-01-15'), visible: false }
])

const showConnectionDialog = ref(false)
const editingConnection = ref<TriplestoreConnection | null>(null)
const testing = ref(false)

const connectionForm = reactive({
  name: '',
  type: 'fuseki',
  url: '',
  defaultGraph: '',
  authType: 'none',
  username: '',
  password: '',
  isDefault: false
})

onMounted(() => {
  loadSettings()
})

function loadSettings() {
  // Load from API or localStorage
}

async function saveSettings() {
  saving.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 500))
    // Save to API
  } finally {
    saving.value = false
  }
}

function resetSettings() {
  // Reset to defaults
}

function addConnection() {
  editingConnection.value = null
  Object.assign(connectionForm, {
    name: '',
    type: 'fuseki',
    url: '',
    defaultGraph: '',
    authType: 'none',
    username: '',
    password: '',
    isDefault: false
  })
  showConnectionDialog.value = true
}

function editConnection(conn: TriplestoreConnection) {
  editingConnection.value = conn
  Object.assign(connectionForm, conn)
  showConnectionDialog.value = true
}

function deleteConnection(conn: TriplestoreConnection) {
  triplestoreConnections.value = triplestoreConnections.value.filter(c => c.id !== conn.id)
}

async function testConnection() {
  testing.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 1000))
  } finally {
    testing.value = false
  }
}

function saveConnection() {
  showConnectionDialog.value = false
}

function generateApiKey() {
  const newKey: ApiKey = {
    id: Date.now().toString(),
    name: 'New API Key',
    key: 'rf_sk_' + Math.random().toString(36).substring(2, 18),
    createdAt: new Date(),
    visible: true
  }
  apiKeys.value.push(newKey)
}

function toggleKeyVisibility(key: ApiKey) {
  key.visible = !key.visible
}

function copyKey(key: ApiKey) {
  navigator.clipboard.writeText(key.key)
}

function deleteApiKey(key: ApiKey) {
  apiKeys.value = apiKeys.value.filter(k => k.id !== key.id)
}

function formatDate(date: Date): string {
  return new Date(date).toLocaleDateString()
}
</script>

<style scoped>
.settings-page {
  padding: 1.5rem;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.settings-header h1 {
  margin: 0 0 1.5rem;
}

.settings-content {
  display: flex;
  gap: 2rem;
  flex: 1;
  overflow: hidden;
}

.settings-nav {
  width: 200px;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-item:hover {
  background: var(--surface-100);
}

.nav-item.active {
  background: var(--primary-color-alpha);
  color: var(--primary-color);
  font-weight: 600;
}

.settings-panel {
  flex: 1;
  background: var(--surface-card);
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.section-content {
  flex: 1;
}

.section-content h2 {
  margin: 0 0 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--surface-border);
}

.setting-group {
  margin-bottom: 2rem;
}

.setting-group h3 {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  text-transform: uppercase;
  color: var(--text-color-secondary);
  letter-spacing: 0.05em;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  border-bottom: 1px solid var(--surface-border);
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-info label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.setting-description {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.connections-table {
  margin-bottom: 1rem;
}

.api-keys-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.api-key-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.key-info {
  display: flex;
  flex-direction: column;
  min-width: 150px;
}

.key-name {
  font-weight: 600;
}

.key-created {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.key-value {
  flex: 1;
}

.key-value code {
  font-size: 0.875rem;
}

.key-actions {
  display: flex;
  gap: 0.25rem;
}

.about-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 2rem;
}

.logo-section {
  margin-bottom: 2rem;
}

.logo-section h3 {
  margin: 1rem 0 0.5rem;
}

.logo-section p {
  color: var(--text-color-secondary);
}

.version-info {
  background: var(--surface-ground);
  padding: 1.5rem 2rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  min-width: 300px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
}

.info-row span:first-child {
  color: var(--text-color-secondary);
}

.links-section {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
}

.license-info {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}

.copyright {
  margin-top: 0.5rem;
}

.settings-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--surface-border);
  margin-top: auto;
}

.connection-form .field {
  margin-bottom: 1rem;
}

.connection-form .field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.auth-fields {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.field-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
}
</style>
