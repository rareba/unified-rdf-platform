import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/shacl'

export const useShaclStore = defineStore('shacl', () => {
  const shapes = ref<api.Shape[]>([])
  const currentShape = ref<api.Shape | null>(null)
  const templates = ref<api.Shape[]>([])
  const lastValidationResult = ref<api.ValidationResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchShapes(params?: { search?: string; category?: string; isTemplate?: boolean }) {
    loading.value = true
    error.value = null
    try {
      shapes.value = await api.fetchShapes(params)
      return shapes.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchShape(id: string) {
    loading.value = true
    error.value = null
    try {
      currentShape.value = await api.fetchShape(id)
      return currentShape.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function createShape(data: api.ShapeCreateRequest) {
    loading.value = true
    error.value = null
    try {
      const shape = await api.createShape(data)
      shapes.value.unshift(shape)
      return shape
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateShape(id: string, data: Partial<api.ShapeCreateRequest>) {
    loading.value = true
    error.value = null
    try {
      const shape = await api.updateShape(id, data)
      const index = shapes.value.findIndex(s => s.id === id)
      if (index >= 0) {
        shapes.value[index] = shape
      }
      if (currentShape.value?.id === id) {
        currentShape.value = shape
      }
      return shape
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deleteShape(id: string) {
    loading.value = true
    error.value = null
    try {
      await api.deleteShape(id)
      shapes.value = shapes.value.filter(s => s.id !== id)
      if (currentShape.value?.id === id) {
        currentShape.value = null
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function validateSyntax(content: string, format: 'turtle' | 'jsonld') {
    try {
      return await api.validateSyntax(content, format)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function runValidation(shapeId: string, data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples') {
    loading.value = true
    error.value = null
    try {
      lastValidationResult.value = await api.runValidation(shapeId, data, dataFormat)
      return lastValidationResult.value
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function runValidationOnGraph(shapeId: string, triplestoreId: string, graphUri: string) {
    loading.value = true
    error.value = null
    try {
      lastValidationResult.value = await api.runValidationOnGraph(shapeId, triplestoreId, graphUri)
      return lastValidationResult.value
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function inferShape(data: string, dataFormat: 'turtle' | 'jsonld' | 'ntriples', options?: { targetClass?: string }) {
    loading.value = true
    error.value = null
    try {
      const shape = await api.inferShape(data, dataFormat, options)
      return shape
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function generateTurtle(nodeShape: { uri: string; targetClass: string; properties: api.PropertyShape[] }) {
    try {
      return await api.generateTurtle(nodeShape)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function fetchTemplates() {
    try {
      templates.value = await api.fetchShapeTemplates()
      return templates.value
    } catch (e: any) {
      error.value = e.message
      return []
    }
  }

  function clearCurrentShape() {
    currentShape.value = null
  }

  function clearValidationResult() {
    lastValidationResult.value = null
  }

  return {
    shapes,
    currentShape,
    templates,
    lastValidationResult,
    loading,
    error,
    fetchShapes,
    fetchShape,
    createShape,
    updateShape,
    deleteShape,
    validateSyntax,
    runValidation,
    runValidationOnGraph,
    inferShape,
    generateTurtle,
    fetchTemplates,
    clearCurrentShape,
    clearValidationResult
  }
})
