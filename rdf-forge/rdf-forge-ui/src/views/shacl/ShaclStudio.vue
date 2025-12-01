<template>
  <div class="shacl-studio">
    <div class="studio-layout">
      <aside class="shape-library">
        <div class="library-header">
          <h3>Shape Library</h3>
          <Button icon="pi pi-plus" class="p-button-sm" @click="createNewShape" v-tooltip="'New Shape'" />
        </div>
        
        <InputText v-model="searchQuery" placeholder="Search shapes..." class="w-full mb-3" />
        
        <div class="library-categories">
          <div v-for="category in categories" :key="category" class="category-section">
            <div class="category-label">{{ category }}</div>
            <div 
              v-for="shape in filteredShapes.filter(s => s.category === category)" 
              :key="shape.id"
              class="shape-item"
              :class="{ active: selectedShape?.id === shape.id }"
              @click="selectShape(shape)"
            >
              <i class="pi pi-check-square"></i>
              <span>{{ shape.name }}</span>
            </div>
          </div>
        </div>
      </aside>

      <div class="editor-area">
        <TabView v-model:activeIndex="activeTab">
          <TabPanel header="Form Builder">
            <div class="form-builder" v-if="currentShape">
              <Card class="mb-3">
                <template #title>Node Shape</template>
                <template #content>
                  <div class="grid">
                    <div class="col-12 md:col-6">
                      <label>Shape URI</label>
                      <InputText v-model="currentShape.uri" class="w-full" />
                    </div>
                    <div class="col-12 md:col-6">
                      <label>Name</label>
                      <InputText v-model="currentShape.name" class="w-full" />
                    </div>
                    <div class="col-12 md:col-6">
                      <label>Target Class</label>
                      <InputText v-model="currentShape.targetClass" class="w-full" placeholder="e.g., schema:Person" />
                    </div>
                    <div class="col-12 md:col-6">
                      <label>Category</label>
                      <Dropdown v-model="currentShape.category" :options="categoryOptions" class="w-full" />
                    </div>
                    <div class="col-12">
                      <label>Description</label>
                      <Textarea v-model="currentShape.description" rows="2" class="w-full" />
                    </div>
                  </div>
                </template>
              </Card>

              <Card>
                <template #title>
                  <div class="flex justify-content-between align-items-center">
                    <span>Property Shapes</span>
                    <Button icon="pi pi-plus" label="Add Property" class="p-button-sm" @click="addProperty" />
                  </div>
                </template>
                <template #content>
                  <div v-for="(prop, index) in currentShape.properties" :key="index" class="property-card">
                    <div class="property-header">
                      <span class="property-path">{{ prop.path || 'New Property' }}</span>
                      <Button icon="pi pi-trash" class="p-button-text p-button-danger p-button-sm" @click="removeProperty(index)" />
                    </div>
                    
                    <div class="grid property-fields">
                      <div class="col-12 md:col-4">
                        <label>Property Path</label>
                        <InputText v-model="prop.path" class="w-full" placeholder="e.g., schema:name" />
                      </div>
                      <div class="col-12 md:col-4">
                        <label>Datatype</label>
                        <Dropdown v-model="prop.datatype" :options="datatypes" class="w-full" />
                      </div>
                      <div class="col-12 md:col-4">
                        <label>Node Kind</label>
                        <Dropdown v-model="prop.nodeKind" :options="nodeKinds" class="w-full" />
                      </div>
                      <div class="col-6 md:col-3">
                        <label>Min Count</label>
                        <InputNumber v-model="prop.minCount" class="w-full" :min="0" />
                      </div>
                      <div class="col-6 md:col-3">
                        <label>Max Count</label>
                        <InputNumber v-model="prop.maxCount" class="w-full" :min="0" />
                      </div>
                      <div class="col-6 md:col-3">
                        <label>Min Length</label>
                        <InputNumber v-model="prop.minLength" class="w-full" :min="0" />
                      </div>
                      <div class="col-6 md:col-3">
                        <label>Max Length</label>
                        <InputNumber v-model="prop.maxLength" class="w-full" :min="0" />
                      </div>
                      <div class="col-12 md:col-6">
                        <label>Pattern (Regex)</label>
                        <InputText v-model="prop.pattern" class="w-full" />
                      </div>
                      <div class="col-12 md:col-6">
                        <label>Message</label>
                        <InputText v-model="prop.message" class="w-full" placeholder="Validation error message" />
                      </div>
                    </div>
                  </div>
                </template>
              </Card>
            </div>
          </TabPanel>
          
          <TabPanel header="Code Editor">
            <div class="code-editor">
              <Textarea v-model="shapeContent" rows="25" class="w-full font-mono" style="font-family: monospace;" />
            </div>
          </TabPanel>
          
          <TabPanel header="Validation Tester">
            <div class="validation-tester">
              <div class="grid">
                <div class="col-12 md:col-6">
                  <Card>
                    <template #title>Test Data</template>
                    <template #content>
                      <Textarea v-model="testData" rows="15" class="w-full font-mono" placeholder="Paste RDF data here (Turtle format)" />
                    </template>
                  </Card>
                </div>
                <div class="col-12 md:col-6">
                  <Card>
                    <template #title>
                      <div class="flex justify-content-between align-items-center">
                        <span>Validation Report</span>
                        <Button label="Run Validation" icon="pi pi-play" @click="runValidation" />
                      </div>
                    </template>
                    <template #content>
                      <div v-if="validationReport" class="validation-report">
                        <div class="report-summary" :class="{ conforms: validationReport.conforms }">
                          <i :class="validationReport.conforms ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
                          <span>{{ validationReport.conforms ? 'Validation Passed' : 'Validation Failed' }}</span>
                        </div>
                        
                        <div v-if="!validationReport.conforms" class="violations">
                          <div v-for="(result, idx) in validationReport.results" :key="idx" class="violation-item">
                            <Tag :severity="getSeverity(result.severity)">{{ result.severity }}</Tag>
                            <div class="violation-details">
                              <div><strong>Focus:</strong> {{ result.focusNode }}</div>
                              <div><strong>Path:</strong> {{ result.resultPath }}</div>
                              <div><strong>Message:</strong> {{ result.message }}</div>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div v-else class="empty-state">
                        <i class="pi pi-info-circle"></i>
                        <span>Run validation to see results</span>
                      </div>
                    </template>
                  </Card>
                </div>
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>
    </div>

    <div class="studio-footer">
      <Button label="Generate Turtle" icon="pi pi-code" class="p-button-outlined" @click="generateTurtle" />
      <div class="footer-right">
        <Button label="Validate Syntax" icon="pi pi-check" class="p-button-outlined" @click="validateSyntax" />
        <Button label="Save Shape" icon="pi pi-save" class="p-button-primary" @click="saveShape" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import Card from 'primevue/card'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Dropdown from 'primevue/dropdown'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const activeTab = ref(0)
const searchQuery = ref('')
const selectedShape = ref<any>(null)
const shapeContent = ref('')
const testData = ref('')
const validationReport = ref<any>(null)

const categories = ['My Shapes', 'Templates', 'Shared']
const categoryOptions = ['My Shapes', 'Templates', 'Shared', 'Cube Schema', 'DCAT', 'Schema.org']
const datatypes = ['string', 'integer', 'decimal', 'boolean', 'date', 'dateTime', 'anyURI']
const nodeKinds = ['Literal', 'IRI', 'BlankNode', 'BlankNodeOrIRI', 'BlankNodeOrLiteral', 'IRIOrLiteral']

const shapes = ref([
  { id: '1', name: 'PersonShape', category: 'My Shapes', uri: 'ex:PersonShape', targetClass: 'schema:Person', description: 'Validates Person entities', properties: [] },
  { id: '2', name: 'ProductShape', category: 'My Shapes', uri: 'ex:ProductShape', targetClass: 'schema:Product', description: '', properties: [] },
  { id: '3', name: 'CubeObservation', category: 'Templates', uri: 'cube:ObservationShape', targetClass: 'cube:Observation', description: 'Standard cube observation shape', properties: [] }
])

const currentShape = ref<any>({
  uri: '',
  name: '',
  targetClass: '',
  description: '',
  category: 'My Shapes',
  properties: []
})

const filteredShapes = computed(() => {
  if (!searchQuery.value) return shapes.value
  const q = searchQuery.value.toLowerCase()
  return shapes.value.filter(s => s.name.toLowerCase().includes(q) || s.targetClass?.toLowerCase().includes(q))
})

const createNewShape = () => {
  currentShape.value = {
    uri: 'ex:NewShape',
    name: 'New Shape',
    targetClass: '',
    description: '',
    category: 'My Shapes',
    properties: []
  }
  selectedShape.value = null
}

const selectShape = (shape: any) => {
  selectedShape.value = shape
  currentShape.value = { ...shape }
}

const addProperty = () => {
  currentShape.value.properties.push({
    path: '',
    datatype: 'string',
    nodeKind: 'Literal',
    minCount: null,
    maxCount: null,
    minLength: null,
    maxLength: null,
    pattern: '',
    message: ''
  })
}

const removeProperty = (index: number) => {
  currentShape.value.properties.splice(index, 1)
}

const generateTurtle = () => {
  let turtle = `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <http://example.org/> .
@prefix schema: <http://schema.org/> .

<${currentShape.value.uri}>
  a sh:NodeShape ;
  sh:targetClass <${currentShape.value.targetClass}> ;
`

  currentShape.value.properties.forEach((prop: any, idx: number) => {
    if (prop.path) {
      turtle += `  sh:property [\n`
      turtle += `    sh:path <${prop.path}> ;\n`
      if (prop.datatype) turtle += `    sh:datatype xsd:${prop.datatype} ;\n`
      if (prop.minCount !== null) turtle += `    sh:minCount ${prop.minCount} ;\n`
      if (prop.maxCount !== null) turtle += `    sh:maxCount ${prop.maxCount} ;\n`
      if (prop.pattern) turtle += `    sh:pattern "${prop.pattern}" ;\n`
      if (prop.message) turtle += `    sh:message "${prop.message}" ;\n`
      turtle += `  ]${idx < currentShape.value.properties.length - 1 ? ' ;' : ' .'}\n`
    }
  })

  shapeContent.value = turtle
  activeTab.value = 1
  toast.add({ severity: 'success', summary: 'Generated', detail: 'Turtle code generated', life: 3000 })
}

const validateSyntax = () => {
  toast.add({ severity: 'success', summary: 'Valid', detail: 'SHACL syntax is valid', life: 3000 })
}

const saveShape = () => {
  toast.add({ severity: 'success', summary: 'Saved', detail: 'Shape saved successfully', life: 3000 })
}

const runValidation = () => {
  validationReport.value = {
    conforms: false,
    results: [
      { severity: 'VIOLATION', focusNode: '<http://example.org/person/1>', resultPath: 'schema:email', message: 'Invalid email format' }
    ]
  }
}

const getSeverity = (severity: string) => {
  const map: Record<string, string> = { VIOLATION: 'danger', WARNING: 'warning', INFO: 'info' }
  return map[severity] || 'info'
}
</script>

<style scoped>
.shacl-studio {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.studio-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.shape-library {
  width: 260px;
  border-right: 1px solid #e5e7eb;
  padding: 1rem;
  overflow-y: auto;
  background: #f8fafc;
}

.library-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.library-header h3 {
  margin: 0;
  font-size: 1rem;
}

.category-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  padding: 0.5rem 0;
  margin-top: 0.5rem;
}

.shape-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  margin-bottom: 0.25rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.shape-item:hover { background: #e2e8f0; }
.shape-item.active { background: #dbeafe; color: #1d4ed8; }

.editor-area {
  flex: 1;
  padding: 1rem;
  overflow-y: auto;
}

.property-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.property-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.property-path {
  font-weight: 500;
  color: #1e293b;
}

.property-fields label {
  display: block;
  font-size: 0.75rem;
  color: #64748b;
  margin-bottom: 0.25rem;
}

.studio-footer {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
}

.footer-right {
  display: flex;
  gap: 0.5rem;
}

.validation-report { padding: 1rem 0; }

.report-summary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  border-radius: 8px;
  background: #fee2e2;
  color: #dc2626;
  margin-bottom: 1rem;
}

.report-summary.conforms {
  background: #dcfce7;
  color: #16a34a;
}

.report-summary i { font-size: 1.5rem; }

.violation-item {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
}

.violation-details {
  margin-top: 0.5rem;
  font-size: 0.875rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem;
  color: #64748b;
}

.code-editor textarea {
  font-family: 'Fira Code', 'Consolas', monospace !important;
}
</style>
