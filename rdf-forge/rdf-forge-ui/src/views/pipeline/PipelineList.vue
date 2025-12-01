<template>
  <div class="pipeline-list">
    <div class="header">
      <h1>Pipelines</h1>
      <div class="actions">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="searchQuery" placeholder="Search pipelines..." />
        </span>
        <Dropdown v-model="statusFilter" :options="statusOptions" optionLabel="label" optionValue="value" placeholder="Filter by status" />
        <Button label="New Pipeline" icon="pi pi-plus" @click="createPipeline" />
      </div>
    </div>

    <DataTable
      :value="filteredPipelines"
      :loading="loading"
      :paginator="true"
      :rows="10"
      :rowsPerPageOptions="[10, 25, 50]"
      dataKey="id"
      :globalFilterFields="['name', 'description', 'tags']"
      responsiveLayout="scroll"
      class="p-datatable-sm"
      @row-click="openPipeline"
    >
      <template #empty>
        <div class="empty-state">
          <i class="pi pi-sitemap" style="font-size: 3rem; color: var(--text-color-secondary)" />
          <p>No pipelines found</p>
          <Button label="Create your first pipeline" icon="pi pi-plus" @click="createPipeline" />
        </div>
      </template>

      <Column field="name" header="Name" :sortable="true" style="min-width: 200px">
        <template #body="{ data }">
          <div class="pipeline-name">
            <span class="name">{{ data.name }}</span>
            <span class="description">{{ data.description }}</span>
          </div>
        </template>
      </Column>

      <Column field="status" header="Status" :sortable="true" style="width: 120px">
        <template #body="{ data }">
          <Tag :value="data.status" :severity="getStatusSeverity(data.status)" />
        </template>
      </Column>

      <Column field="stepsCount" header="Steps" :sortable="true" style="width: 80px">
        <template #body="{ data }">
          <span class="step-count">{{ data.stepsCount }}</span>
        </template>
      </Column>

      <Column field="lastRun" header="Last Run" :sortable="true" style="width: 150px">
        <template #body="{ data }">
          <span v-if="data.lastRun">{{ formatDate(data.lastRun) }}</span>
          <span v-else class="text-muted">Never</span>
        </template>
      </Column>

      <Column field="tags" header="Tags" style="width: 200px">
        <template #body="{ data }">
          <div class="tags">
            <Tag v-for="tag in data.tags" :key="tag" :value="tag" severity="info" class="mr-1" />
          </div>
        </template>
      </Column>

      <Column field="updatedAt" header="Updated" :sortable="true" style="width: 150px">
        <template #body="{ data }">
          {{ formatDate(data.updatedAt) }}
        </template>
      </Column>

      <Column style="width: 120px">
        <template #body="{ data }">
          <div class="row-actions">
            <Button icon="pi pi-play" class="p-button-rounded p-button-success p-button-text" @click.stop="runPipeline(data)" v-tooltip="'Run'" />
            <Button icon="pi pi-copy" class="p-button-rounded p-button-secondary p-button-text" @click.stop="duplicatePipeline(data)" v-tooltip="'Duplicate'" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-danger p-button-text" @click.stop="confirmDelete(data)" v-tooltip="'Delete'" />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="deleteDialogVisible" header="Confirm Delete" :modal="true" :style="{ width: '400px' }">
      <div class="confirmation-content">
        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem; color: var(--yellow-500)" />
        <span>Are you sure you want to delete <strong>{{ selectedPipeline?.name }}</strong>?</span>
      </div>
      <template #footer>
        <Button label="Cancel" class="p-button-text" @click="deleteDialogVisible = false" />
        <Button label="Delete" class="p-button-danger" @click="deletePipeline" :loading="deleting" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import { usePipelineStore } from '@/stores/pipeline'

interface Pipeline {
  id: string
  name: string
  description: string
  status: string
  stepsCount: number
  lastRun?: Date
  tags: string[]
  updatedAt: Date
}

const router = useRouter()
const pipelineStore = usePipelineStore()

const loading = ref(false)
const searchQuery = ref('')
const statusFilter = ref<string | null>(null)
const deleteDialogVisible = ref(false)
const selectedPipeline = ref<Pipeline | null>(null)
const deleting = ref(false)

const statusOptions = [
  { label: 'All', value: null },
  { label: 'Active', value: 'active' },
  { label: 'Draft', value: 'draft' },
  { label: 'Archived', value: 'archived' }
]

const pipelines = ref<Pipeline[]>([])

const filteredPipelines = computed(() => {
  let result = pipelines.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(p => 
      p.name.toLowerCase().includes(query) ||
      p.description.toLowerCase().includes(query) ||
      p.tags.some(t => t.toLowerCase().includes(query))
    )
  }
  if (statusFilter.value) {
    result = result.filter(p => p.status === statusFilter.value)
  }
  return result
})

onMounted(async () => {
  await loadPipelines()
})

async function loadPipelines() {
  loading.value = true
  try {
    pipelines.value = await pipelineStore.fetchPipelines()
  } finally {
    loading.value = false
  }
}

function createPipeline() {
  router.push('/pipelines/new')
}

function openPipeline(event: { data: Pipeline }) {
  router.push(`/pipelines/${event.data.id}`)
}

async function runPipeline(pipeline: Pipeline) {
  await pipelineStore.runPipeline(pipeline.id)
  router.push('/jobs')
}

async function duplicatePipeline(pipeline: Pipeline) {
  await pipelineStore.duplicatePipeline(pipeline.id)
  await loadPipelines()
}

function confirmDelete(pipeline: Pipeline) {
  selectedPipeline.value = pipeline
  deleteDialogVisible.value = true
}

async function deletePipeline() {
  if (!selectedPipeline.value) return
  deleting.value = true
  try {
    await pipelineStore.deletePipeline(selectedPipeline.value.id)
    await loadPipelines()
    deleteDialogVisible.value = false
  } finally {
    deleting.value = false
  }
}

function getStatusSeverity(status: string): string {
  switch (status) {
    case 'active': return 'success'
    case 'draft': return 'warning'
    case 'archived': return 'secondary'
    default: return 'info'
  }
}

function formatDate(date: Date): string {
  return new Date(date).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped>
.pipeline-list {
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

.pipeline-name {
  display: flex;
  flex-direction: column;
}

.pipeline-name .name {
  font-weight: 600;
}

.pipeline-name .description {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.step-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-200);
  border-radius: 50%;
  width: 2rem;
  height: 2rem;
  font-weight: 600;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.row-actions {
  display: flex;
  gap: 0.25rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  gap: 1rem;
}

.text-muted {
  color: var(--text-color-secondary);
}

.confirmation-content {
  display: flex;
  align-items: center;
}
</style>
