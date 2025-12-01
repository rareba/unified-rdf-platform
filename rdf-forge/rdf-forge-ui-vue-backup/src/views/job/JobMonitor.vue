<template>
  <div class="job-monitor">
    <div class="header">
      <div class="header-left">
        <Button icon="pi pi-arrow-left" class="p-button-text" @click="goBack" />
        <div class="job-title">
          <h1>Job {{ jobId.substring(0, 8) }}</h1>
          <Skeleton v-if="loading" width="6rem" height="2rem"></Skeleton>
          <Tag v-else :value="job?.status" :severity="getStatusSeverity(job?.status)" :icon="getStatusIcon(job?.status)" />
        </div>
      </div>
      <div class="header-actions">
        <template v-if="!loading">
          <Button v-if="job?.status === 'running'" label="Cancel" icon="pi pi-stop" class="p-button-danger" @click="cancelJob" />
          <Button v-if="job?.status === 'failed'" label="Retry" icon="pi pi-refresh" @click="retryJob" />
          <Button label="View Pipeline" icon="pi pi-sitemap" class="p-button-outlined" @click="viewPipeline" />
        </template>
        <Skeleton v-else width="200px" height="2.5rem"></Skeleton>
      </div>
    </div>

    <div class="monitor-content">
      <div class="main-panel">
        <div class="progress-section" v-if="job?.status === 'running'">
          <div class="progress-header">
            <span>Progress</span>
            <span>{{ job.progress }}%</span>
          </div>
          <ProgressBar :value="job.progress" style="height: 12px" />
          <div class="progress-details">
            <span>Step {{ currentStep }} of {{ totalSteps }}</span>
            <span>{{ currentStepName }}</span>
          </div>
        </div>

        <TabView>
          <TabPanel header="Logs">
            <div class="logs-toolbar">
              <Dropdown v-model="logLevel" :options="logLevelOptions" optionLabel="label" optionValue="value" placeholder="Log Level" />
              <span class="p-input-icon-left">
                <i class="pi pi-search" />
                <InputText v-model="logSearch" placeholder="Search logs..." />
              </span>
              <div class="spacer" />
              <ToggleButton v-model="autoScroll" onLabel="Auto-scroll" offLabel="Auto-scroll" onIcon="pi pi-check" offIcon="pi pi-times" />
              <Button icon="pi pi-download" class="p-button-text" @click="downloadLogs" v-tooltip="'Download logs'" />
            </div>
            <div class="logs-container" ref="logsContainer">
              <div v-for="log in filteredLogs" :key="log.id" :class="['log-entry', `log-${log.level}`]">
                <span class="log-timestamp">{{ formatTimestamp(log.timestamp) }}</span>
                <span class="log-level">{{ log.level.toUpperCase() }}</span>
                <span class="log-step" v-if="log.step">[{{ log.step }}]</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
              <div v-if="logs.length === 0" class="logs-empty">
                <i class="pi pi-file" />
                <span>No logs available yet</span>
              </div>
            </div>
          </TabPanel>

          <TabPanel header="Pipeline Steps">
            <div class="steps-timeline">
              <div v-for="(step, index) in pipelineSteps" :key="step.id" :class="['step-item', step.status]">
                <div class="step-connector" v-if="index < pipelineSteps.length - 1" />
                <div class="step-icon">
                  <i :class="getStepIcon(step.status)" />
                </div>
                <div class="step-content">
                  <div class="step-header">
                    <span class="step-name">{{ step.name }}</span>
                    <span class="step-duration" v-if="step.duration">{{ formatDuration(step.duration) }}</span>
                  </div>
                  <div class="step-details" v-if="step.metrics">
                    <span>{{ formatNumber(step.metrics.rowsProcessed) }} rows processed</span>
                    <span v-if="step.metrics.quadsGenerated">{{ formatNumber(step.metrics.quadsGenerated) }} quads generated</span>
                  </div>
                  <div class="step-error" v-if="step.error">
                    {{ step.error }}
                  </div>
                </div>
              </div>
            </div>
          </TabPanel>

          <TabPanel header="Metrics">
            <div class="metrics-grid">
              <div class="metric-card">
                <div class="metric-icon"><i class="pi pi-database" /></div>
                <div class="metric-content">
                  <span class="metric-value">{{ formatNumber(job?.metrics?.rowsProcessed || 0) }}</span>
                  <span class="metric-label">Rows Processed</span>
                </div>
              </div>
              <div class="metric-card">
                <div class="metric-icon"><i class="pi pi-share-alt" /></div>
                <div class="metric-content">
                  <span class="metric-value">{{ formatNumber(job?.metrics?.quadsGenerated || 0) }}</span>
                  <span class="metric-label">Quads Generated</span>
                </div>
              </div>
              <div class="metric-card">
                <div class="metric-icon"><i class="pi pi-clock" /></div>
                <div class="metric-content">
                  <span class="metric-value">{{ job?.duration ? formatDuration(job.duration) : '-' }}</span>
                  <span class="metric-label">Duration</span>
                </div>
              </div>
              <div class="metric-card">
                <div class="metric-icon"><i class="pi pi-bolt" /></div>
                <div class="metric-content">
                  <span class="metric-value">{{ throughput }}</span>
                  <span class="metric-label">Throughput</span>
                </div>
              </div>
            </div>

            <div class="charts-section">
              <h3>Processing Rate</h3>
              <div class="chart-placeholder">
                <!-- Chart would be rendered here with ECharts -->
                <p>Processing rate chart</p>
              </div>
            </div>
          </TabPanel>

          <TabPanel header="Variables">
            <DataTable :value="jobVariables" class="p-datatable-sm">
              <Column field="name" header="Variable" />
              <Column field="value" header="Value">
                <template #body="{ data }">
                  <code v-if="data.sensitive">********</code>
                  <code v-else>{{ data.value }}</code>
                </template>
              </Column>
              <Column field="source" header="Source">
                <template #body="{ data }">
                  <Tag :value="data.source" :severity="getSourceSeverity(data.source)" />
                </template>
              </Column>
            </DataTable>
          </TabPanel>

          <TabPanel header="Error" v-if="job?.status === 'failed'">
            <div class="error-section">
              <div class="error-header">
                <i class="pi pi-times-circle" />
                <span>Job Failed</span>
              </div>
              <div class="error-message">
                {{ job.errorMessage }}
              </div>
              <div class="error-details" v-if="job.errorDetails">
                <h4>Stack Trace</h4>
                <pre>{{ job.errorDetails.stackTrace }}</pre>
              </div>
              <div class="error-context" v-if="job.errorDetails?.context">
                <h4>Context</h4>
                <pre>{{ JSON.stringify(job.errorDetails.context, null, 2) }}</pre>
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>

      <div class="side-panel">
        <Panel header="Job Details">
          <div v-if="loading" class="flex flex-column gap-3">
            <Skeleton width="100%" height="2rem" v-for="i in 7" :key="i"></Skeleton>
          </div>
          <template v-else>
            <div class="detail-item">
              <span class="label">Job ID</span>
              <code class="value">{{ jobId }}</code>
            </div>
            <div class="detail-item">
              <span class="label">Pipeline</span>
              <span class="value link" @click="viewPipeline">{{ job?.pipelineName }}</span>
            </div>
            <div class="detail-item">
              <span class="label">Version</span>
              <span class="value">v{{ job?.pipelineVersion }}</span>
            </div>
            <div class="detail-item">
              <span class="label">Triggered By</span>
              <Tag :value="job?.triggeredBy" :severity="getTriggerSeverity(job?.triggeredBy)" />
            </div>
            <div class="detail-item">
              <span class="label">Created</span>
              <span class="value">{{ formatDate(job?.createdAt) }}</span>
            </div>
            <div class="detail-item">
              <span class="label">Started</span>
              <span class="value">{{ formatDate(job?.startedAt) || 'Pending' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">Completed</span>
              <span class="value">{{ formatDate(job?.completedAt) || '-' }}</span>
            </div>
          </template>
        </Panel>

        <Panel header="Output" v-if="job?.status === 'completed'">
          <div class="output-item">
            <i class="pi pi-database" />
            <div class="output-info">
              <span class="output-name">Target Graph</span>
              <code class="output-uri">{{ job?.outputGraph }}</code>
            </div>
            <Button icon="pi pi-external-link" class="p-button-text p-button-sm" @click="browseGraph" />
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import ProgressBar from 'primevue/progressbar'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Panel from 'primevue/panel'
import Dropdown from 'primevue/dropdown'
import InputText from 'primevue/inputtext'
import ToggleButton from 'primevue/togglebutton'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Skeleton from 'primevue/skeleton'
import { useToast } from 'primevue/usetoast'
import { useJobStore } from '@/stores/job'

interface LogEntry {
  id: string
  timestamp: Date
  level: 'debug' | 'info' | 'warn' | 'error'
  step?: string
  message: string
}

interface PipelineStep {
  id: string
  name: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped'
  duration?: number
  metrics?: { rowsProcessed: number; quadsGenerated?: number }
  error?: string
}

const route = useRoute()
const router = useRouter()
const jobStore = useJobStore()
const toast = useToast()

const jobId = computed(() => route.params.id as string)
const job = ref<any>(null)
const logs = ref<LogEntry[]>([])
const pipelineSteps = ref<PipelineStep[]>([])
const logsContainer = ref<HTMLElement | null>(null)
const loading = ref(true)

const logLevel = ref('info')
const logSearch = ref('')
const autoScroll = ref(true)

let pollInterval: number
let wsConnection: WebSocket | null = null

const logLevelOptions = [
  { label: 'All', value: 'all' },
  { label: 'Debug', value: 'debug' },
  { label: 'Info', value: 'info' },
  { label: 'Warning', value: 'warn' },
  { label: 'Error', value: 'error' }
]

const currentStep = computed(() => pipelineSteps.value.findIndex(s => s.status === 'running') + 1 || pipelineSteps.value.filter(s => s.status === 'completed').length)
const totalSteps = computed(() => pipelineSteps.value.length)
const currentStepName = computed(() => pipelineSteps.value.find(s => s.status === 'running')?.name || '-')

const filteredLogs = computed(() => {
  let result = logs.value
  if (logLevel.value !== 'all') {
    const levels = ['debug', 'info', 'warn', 'error']
    const minLevel = levels.indexOf(logLevel.value)
    result = result.filter(l => levels.indexOf(l.level) >= minLevel)
  }
  if (logSearch.value) {
    const query = logSearch.value.toLowerCase()
    result = result.filter(l => l.message.toLowerCase().includes(query))
  }
  return result
})

const throughput = computed(() => {
  if (!job.value?.metrics?.rowsProcessed || !job.value?.duration) return '-'
  const rowsPerSec = (job.value.metrics.rowsProcessed / (job.value.duration / 1000)).toFixed(0)
  return `${formatNumber(Number(rowsPerSec))} rows/s`
})

const jobVariables = computed(() => {
  if (!job.value?.variables) return []
  return Object.entries(job.value.variables).map(([name, value]) => ({
    name,
    value: value as string,
    sensitive: name.toLowerCase().includes('password') || name.toLowerCase().includes('secret'),
    source: 'runtime'
  }))
})

onMounted(async () => {
  await loadJob()
  connectWebSocket()
  pollInterval = window.setInterval(loadJob, 3000)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
  if (wsConnection) wsConnection.close()
})

watch(logs, () => {
  if (autoScroll.value) {
    nextTick(() => {
      if (logsContainer.value) {
        logsContainer.value.scrollTop = logsContainer.value.scrollHeight
      }
    })
  }
})

async function loadJob() {
  try {
    job.value = await jobStore.fetchJob(jobId.value)
    pipelineSteps.value = job.value?.steps || []
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load job details', life: 3000 })
  } finally {
    loading.value = false
  }
}

function connectWebSocket() {
  const wsUrl = `ws://localhost:8003/api/v1/jobs/${jobId.value}/logs/stream`
  wsConnection = new WebSocket(wsUrl)
  wsConnection.onmessage = (event) => {
    const log = JSON.parse(event.data)
    logs.value.push(log)
  }
  wsConnection.onerror = () => {
    console.error('WebSocket error')
  }
}

function goBack() {
  router.push('/jobs')
}

async function cancelJob() {
  try {
    await jobStore.cancelJob(jobId.value)
    toast.add({ severity: 'success', summary: 'Cancelled', detail: 'Job cancelled successfully', life: 3000 })
    await loadJob()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to cancel job', life: 3000 })
  }
}

async function retryJob() {
  try {
    await jobStore.retryJob(jobId.value)
    toast.add({ severity: 'success', summary: 'Retrying', detail: 'Job retry started', life: 3000 })
    await loadJob()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to retry job', life: 3000 })
  }
}

function viewPipeline() {
  if (job.value?.pipelineId) {
    router.push(`/pipelines/${job.value.pipelineId}`)
  }
}

function browseGraph() {
  if (job.value?.outputGraph) {
    router.push({ path: '/triplestore', query: { graph: job.value.outputGraph } })
  }
}

function downloadLogs() {
  const content = logs.value.map(l => `${l.timestamp} [${l.level.toUpperCase()}] ${l.step ? `[${l.step}] ` : ''}${l.message}`).join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `job-${jobId.value}-logs.txt`
  a.click()
}

function getStatusSeverity(status?: string): string {
  switch (status) {
    case 'running': return 'info'
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'cancelled': return 'warning'
    default: return 'secondary'
  }
}

function getStatusIcon(status?: string): string {
  switch (status) {
    case 'running': return 'pi pi-spin pi-spinner'
    case 'completed': return 'pi pi-check'
    case 'failed': return 'pi pi-times'
    case 'cancelled': return 'pi pi-ban'
    default: return 'pi pi-clock'
  }
}

function getStepIcon(status: string): string {
  switch (status) {
    case 'running': return 'pi pi-spin pi-spinner'
    case 'completed': return 'pi pi-check'
    case 'failed': return 'pi pi-times'
    case 'skipped': return 'pi pi-minus'
    default: return 'pi pi-circle'
  }
}

function getTriggerSeverity(trigger?: string): string {
  switch (trigger) {
    case 'manual': return 'info'
    case 'schedule': return 'success'
    case 'api': return 'warning'
    default: return 'secondary'
  }
}

function getSourceSeverity(source: string): string {
  switch (source) {
    case 'environment': return 'info'
    case 'runtime': return 'success'
    case 'default': return 'secondary'
    default: return 'info'
  }
}

function formatDate(date?: Date): string {
  if (!date) return ''
  return new Date(date).toLocaleString()
}

function formatTimestamp(date: Date): string {
  return new Date(date).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 })
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  if (ms < 3600000) return `${Math.round(ms / 60000)}m`
  return `${Math.round(ms / 3600000)}h`
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}
</script>

<style scoped>
.job-monitor {
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

.job-title {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.job-title h1 {
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.monitor-content {
  display: flex;
  gap: 1.5rem;
  flex: 1;
  overflow: hidden;
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-panel {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.progress-section {
  background: var(--surface-ground);
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-weight: 600;
}

.progress-details {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.logs-toolbar {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-bottom: 1rem;
}

.logs-toolbar .spacer {
  flex: 1;
}

.logs-container {
  height: 400px;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 4px;
  padding: 0.5rem;
  font-family: monospace;
  font-size: 0.8125rem;
}

.log-entry {
  padding: 0.25rem 0.5rem;
  display: flex;
  gap: 0.75rem;
}

.log-entry:hover {
  background: rgba(255, 255, 255, 0.05);
}

.log-timestamp {
  color: #888;
  flex-shrink: 0;
}

.log-level {
  width: 50px;
  flex-shrink: 0;
}

.log-debug .log-level { color: #888; }
.log-info .log-level { color: #4fc3f7; }
.log-warn .log-level { color: #ffb74d; }
.log-error .log-level { color: #ef5350; }

.log-step {
  color: #ba68c8;
  flex-shrink: 0;
}

.log-message {
  color: #ddd;
}

.logs-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #888;
  gap: 0.5rem;
}

.steps-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.step-item {
  display: flex;
  gap: 1rem;
  position: relative;
  padding-bottom: 1.5rem;
}

.step-connector {
  position: absolute;
  left: 15px;
  top: 32px;
  bottom: 0;
  width: 2px;
  background: var(--surface-border);
}

.step-item.completed .step-connector {
  background: var(--green-500);
}

.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-200);
  flex-shrink: 0;
  z-index: 1;
}

.step-item.completed .step-icon {
  background: var(--green-500);
  color: white;
}

.step-item.running .step-icon {
  background: var(--blue-500);
  color: white;
}

.step-item.failed .step-icon {
  background: var(--red-500);
  color: white;
}

.step-content {
  flex: 1;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-name {
  font-weight: 600;
}

.step-duration {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.step-details {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin-top: 0.25rem;
  display: flex;
  gap: 1rem;
}

.step-error {
  margin-top: 0.5rem;
  padding: 0.5rem;
  background: var(--red-100);
  color: var(--red-700);
  border-radius: 4px;
  font-size: 0.875rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 2rem;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  background: var(--surface-ground);
  border-radius: 8px;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--primary-color-alpha);
  display: flex;
  align-items: center;
  justify-content: center;
}

.metric-icon i {
  font-size: 1.5rem;
  color: var(--primary-color);
}

.metric-content {
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 1.5rem;
  font-weight: 700;
}

.metric-label {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.charts-section h3 {
  margin-bottom: 1rem;
}

.chart-placeholder {
  height: 200px;
  background: var(--surface-ground);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-color-secondary);
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--surface-border);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-item .label {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}

.detail-item .value {
  font-weight: 500;
}

.detail-item .value.link {
  color: var(--primary-color);
  cursor: pointer;
}

.detail-item .value.link:hover {
  text-decoration: underline;
}

.output-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.output-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.output-name {
  font-weight: 600;
}

.output-uri {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.error-section {
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: 8px;
  padding: 1.5rem;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--red-700);
  margin-bottom: 1rem;
}

.error-message {
  color: var(--red-700);
  margin-bottom: 1rem;
}

.error-details,
.error-context {
  margin-top: 1rem;
}

.error-details h4,
.error-context h4 {
  margin-bottom: 0.5rem;
  color: var(--red-700);
}

.error-details pre,
.error-context pre {
  background: white;
  padding: 1rem;
  border-radius: 4px;
  overflow: auto;
  max-height: 300px;
  font-size: 0.75rem;
}
</style>
