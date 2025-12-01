<template>
  <div class="pipeline-designer">
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <InputText v-model="pipelineName" placeholder="Pipeline Name" class="pipeline-name-input" />
        <Dropdown v-model="selectedFormat" :options="formats" placeholder="Format" class="w-8rem" />
      </div>
      <div class="toolbar-right">
        <Button label="Validate" icon="pi pi-check" class="p-button-outlined" @click="validatePipeline" />
        <Button label="Preview" icon="pi pi-eye" class="p-button-outlined" @click="previewPipeline" />
        <Button label="Save" icon="pi pi-save" class="p-button-primary" @click="savePipeline" />
        <Button label="Run" icon="pi pi-play" class="p-button-success" @click="runPipeline" />
      </div>
    </div>

    <div class="designer-content">
      <aside class="operation-palette">
        <div class="palette-header">
          <span>Operations</span>
          <InputText v-model="searchQuery" placeholder="Search..." class="p-inputtext-sm" />
        </div>
        
        <div class="operation-categories">
          <div v-for="category in filteredOperations" :key="category.type" class="category">
            <div class="category-header" @click="toggleCategory(category.type)">
              <i :class="getCategoryIcon(category.type)"></i>
              <span>{{ category.type }}</span>
              <i :class="expandedCategories.includes(category.type) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"></i>
            </div>
            <div v-if="expandedCategories.includes(category.type)" class="category-operations">
              <div 
                v-for="op in category.operations" 
                :key="op.id" 
                class="operation-item"
                draggable="true"
                @dragstart="onDragStart($event, op)"
              >
                <i class="pi pi-circle"></i>
                <span>{{ op.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <div class="canvas-container" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 1, x: 0, y: 0 }"
          :min-zoom="0.2"
          :max-zoom="4"
          fit-view-on-init
          @node-click="onNodeClick"
          @connect="onConnect"
        >
          <Background />
          <Controls />
          <MiniMap />
          
          <template #node-operation="nodeProps">
            <div class="operation-node" :class="nodeProps.data.type.toLowerCase()">
              <div class="node-header">
                <i :class="getOperationIcon(nodeProps.data.operationType)"></i>
                <span>{{ nodeProps.data.label }}</span>
              </div>
              <Handle type="target" :position="Position.Left" />
              <Handle type="source" :position="Position.Right" />
            </div>
          </template>
        </VueFlow>
      </div>

      <aside class="properties-panel" v-if="selectedNode">
        <div class="panel-header">
          <span>Properties</span>
          <Button icon="pi pi-times" class="p-button-text p-button-sm" @click="selectedNode = null" />
        </div>
        
        <div class="panel-content">
          <div class="property-group">
            <label>Step Name</label>
            <InputText v-model="selectedNode.data.label" class="w-full" />
          </div>
          
          <div class="property-group">
            <label>Operation</label>
            <Dropdown 
              v-model="selectedNode.data.operationType" 
              :options="allOperations" 
              optionLabel="name" 
              optionValue="id"
              class="w-full" 
            />
          </div>

          <Divider />
          
          <div class="property-group" v-for="(param, key) in selectedNode.data.parameters" :key="key">
            <label>{{ key }}</label>
            <InputText v-model="selectedNode.data.parameters[key]" class="w-full" />
          </div>
          
          <Button label="Delete Step" icon="pi pi-trash" class="p-button-danger w-full mt-3" @click="deleteNode" />
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { VueFlow, useVueFlow, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { Handle } from '@vue-flow/core'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Button from 'primevue/button'
import Divider from 'primevue/divider'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const { addNodes, addEdges, removeNodes } = useVueFlow()

const pipelineName = ref('New Pipeline')
const selectedFormat = ref('YAML')
const formats = ['YAML', 'TURTLE', 'JSON']
const searchQuery = ref('')
const expandedCategories = ref(['SOURCE', 'TRANSFORM'])
const selectedNode = ref<any>(null)

const nodes = ref<any[]>([])
const edges = ref<any[]>([])

const operationCatalog = ref([
  {
    type: 'SOURCE',
    operations: [
      { id: 'load-csv', name: 'Load CSV', parameters: { file: '', delimiter: ',', hasHeader: true } },
      { id: 'load-json', name: 'Load JSON', parameters: { file: '', jsonPath: '' } },
      { id: 'load-parquet', name: 'Load Parquet', parameters: { file: '' } },
      { id: 'http-get', name: 'HTTP GET', parameters: { url: '', headers: {} } },
      { id: 's3-fetch', name: 'S3 Fetch', parameters: { bucket: '', key: '' } }
    ]
  },
  {
    type: 'TRANSFORM',
    operations: [
      { id: 'map-to-rdf', name: 'Map to RDF', parameters: { baseUri: '', propertyMappings: {} } },
      { id: 'filter', name: 'Filter', parameters: { condition: '' } },
      { id: 'merge', name: 'Merge', parameters: {} },
      { id: 'enrich', name: 'Enrich', parameters: { lookupSource: '' } }
    ]
  },
  {
    type: 'CUBE',
    operations: [
      { id: 'create-observation', name: 'Create Observation', parameters: { cubeUri: '', dimensions: {}, measures: {} } },
      { id: 'map-dimensions', name: 'Map Dimensions', parameters: { mappings: {} } }
    ]
  },
  {
    type: 'VALIDATION',
    operations: [
      { id: 'validate-shacl', name: 'Validate SHACL', parameters: { shapeFile: '', onViolation: 'error' } },
      { id: 'validate-cube', name: 'Validate Cube', parameters: { strict: true } }
    ]
  },
  {
    type: 'OUTPUT',
    operations: [
      { id: 'graph-store-put', name: 'Graph Store PUT', parameters: { endpoint: '', graph: '' } },
      { id: 'write-file', name: 'Write File', parameters: { destination: '', format: 'ntriples' } }
    ]
  }
])

const allOperations = computed(() => operationCatalog.value.flatMap(c => c.operations))

const filteredOperations = computed(() => {
  if (!searchQuery.value) return operationCatalog.value
  
  const query = searchQuery.value.toLowerCase()
  return operationCatalog.value.map(category => ({
    ...category,
    operations: category.operations.filter(op => 
      op.name.toLowerCase().includes(query) || op.id.includes(query)
    )
  })).filter(c => c.operations.length > 0)
})

const getCategoryIcon = (type: string) => {
  const icons: Record<string, string> = {
    SOURCE: 'pi pi-download',
    TRANSFORM: 'pi pi-cog',
    CUBE: 'pi pi-box',
    VALIDATION: 'pi pi-check-square',
    OUTPUT: 'pi pi-upload'
  }
  return icons[type] || 'pi pi-circle'
}

const getOperationIcon = (opType: string) => {
  return 'pi pi-circle-fill'
}

const toggleCategory = (type: string) => {
  const idx = expandedCategories.value.indexOf(type)
  if (idx >= 0) {
    expandedCategories.value.splice(idx, 1)
  } else {
    expandedCategories.value.push(type)
  }
}

let nodeId = 0
const onDragStart = (event: DragEvent, operation: any) => {
  event.dataTransfer?.setData('application/json', JSON.stringify(operation))
}

const onDrop = (event: DragEvent) => {
  const data = event.dataTransfer?.getData('application/json')
  if (!data) return
  
  const operation = JSON.parse(data)
  const canvasRect = (event.target as HTMLElement).getBoundingClientRect()
  
  const newNode = {
    id: `node-${++nodeId}`,
    type: 'operation',
    position: { x: event.clientX - canvasRect.left - 75, y: event.clientY - canvasRect.top - 25 },
    data: {
      label: operation.name,
      operationType: operation.id,
      type: operationCatalog.value.find(c => c.operations.some(o => o.id === operation.id))?.type || 'TRANSFORM',
      parameters: { ...operation.parameters }
    }
  }
  
  nodes.value = [...nodes.value, newNode]
}

const onNodeClick = ({ node }: any) => {
  selectedNode.value = node
}

const onConnect = (params: any) => {
  edges.value = [...edges.value, { ...params, id: `edge-${params.source}-${params.target}` }]
}

const deleteNode = () => {
  if (selectedNode.value) {
    nodes.value = nodes.value.filter(n => n.id !== selectedNode.value.id)
    edges.value = edges.value.filter(e => e.source !== selectedNode.value.id && e.target !== selectedNode.value.id)
    selectedNode.value = null
  }
}

const validatePipeline = () => {
  toast.add({ severity: 'success', summary: 'Valid', detail: 'Pipeline structure is valid', life: 3000 })
}

const previewPipeline = () => {
  toast.add({ severity: 'info', summary: 'Preview', detail: 'Preview mode coming soon', life: 3000 })
}

const savePipeline = () => {
  toast.add({ severity: 'success', summary: 'Saved', detail: 'Pipeline saved successfully', life: 3000 })
}

const runPipeline = () => {
  toast.add({ severity: 'info', summary: 'Running', detail: 'Pipeline execution started', life: 3000 })
}
</script>

<style scoped>
.pipeline-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.toolbar-left, .toolbar-right {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.pipeline-name-input {
  font-weight: 500;
  width: 200px;
}

.designer-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.operation-palette {
  width: 240px;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.palette-header {
  padding: 0.75rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.palette-header span {
  font-weight: 600;
  color: #1e293b;
}

.operation-categories {
  flex: 1;
  overflow-y: auto;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  cursor: pointer;
  font-weight: 500;
  color: #475569;
  border-bottom: 1px solid #e5e7eb;
}

.category-header:hover {
  background: #e2e8f0;
}

.category-operations {
  padding: 0.5rem;
}

.operation-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  margin-bottom: 0.25rem;
  border-radius: 4px;
  cursor: grab;
  font-size: 0.875rem;
  color: #334155;
  transition: all 0.2s;
}

.operation-item:hover {
  background: #dbeafe;
}

.operation-item:active {
  cursor: grabbing;
}

.canvas-container {
  flex: 1;
  background: #f1f5f9;
}

.properties-panel {
  width: 280px;
  border-left: 1px solid #e5e7eb;
  background: white;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
  font-weight: 600;
}

.panel-content {
  padding: 1rem;
}

.property-group {
  margin-bottom: 1rem;
}

.property-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  color: #64748b;
}

.operation-node {
  background: white;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  min-width: 150px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.operation-node.source { border-color: #3b82f6; }
.operation-node.transform { border-color: #f59e0b; }
.operation-node.cube { border-color: #8b5cf6; }
.operation-node.validation { border-color: #10b981; }
.operation-node.output { border-color: #ef4444; }

.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 500;
}
</style>
