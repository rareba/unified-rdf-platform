import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/pipeline'

export const usePipelineStore = defineStore('pipeline', () => {
  const pipelines = ref<api.Pipeline[]>([])
  const currentPipeline = ref<api.Pipeline | null>(null)
  const operations = ref<api.Operation[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchPipelines(params?: { search?: string; status?: string }) {
    loading.value = true
    error.value = null
    try {
      pipelines.value = await api.fetchPipelines(params)
      return pipelines.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchPipeline(id: string) {
    loading.value = true
    error.value = null
    try {
      currentPipeline.value = await api.fetchPipeline(id)
      return currentPipeline.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function createPipeline(data: api.PipelineCreateRequest) {
    loading.value = true
    error.value = null
    try {
      const pipeline = await api.createPipeline(data)
      pipelines.value.unshift(pipeline)
      return pipeline
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updatePipeline(id: string, data: Partial<api.PipelineCreateRequest>) {
    loading.value = true
    error.value = null
    try {
      const pipeline = await api.updatePipeline(id, data)
      const index = pipelines.value.findIndex(p => p.id === id)
      if (index >= 0) {
        pipelines.value[index] = pipeline
      }
      if (currentPipeline.value?.id === id) {
        currentPipeline.value = pipeline
      }
      return pipeline
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deletePipeline(id: string) {
    loading.value = true
    error.value = null
    try {
      await api.deletePipeline(id)
      pipelines.value = pipelines.value.filter(p => p.id !== id)
      if (currentPipeline.value?.id === id) {
        currentPipeline.value = null
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function duplicatePipeline(id: string) {
    loading.value = true
    error.value = null
    try {
      const pipeline = await api.duplicatePipeline(id)
      pipelines.value.unshift(pipeline)
      return pipeline
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function validatePipeline(definition: string, format: 'yaml' | 'turtle') {
    try {
      return await api.validatePipeline(definition, format)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function runPipeline(id: string, variables?: Record<string, any>) {
    try {
      return await api.runPipeline(id, variables)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function fetchOperations() {
    try {
      operations.value = await api.fetchOperations()
      return operations.value
    } catch (e: any) {
      error.value = e.message
      return []
    }
  }

  function clearCurrentPipeline() {
    currentPipeline.value = null
  }

  return {
    pipelines,
    currentPipeline,
    operations,
    loading,
    error,
    fetchPipelines,
    fetchPipeline,
    createPipeline,
    updatePipeline,
    deletePipeline,
    duplicatePipeline,
    validatePipeline,
    runPipeline,
    fetchOperations,
    clearCurrentPipeline
  }
})
