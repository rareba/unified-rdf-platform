<template>
  <div class="dashboard">
    <div class="grid">
      <div class="col-12 md:col-6 lg:col-3">
        <Card class="stat-card">
          <template #content>
            <div class="stat-content">
              <div class="stat-icon bg-blue-100">
                <i class="pi pi-sitemap text-blue-500"></i>
              </div>
              <div class="stat-info">
                <span class="stat-value">{{ stats.pipelines }}</span>
                <span class="stat-label">Pipelines</span>
              </div>
            </div>
          </template>
        </Card>
      </div>
      
      <div class="col-12 md:col-6 lg:col-3">
        <Card class="stat-card">
          <template #content>
            <div class="stat-content">
              <div class="stat-icon bg-green-100">
                <i class="pi pi-check-circle text-green-500"></i>
              </div>
              <div class="stat-info">
                <span class="stat-value">{{ stats.completedJobs }}</span>
                <span class="stat-label">Completed Jobs</span>
              </div>
            </div>
          </template>
        </Card>
      </div>
      
      <div class="col-12 md:col-6 lg:col-3">
        <Card class="stat-card">
          <template #content>
            <div class="stat-content">
              <div class="stat-icon bg-purple-100">
                <i class="pi pi-check-square text-purple-500"></i>
              </div>
              <div class="stat-info">
                <span class="stat-value">{{ stats.shapes }}</span>
                <span class="stat-label">SHACL Shapes</span>
              </div>
            </div>
          </template>
        </Card>
      </div>
      
      <div class="col-12 md:col-6 lg:col-3">
        <Card class="stat-card">
          <template #content>
            <div class="stat-content">
              <div class="stat-icon bg-orange-100">
                <i class="pi pi-database text-orange-500"></i>
              </div>
              <div class="stat-info">
                <span class="stat-value">{{ stats.dataSources }}</span>
                <span class="stat-label">Data Sources</span>
              </div>
            </div>
          </template>
        </Card>
      </div>
    </div>

    <div class="grid mt-4">
      <div class="col-12 lg:col-8">
        <Card>
          <template #title>Recent Jobs</template>
          <template #content>
            <DataTable :value="recentJobs" :rows="5" responsiveLayout="scroll">
              <Column field="pipeline" header="Pipeline"></Column>
              <Column field="status" header="Status">
                <template #body="{ data }">
                  <Tag :severity="getStatusSeverity(data.status)">{{ data.status }}</Tag>
                </template>
              </Column>
              <Column field="startedAt" header="Started">
                <template #body="{ data }">
                  {{ formatDate(data.startedAt) }}
                </template>
              </Column>
              <Column field="duration" header="Duration"></Column>
            </DataTable>
          </template>
        </Card>
      </div>
      
      <div class="col-12 lg:col-4">
        <Card>
          <template #title>Quick Actions</template>
          <template #content>
            <div class="quick-actions">
              <Button 
                label="New Pipeline" 
                icon="pi pi-plus" 
                class="p-button-primary w-full mb-2"
                @click="$router.push('/pipelines/new')"
              />
              <Button 
                label="Create Shape" 
                icon="pi pi-plus" 
                class="p-button-secondary w-full mb-2"
                @click="$router.push('/shacl/new')"
              />
              <Button 
                label="Upload Data" 
                icon="pi pi-upload" 
                class="p-button-info w-full mb-2"
                @click="$router.push('/data')"
              />
              <Button 
                label="Create Cube" 
                icon="pi pi-box" 
                class="p-button-help w-full"
                @click="$router.push('/cube')"
              />
            </div>
          </template>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'

const stats = ref({
  pipelines: 0,
  completedJobs: 0,
  shapes: 0,
  dataSources: 0
})

const recentJobs = ref([
  { pipeline: 'Sales Data ETL', status: 'COMPLETED', startedAt: new Date(), duration: '2m 34s' },
  { pipeline: 'Customer Import', status: 'RUNNING', startedAt: new Date(), duration: '1m 12s' },
  { pipeline: 'Product Catalog', status: 'FAILED', startedAt: new Date(), duration: '45s' }
])

const getStatusSeverity = (status: string) => {
  const map: Record<string, string> = {
    COMPLETED: 'success',
    RUNNING: 'info',
    FAILED: 'danger',
    PENDING: 'warning'
  }
  return map[status] || 'info'
}

const formatDate = (date: Date) => {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

onMounted(async () => {
  stats.value = { pipelines: 12, completedJobs: 156, shapes: 8, dataSources: 24 }
})
</script>

<style scoped>
.stat-card {
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon i {
  font-size: 1.5rem;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1e293b;
}

.stat-label {
  color: #64748b;
  font-size: 0.875rem;
}

.quick-actions {
  display: flex;
  flex-direction: column;
}
</style>
