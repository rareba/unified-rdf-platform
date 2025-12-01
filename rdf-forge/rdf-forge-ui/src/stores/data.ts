import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/data'

export const useDataStore = defineStore('data', () => {
  const dataSources = ref<api.DataSource[]>([])
  const currentDataSource = ref<api.DataSource | null>(null)
  const currentPreview = ref<api.DataPreview | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchDataSources(params?: { search?: string; format?: string }) {
    loading.value = true
    error.value = null
    try {
      dataSources.value = await api.fetchDataSources(params)
      return dataSources.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchDataSource(id: string) {
    loading.value = true
    error.value = null
    try {
      currentDataSource.value = await api.fetchDataSource(id)
      return currentDataSource.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function uploadDataSource(file: File, options?: api.UploadOptions) {
    loading.value = true
    error.value = null
    try {
      const dataSource = await api.uploadDataSource(file, options)
      dataSources.value.unshift(dataSource)
      return dataSource
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deleteDataSource(id: string) {
    loading.value = true
    error.value = null
    try {
      await api.deleteDataSource(id)
      dataSources.value = dataSources.value.filter(d => d.id !== id)
      if (currentDataSource.value?.id === id) {
        currentDataSource.value = null
        currentPreview.value = null
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function previewDataSource(id: string, params?: { rows?: number; offset?: number }) {
    loading.value = true
    error.value = null
    try {
      currentPreview.value = await api.previewDataSource(id, params)
      return currentPreview.value
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  function downloadDataSource(id: string) {
    api.downloadDataSource(id)
  }

  async function analyzeDataSource(id: string) {
    loading.value = true
    error.value = null
    try {
      return await api.analyzeDataSource(id)
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function detectFormat(file: File) {
    try {
      return await api.detectFormat(file)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  function clearCurrentDataSource() {
    currentDataSource.value = null
    currentPreview.value = null
  }

  return {
    dataSources,
    currentDataSource,
    currentPreview,
    loading,
    error,
    fetchDataSources,
    fetchDataSource,
    uploadDataSource,
    deleteDataSource,
    previewDataSource,
    downloadDataSource,
    analyzeDataSource,
    detectFormat,
    clearCurrentDataSource
  }
})
