<template>
  <div class="job-list">
    <div class="header">
      <h1>Jobs</h1>
      <div class="actions">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="searchQuery" placeholder="Search jobs..." />
        </span>
        <Dropdown v-model="statusFilter" :options="statusOptions" optionLabel="label" optionValue="value" placeholder="Status" />
        <Calendar v-model="dateRange" selectionMode="range" placeholder="Date range" :showIcon="true" />
        <Button label="Refresh" icon="pi pi-refresh" class="p-button-outlined" @click="loadJobs" :loading="loading" />
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-value">{{ runningCount }}</span>
        <span class="stat-label">Running</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ completedToday }}</span>
        <span class="stat-label">Completed Today</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ failedToday }}</span>
        <span class="stat-label">Failed Today</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ avgDuration }}</span>
        <span class="stat-label">Avg Duration</span>
      </div>
    </div>

    <DataTable
      :value="filteredJobs"
      :loading="loading"
      :paginator="true"
      :rows="15"
      :rowsPerPageOptions="[15, 30, 50]"
      dataKey="id"
      responsiveLayout="scroll"
      class="p-datatable-sm"
      @row-click="openJob"
    >
      <template #empty>
        <div class="empty-state">
          <i class="pi pi-clock" style="font-size: 3rem; color: var(--text-color-secondary)" />
          <p>No jobs found</p>
        </div>
      </template>

      <Column field="id" header="Job ID" style="width: 150px">
        <template #body="{ data }">
          <code class="job-id">{{ data.id.substring(0, 8) }}</code>
        </template>
      </Column>

      <Column field="pipelineName" header="Pipeline" style="min-width: 200px">
        <template #body="{ data }">
          <div class="pipeline-info">
            <span class="pipeline-name">{{ data.pipelineName }}</span>
            <span class="pipeline-version">v{{ data.pipelineVersion }}</span>
          </div>
        </template>
      </Column>

      <Column field="status" header="Status" style="width: 130px">
        <template #body="{ data }">
          <Tag :value="data.status" :severity="getStatusSeverity(data.status)" :icon="getStatusIcon(data.status)" />
        </template>
      </Column>

      <Column field="progress" header="Progress" style="width: 150px">
        <template #body="{ data }">
          <ProgressBar v-if="data.status === 'running'" :value="data.progress" :showValue="true" style="height: 8px" />
          <span v-else-if="data.status === 'completed'" class="text-green-500">100%</span>
          <span v-else-if="data.status === 'failed'" class="text-red-500">Failed</span>
          <span v-else class="text-muted">-</span>
        </template>
      </Column>

      <Column field="metrics" header="Metrics" style="width: 180px">
        <template #body="{ data }">
          <div v-if="data.metrics" class="metrics">
            <span><i class="pi pi-database" /> {{ formatNumber(data.metrics.rowsProcessed) }} rows</span>
            <span><i class="pi pi-share-alt" /> {{ formatNumber(data.metrics.quadsGenerated) }} quads</span>
          </div>
          <span v-else class="text-muted">-</span>
        </template>
      </Column>

      <Column field="startedAt" header="Started" :sortable="true" style="width: 150px">
        <template #body="{ data }">
          <span v-if="data.startedAt">{{ formatDate(data.startedAt) }}</span>
          <span v-else class="text-muted">Pending</span>
        </template>
      </Column>

      <Column field="duration" header="Duration" style="width: 100px">
        <template #body="{ data }">
          <span v-if="data.duration">{{ formatDuration(data.duration) }}</span>
          <span v-else-if="data.status === 'running'" class="running-timer">{{ getRunningTime(data.startedAt) }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </Column>

      <Column field="triggeredBy" header="Trigger" style="width: 100px">
        <template #body="{ data }">
          <Tag :value="data.triggeredBy" :severity="getTriggerSeverity(data.triggeredBy)" />
        </template>
      </Column>

      <Column style="width: 120px">
        <template #body="{ data }">
          <div class="row-actions">
            <Button v-if="data.status === 'running'" icon="pi pi-stop" class="p-button-rounded p-button-danger p-button-text" @click.stop="cancelJob(data)" v-tooltip="'Cancel'" />
            <Button v-if="data.status === 'failed'" icon="pi pi-refresh" class="p-button-rounded p-button-warning p-button-text" @click.stop="retryJob(data)" v-tooltip="'Retry'" />
            <Button icon="pi pi-file" class="p-button-rounded p-button-secondary p-button-text" @click.stop="viewLogs(data)" v-tooltip="'Logs'" />
            <Button icon="pi pi-external-link" class="p-button-rounded p-button-text" @click.stop="openJob({ data })" v-tooltip="'Details'" />
          </div>
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Calendar from 'primevue/calendar'
import Tag from 'primevue/tag'
import ProgressBar from 'primevue/progressbar'
import { useJobStore } from '@/stores/job'

interface JobMetrics {
  rowsProcessed: number
  quadsGenerated: number
  duration: number
}

interface Job {
  id: string
  pipelineId: string
  pipelineName: string
  pipelineVersion: number
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  metrics?: JobMetrics
  startedAt?: Date
  completedAt?: Date
  duration?: number
  triggeredBy: 'manual' | 'schedule' | 'api'
  errorMessage?: string
}

const router = useRouter()
const jobStore = useJobStore()

const loading = ref(false)
const searchQuery = ref('')
const statusFilter = ref<string | null>(null)
const dateRange = ref<Date[] | null>(null)
const jobs = ref<Job[]>([])
let refreshInterval: number

const statusOptions = [
  { label: 'All', value: null },
  { label: 'Running', value: 'running' },
  { label: 'Completed', value: 'completed' },
  { label: 'Failed', value: 'failed' },
  { label: 'Pending', value: 'pending' },
  { label: 'Cancelled', value: 'cancelled' }
]

const filteredJobs = computed(() => {
  let result = jobs.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(j =>
      j.id.toLowerCase().includes(query) ||
      j.pipelineName.toLowerCase().includes(query)
    )
  }
  if (statusFilter.value) {
    result = result.filter(j => j.status === statusFilter.value)
  }
  return result
})

const runningCount = computed(() => jobs.value.filter(j => j.status === 'running').length)
const completedToday = computed(() => {
  const today = new Date().toDateString()
  return jobs.value.filter(j => j.status === 'completed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length
})
const failedToday = computed(() => {
  const today = new Date().toDateString()
  return jobs.value.filter(j => j.status === 'failed' && j.completedAt && new Date(j.completedAt).toDateString() === today).length
})
const avgDuration = computed(() => {
  const completed = jobs.value.filter(j => j.duration)
  if (completed.length === 0) return '-'
  const avg = completed.reduce((sum, j) => sum + (j.duration || 0), 0) / completed.length
  return formatDuration(avg)
})

onMounted(async () => {
  await loadJobs()
  refreshInterval = window.setInterval(loadJobs, 5000)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})

async function loadJobs() {
  loading.value = true
  try {
    jobs.value = await jobStore.fetchJobs()
  } finally {
    loading.value = false
  }
}

function openJob(event: { data: Job }) {
  router.push(`/jobs/${event.data.id}`)
}

async function cancelJob(job: Job) {
  await jobStore.cancelJob(job.id)
  await loadJobs()
}

async function retryJob(job: Job) {
  await jobStore.retryJob(job.id)
  await loadJobs()
}

function viewLogs(job: Job) {
  router.push(`/jobs/${job.id}/logs`)
}

function getStatusSeverity(status: string): string {
  switch (status) {
    case 'running': return 'info'
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'cancelled': return 'warning'
    default: return 'secondary'
  }
}

function getStatusIcon(status: string): string {
  switch (status) {
    case 'running': return 'pi pi-spin pi-spinner'
    case 'completed': return 'pi pi-check'
    case 'failed': return 'pi pi-times'
    case 'cancelled': return 'pi pi-ban'
    default: return 'pi pi-clock'
  }
}

function getTriggerSeverity(trigger: string): string {
  switch (trigger) {
    case 'manual': return 'info'
    case 'schedule': return 'success'
    case 'api': return 'warning'
    default: return 'secondary'
  }
}

function formatDate(date: Date): string {
  return new Date(date).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  if (ms < 3600000) return `${Math.round(ms / 60000)}m`
  return `${Math.round(ms / 3600000)}h`
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}

function getRunningTime(startedAt?: Date): string {
  if (!startedAt) return '-'
  const elapsed = Date.now() - new Date(startedAt).getTime()
  return formatDuration(elapsed)
}
</script>

<style scoped>
.job-list {
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

.stats-bar {
  display: flex;
  gap: 2rem;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
}

.stat-label {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.job-id {
  background: var(--surface-200);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.875rem;
}

.pipeline-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.pipeline-name {
  font-weight: 600;
}

.pipeline-version {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  background: var(--surface-200);
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
}

.metrics {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
}

.metrics i {
  margin-right: 0.25rem;
  font-size: 0.75rem;
}

.row-actions {
  display: flex;
  gap: 0.25rem;
}

.running-timer {
  color: var(--primary-color);
  font-weight: 600;
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
</style>
