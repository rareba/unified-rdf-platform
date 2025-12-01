<template>
  <div class="dimension-manager">
    <LoadingOverlay :loading="loading" message="Loading dimensions..." />
    <div class="d-flex align-center mb-4">
      <h1 class="text-h4">Dimensions</h1>
      <v-spacer></v-spacer>
      <v-btn color="primary" prepend-icon="mdi-plus" @click="openCreateDialog">
        New Dimension
      </v-btn>
    </div>

    <v-row>
      <v-col cols="12" md="4">
        <v-card class="mb-4">
          <v-card-title>Dimension List</v-card-title>
          <v-text-field
            v-model="search"
            prepend-inner-icon="mdi-magnify"
            label="Search dimensions..."
            single-line
            hide-details
            class="mx-4 mb-2"
            @input="debouncedSearch"
          ></v-text-field>
          
          <v-list lines="two" v-if="!loading">
            <v-list-item
              v-for="dim in dimensions"
              :key="dim.id"
              :title="dim.name"
              :subtitle="dim.type + ' • ' + (dim.valueCount || 0) + ' values'"
              :active="currentDimension?.id === dim.id"
              @click="selectDimension(dim.id)"
            >
              <template v-slot:prepend>
                <v-icon :color="getTypeColor(dim.type || 'KEY')">mdi-shape</v-icon>
              </template>
            </v-list-item>
            <v-list-item v-if="dimensions.length === 0">
              <v-list-item-title class="text-grey">No dimensions found</v-list-item-title>
            </v-list-item>
          </v-list>
          <div v-else class="d-flex justify-center pa-4">
            <v-progress-circular indeterminate></v-progress-circular>
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" md="8">
        <v-card v-if="currentDimension">
          <v-toolbar color="surface" flat class="border-b">
            <v-toolbar-title>{{ currentDimension.name }}</v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn icon="mdi-download" title="Export Turtle" @click="exportDimension"></v-btn>
            <v-btn icon="mdi-upload" title="Import CSV" @click="triggerImport"></v-btn>
            <v-btn icon="mdi-delete" color="error" @click="deleteDimension"></v-btn>
            <input type="file" ref="fileInput" style="display: none" accept=".csv" @change="handleFileUpload" />
          </v-toolbar>

          <v-card-text class="pa-4">
            <div class="text-subtitle-1 mb-2">URI: {{ currentDimension.uri }}</div>
            <div class="text-body-2 mb-4">{{ currentDimension.description }}</div>

            <v-divider class="mb-4"></v-divider>

            <div class="d-flex justify-space-between align-center mb-2">
              <h3 class="text-h6">Values</h3>
              <v-btn size="small" variant="text" prepend-icon="mdi-refresh" @click="refreshValues">Refresh</v-btn>
            </div>

            <v-data-table
              :headers="valueHeaders"
              :items="currentValues"
              :loading="loadingValues"
            >
              <template v-slot:item.uri="{ item }">
                <span class="text-caption">{{ item.uri }}</span>
              </template>
            </v-data-table>
          </v-card-text>
        </v-card>
        
        <v-card v-else class="d-flex align-center justify-center fill-height" min-height="400">
          <div class="text-center text-grey">
            <v-icon size="64" class="mb-2">mdi-arrow-left</v-icon>
            <div class="text-h6">Select a dimension to view details</div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- Create Dialog -->
    <v-dialog v-model="createDialog" max-width="500">
      <v-card>
        <v-card-title>Create New Dimension</v-card-title>
        <v-card-text>
          <v-form @submit.prevent="saveDimension">
            <v-text-field v-model="newDim.name" label="Name" required></v-text-field>
            <v-text-field v-model="newDim.uri" label="URI" required></v-text-field>
            <v-select v-model="newDim.type" :items="dimTypes" label="Type"></v-select>
            <v-textarea v-model="newDim.description" label="Description"></v-textarea>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="createDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="saveDimension">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useDimensionStore } from '@/stores/dimension';
import { storeToRefs } from 'pinia';
import { useToast } from 'primevue/usetoast';
import LoadingOverlay from '@/components/common/LoadingOverlay.vue';

const store = useDimensionStore();
const toast = useToast();
const { dimensions, currentDimension, currentValues, loading } = storeToRefs(store);

const search = ref('');
const createDialog = ref(false);
const loadingValues = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const newDim = ref({
  name: '',
  uri: '',
  type: 'KEY',
  description: ''
});

const dimTypes = ['TEMPORAL', 'GEO', 'KEY', 'MEASURE', 'ATTRIBUTE', 'CODED'];

const valueHeaders = [
  { title: 'Code', key: 'code', align: 'start' as const },
  { title: 'Label', key: 'label', align: 'start' as const },
  { title: 'URI', key: 'uri', align: 'start' as const },
  { title: 'Level', key: 'hierarchyLevel', align: 'end' as const },
];

onMounted(() => {
  store.fetchDimensions();
});

function getTypeColor(type: string) {
  const colors: Record<string, string> = {
    TEMPORAL: 'blue',
    GEO: 'green',
    KEY: 'purple',
    MEASURE: 'orange',
    ATTRIBUTE: 'grey'
  };
  return colors[type] || 'grey';
}

function debouncedSearch() {
  store.fetchDimensions({ search: search.value });
}

async function selectDimension(id: string) {
  try {
    await store.fetchDimension(id);
    refreshValues();
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load dimension details', life: 3000 });
  }
}

async function refreshValues() {
  if (!currentDimension.value?.id) return;
  loadingValues.value = true;
  try {
    await store.fetchValues(currentDimension.value.id);
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to refresh values', life: 3000 });
  } finally {
    loadingValues.value = false;
  }
}

function openCreateDialog() {
  newDim.value = { name: '', uri: '', type: 'KEY', description: '' };
  createDialog.value = true;
}

async function saveDimension() {
  try {
    await store.createDimension(newDim.value as any);
    createDialog.value = false;
    toast.add({ severity: 'success', summary: 'Created', detail: 'Dimension created successfully', life: 3000 });
    if (dimensions.value.length > 0) {
      selectDimension(dimensions.value[dimensions.value.length - 1].id!);
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to create dimension', life: 3000 });
  }
}

async function deleteDimension() {
  if (!currentDimension.value?.id || !confirm('Are you sure?')) return;
  try {
    await store.deleteDimension(currentDimension.value.id);
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Dimension deleted successfully', life: 3000 });
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete dimension', life: 3000 });
  }
}

function triggerImport() {
  fileInput.value?.click();
}

async function handleFileUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file || !currentDimension.value?.id) return;
  
  loadingValues.value = true;
  try {
    await store.importValues(currentDimension.value.id, file);
    toast.add({ severity: 'success', summary: 'Imported', detail: 'Values imported successfully', life: 3000 });
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to import values', life: 3000 });
  } finally {
    loadingValues.value = false;
  }
}

function exportDimension() {
  // Logic to trigger download from API would go here
  toast.add({ severity: 'info', summary: 'Export', detail: 'Exporting Turtle...', life: 3000 });
}
</script>

<style scoped>
.dimension-manager {
  height: 100%;
  position: relative;
}
</style>
