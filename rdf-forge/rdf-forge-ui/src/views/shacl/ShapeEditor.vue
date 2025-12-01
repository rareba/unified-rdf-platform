<template>
  <div class="shape-editor">
    <div class="header">
      <div class="header-left">
        <Button icon="pi pi-arrow-left" class="p-button-text" @click="goBack" />
        <div class="title-section">
          <h1>{{ isNew ? 'New Shape' : shape?.name }}</h1>
          <Tag v-if="!isNew" :value="`v${shape?.version}`" />
        </div>
      </div>
      <div class="header-actions">
        <Button label="Validate Syntax" icon="pi pi-check-circle" class="p-button-outlined" @click="validateSyntax" />
        <Button label="Test with Data" icon="pi pi-play" class="p-button-outlined" @click="showTestDialog = true" />
        <Button label="Save" icon="pi pi-save" @click="saveShape" :loading="saving" />
      </div>
    </div>

    <div class="editor-content">
      <div class="editor-sidebar">
        <Panel header="Shape Info">
          <div class="field">
            <label for="shapeUri">Shape URI *</label>
            <InputText id="shapeUri" v-model="formData.uri" class="w-full" />
          </div>
          <div class="field">
            <label for="shapeName">Name *</label>
            <InputText id="shapeName" v-model="formData.name" class="w-full" />
          </div>
          <div class="field">
            <label for="shapeDesc">Description</label>
            <Textarea id="shapeDesc" v-model="formData.description" :rows="2" class="w-full" />
          </div>
          <div class="field">
            <label for="targetClass">Target Class</label>
            <InputText id="targetClass" v-model="formData.targetClass" class="w-full" placeholder="e.g., schema:Person" />
          </div>
          <div class="field">
            <label for="category">Category</label>
            <Dropdown id="category" v-model="formData.category" :options="categoryOptions" placeholder="Select category" class="w-full" />
          </div>
          <div class="field">
            <label for="tags">Tags</label>
            <Chips id="tags" v-model="formData.tags" separator="," class="w-full" />
          </div>
        </Panel>

        <Panel header="Prefixes" :toggleable="true" :collapsed="true">
          <div class="prefix-list">
            <div v-for="(uri, prefix) in prefixes" :key="prefix" class="prefix-item">
              <code>@prefix {{ prefix }}:</code>
              <span>{{ truncateUri(uri) }}</span>
            </div>
          </div>
          <Button label="Add Prefix" icon="pi pi-plus" class="p-button-text p-button-sm mt-2" @click="addPrefix" />
        </Panel>
      </div>

      <div class="editor-main">
        <TabView v-model:activeIndex="activeTab">
          <TabPanel header="Code Editor">
            <div class="code-editor-container">
              <Textarea
                v-model="formData.content"
                class="w-full code-textarea"
                :rows="25"
                placeholder="Enter SHACL shape in Turtle format..."
              />
            </div>
          </TabPanel>

          <TabPanel header="Form Builder">
            <div class="form-builder">
              <div class="properties-header">
                <h3>Property Shapes</h3>
                <Button label="Add Property" icon="pi pi-plus" class="p-button-sm" @click="addProperty" />
              </div>

              <div v-if="properties.length === 0" class="empty-properties">
                <i class="pi pi-list" style="font-size: 2rem; color: var(--text-color-secondary)" />
                <p>No property shapes defined</p>
                <Button label="Add First Property" icon="pi pi-plus" @click="addProperty" />
              </div>

              <div v-for="(prop, index) in properties" :key="index" class="property-card">
                <div class="property-header">
                  <span class="property-path">{{ prop.path || 'New Property' }}</span>
                  <div class="property-actions">
                    <Button icon="pi pi-chevron-up" class="p-button-rounded p-button-text p-button-sm" :disabled="index === 0" @click="movePropertyUp(index)" />
                    <Button icon="pi pi-chevron-down" class="p-button-rounded p-button-text p-button-sm" :disabled="index === properties.length - 1" @click="movePropertyDown(index)" />
                    <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text p-button-sm" @click="removeProperty(index)" />
                  </div>
                </div>

                <div class="property-form">
                  <div class="form-row">
                    <div class="field">
                      <label>Path *</label>
                      <InputText v-model="prop.path" placeholder="e.g., schema:name" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Name</label>
                      <InputText v-model="prop.name" placeholder="Human-readable name" class="w-full" />
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="field">
                      <label>Datatype</label>
                      <Dropdown v-model="prop.datatype" :options="datatypeOptions" optionLabel="label" optionValue="value" placeholder="Select datatype" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Node Kind</label>
                      <Dropdown v-model="prop.nodeKind" :options="nodeKindOptions" optionLabel="label" optionValue="value" placeholder="Select node kind" class="w-full" />
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="field">
                      <label>Min Count</label>
                      <InputNumber v-model="prop.minCount" :min="0" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Max Count</label>
                      <InputNumber v-model="prop.maxCount" :min="0" class="w-full" />
                    </div>
                  </div>

                  <div class="form-row" v-if="prop.datatype === 'xsd:string'">
                    <div class="field">
                      <label>Min Length</label>
                      <InputNumber v-model="prop.minLength" :min="0" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Max Length</label>
                      <InputNumber v-model="prop.maxLength" :min="0" class="w-full" />
                    </div>
                  </div>

                  <div class="field" v-if="prop.datatype === 'xsd:string'">
                    <label>Pattern (Regex)</label>
                    <InputText v-model="prop.pattern" placeholder="^[A-Z].*$" class="w-full" />
                  </div>

                  <div class="form-row" v-if="['xsd:integer', 'xsd:decimal'].includes(prop.datatype || '')">
                    <div class="field">
                      <label>Min Value</label>
                      <InputNumber v-model="prop.minInclusive" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Max Value</label>
                      <InputNumber v-model="prop.maxInclusive" class="w-full" />
                    </div>
                  </div>

                  <div class="field">
                    <label>Custom Message</label>
                    <InputText v-model="prop.message" placeholder="Validation error message" class="w-full" />
                  </div>
                </div>
              </div>

              <div class="generate-action" v-if="properties.length > 0">
                <Button label="Generate Turtle from Form" icon="pi pi-code" @click="generateFromForm" />
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>
    </div>

    <!-- Test Validation Dialog -->
    <Dialog v-model:visible="showTestDialog" header="Test Validation" :style="{ width: '800px' }" :modal="true">
      <div class="test-content">
        <div class="field">
          <label>Test Data (Turtle)</label>
          <Textarea v-model="testData" :rows="10" class="w-full code-textarea" placeholder="@prefix schema: <http://schema.org/> ..." />
        </div>
        <Button label="Run Validation" icon="pi pi-play" @click="runTest" :loading="testing" />

        <div v-if="testResult" class="test-results">
          <div :class="['result-header', testResult.conforms ? 'success' : 'error']">
            <i :class="testResult.conforms ? 'pi pi-check-circle' : 'pi pi-times-circle'" />
            <span>{{ testResult.conforms ? 'Validation Passed' : `${testResult.violationCount} Violation(s)` }}</span>
          </div>

          <div v-if="!testResult.conforms" class="violations-list">
            <div v-for="(v, i) in testResult.violations" :key="i" class="violation-item">
              <div class="violation-header">
                <Tag :value="v.severity" :severity="getSeverity(v.severity)" />
                <span class="focus-node">{{ v.focusNode }}</span>
              </div>
              <div class="violation-details">
                <span v-if="v.path"><strong>Path:</strong> {{ v.path }}</span>
                <span v-if="v.value"><strong>Value:</strong> {{ v.value }}</span>
                <span><strong>Message:</strong> {{ v.message }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Dropdown from 'primevue/dropdown'
import Chips from 'primevue/chips'
import Tag from 'primevue/tag'
import Panel from 'primevue/panel'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Dialog from 'primevue/dialog'
import { useShaclStore } from '@/stores/shacl'

interface PropertyShape {
  path: string
  name?: string
  datatype?: string
  nodeKind?: string
  minCount?: number
  maxCount?: number
  minLength?: number
  maxLength?: number
  minInclusive?: number
  maxInclusive?: number
  pattern?: string
  message?: string
}

const route = useRoute()
const router = useRouter()
const shaclStore = useShaclStore()

const shapeId = computed(() => route.params.id as string | undefined)
const isNew = computed(() => !shapeId.value)
const shape = ref<any>(null)

const activeTab = ref(0)
const saving = ref(false)
const testing = ref(false)
const showTestDialog = ref(false)
const testData = ref('')
const testResult = ref<any>(null)

const formData = reactive({
  uri: '',
  name: '',
  description: '',
  targetClass: '',
  category: '',
  tags: [] as string[],
  content: ''
})

const properties = ref<PropertyShape[]>([])

const prefixes = ref<Record<string, string>>({
  sh: 'http://www.w3.org/ns/shacl#',
  xsd: 'http://www.w3.org/2001/XMLSchema#',
  schema: 'http://schema.org/',
  ex: 'http://example.org/'
})

const categoryOptions = ['Cube', 'Schema.org', 'DCAT', 'SKOS', 'Custom']

const datatypeOptions = [
  { label: 'String', value: 'xsd:string' },
  { label: 'Integer', value: 'xsd:integer' },
  { label: 'Decimal', value: 'xsd:decimal' },
  { label: 'Boolean', value: 'xsd:boolean' },
  { label: 'Date', value: 'xsd:date' },
  { label: 'DateTime', value: 'xsd:dateTime' },
  { label: 'IRI', value: null }
]

const nodeKindOptions = [
  { label: 'Any', value: null },
  { label: 'IRI', value: 'sh:IRI' },
  { label: 'Literal', value: 'sh:Literal' },
  { label: 'Blank Node', value: 'sh:BlankNode' },
  { label: 'IRI or Literal', value: 'sh:IRIOrLiteral' }
]

onMounted(async () => {
  if (shapeId.value) {
    await loadShape()
  } else {
    formData.content = generateDefaultShape()
  }
})

async function loadShape() {
  shape.value = await shaclStore.fetchShape(shapeId.value!)
  if (shape.value) {
    formData.uri = shape.value.uri
    formData.name = shape.value.name
    formData.description = shape.value.description || ''
    formData.targetClass = shape.value.targetClass || ''
    formData.category = shape.value.category || ''
    formData.tags = shape.value.tags || []
    formData.content = shape.value.content
  }
}

function generateDefaultShape(): string {
  return `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <http://example.org/> .

ex:MyShape
  a sh:NodeShape ;
  sh:targetClass ex:MyClass ;
  sh:property [
    sh:path ex:name ;
    sh:datatype xsd:string ;
    sh:minCount 1 ;
    sh:maxCount 1 ;
  ] .
`
}

function goBack() {
  router.push('/shacl')
}

async function validateSyntax() {
  try {
    const result = await shaclStore.validateSyntax(formData.content, 'turtle')
    if (result.valid) {
      alert('Syntax is valid!')
    } else {
      alert('Syntax errors:\n' + result.errors.join('\n'))
    }
  } catch (e: any) {
    alert('Error: ' + e.message)
  }
}

async function saveShape() {
  saving.value = true
  try {
    if (isNew.value) {
      await shaclStore.createShape({
        uri: formData.uri,
        name: formData.name,
        description: formData.description,
        targetClass: formData.targetClass,
        category: formData.category,
        tags: formData.tags,
        content: formData.content,
        contentFormat: 'turtle'
      })
    } else {
      await shaclStore.updateShape(shapeId.value!, {
        uri: formData.uri,
        name: formData.name,
        description: formData.description,
        targetClass: formData.targetClass,
        category: formData.category,
        tags: formData.tags,
        content: formData.content
      })
    }
    router.push('/shacl')
  } finally {
    saving.value = false
  }
}

function addProperty() {
  properties.value.push({
    path: '',
    minCount: 0
  })
}

function removeProperty(index: number) {
  properties.value.splice(index, 1)
}

function movePropertyUp(index: number) {
  if (index > 0) {
    [properties.value[index - 1], properties.value[index]] = [properties.value[index], properties.value[index - 1]]
  }
}

function movePropertyDown(index: number) {
  if (index < properties.value.length - 1) {
    [properties.value[index], properties.value[index + 1]] = [properties.value[index + 1], properties.value[index]]
  }
}

async function generateFromForm() {
  const turtle = await shaclStore.generateTurtle({
    uri: formData.uri,
    targetClass: formData.targetClass,
    properties: properties.value
  })
  formData.content = turtle
  activeTab.value = 0
}

async function runTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await shaclStore.runValidation(shapeId.value || 'temp', testData.value, 'turtle')
  } finally {
    testing.value = false
  }
}

function addPrefix() {
  const prefix = prompt('Enter prefix (e.g., foaf):')
  const uri = prompt('Enter URI (e.g., http://xmlns.com/foaf/0.1/):')
  if (prefix && uri) {
    prefixes.value[prefix] = uri
  }
}

function truncateUri(uri: string): string {
  return uri.length > 35 ? uri.substring(0, 35) + '...' : uri
}

function getSeverity(severity: string): string {
  switch (severity) {
    case 'Violation': return 'danger'
    case 'Warning': return 'warning'
    case 'Info': return 'info'
    default: return 'secondary'
  }
}
</script>

<style scoped>
.shape-editor {
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

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.title-section h1 {
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
}

.editor-content {
  display: flex;
  gap: 1.5rem;
  flex: 1;
  overflow: hidden;
}

.editor-sidebar {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  overflow: auto;
}

.editor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 600;
  font-size: 0.875rem;
}

.prefix-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.prefix-item {
  display: flex;
  gap: 0.5rem;
  font-size: 0.75rem;
}

.prefix-item code {
  color: var(--primary-color);
}

.code-editor-container {
  height: 100%;
}

.code-textarea {
  font-family: monospace;
  font-size: 0.875rem;
  resize: none;
}

.form-builder {
  padding: 1rem;
}

.properties-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.properties-header h3 {
  margin: 0;
}

.empty-properties {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  text-align: center;
  gap: 1rem;
}

.property-card {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  margin-bottom: 1rem;
  overflow: hidden;
}

.property-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: var(--surface-ground);
  border-bottom: 1px solid var(--surface-border);
}

.property-path {
  font-weight: 600;
}

.property-actions {
  display: flex;
  gap: 0.25rem;
}

.property-form {
  padding: 1rem;
}

.form-row {
  display: flex;
  gap: 1rem;
}

.form-row .field {
  flex: 1;
}

.generate-action {
  margin-top: 1.5rem;
  text-align: center;
}

.test-content .field {
  margin-bottom: 1rem;
}

.test-results {
  margin-top: 1.5rem;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.result-header.success {
  background: var(--green-100);
  color: var(--green-700);
}

.result-header.error {
  background: var(--red-100);
  color: var(--red-700);
}

.result-header i {
  font-size: 1.5rem;
}

.violations-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.violation-item {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
}

.violation-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.focus-node {
  font-family: monospace;
  font-size: 0.875rem;
}

.violation-details {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
}
</style>
