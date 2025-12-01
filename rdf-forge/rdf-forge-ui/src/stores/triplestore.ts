import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api/triplestore'

export const useTriplestoreStore = defineStore('triplestore', () => {
  const connections = ref<api.TriplestoreConnection[]>([])
  const currentConnection = ref<api.TriplestoreConnection | null>(null)
  const graphs = ref<api.Graph[]>([])
  const currentGraph = ref<string | null>(null)
  const currentResource = ref<api.Resource | null>(null)
  const lastQueryResult = ref<api.QueryResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConnections() {
    loading.value = true
    error.value = null
    try {
      connections.value = await api.fetchConnections()
      return connections.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchConnection(id: string) {
    loading.value = true
    error.value = null
    try {
      currentConnection.value = await api.fetchConnection(id)
      return currentConnection.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function createConnection(data: api.ConnectionCreateRequest) {
    loading.value = true
    error.value = null
    try {
      const connection = await api.createConnection(data)
      connections.value.push(connection)
      return connection
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateConnection(id: string, data: Partial<api.ConnectionCreateRequest>) {
    loading.value = true
    error.value = null
    try {
      const connection = await api.updateConnection(id, data)
      const index = connections.value.findIndex(c => c.id === id)
      if (index >= 0) {
        connections.value[index] = connection
      }
      if (currentConnection.value?.id === id) {
        currentConnection.value = connection
      }
      return connection
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deleteConnection(id: string) {
    loading.value = true
    error.value = null
    try {
      await api.deleteConnection(id)
      connections.value = connections.value.filter(c => c.id !== id)
      if (currentConnection.value?.id === id) {
        currentConnection.value = null
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function testConnection(id: string) {
    try {
      return await api.testConnection(id)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function connect(id: string) {
    loading.value = true
    error.value = null
    try {
      await api.connect(id)
      await fetchConnection(id)
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchGraphs(connectionId: string) {
    loading.value = true
    error.value = null
    try {
      graphs.value = await api.fetchGraphs(connectionId)
      return graphs.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchGraphResources(connectionId: string, graphUri: string, params?: { limit?: number; offset?: number }) {
    loading.value = true
    error.value = null
    try {
      return await api.fetchGraphResources(connectionId, graphUri, params)
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function searchResources(connectionId: string, graphUri: string, query: string) {
    loading.value = true
    error.value = null
    try {
      return await api.searchResources(connectionId, graphUri, query)
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchResource(connectionId: string, graphUri: string, resourceUri: string) {
    loading.value = true
    error.value = null
    try {
      currentResource.value = await api.fetchResource(connectionId, graphUri, resourceUri)
      return currentResource.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function executeSparql(connectionId: string, query: string, graph?: string) {
    loading.value = true
    error.value = null
    try {
      lastQueryResult.value = await api.executeSparql(connectionId, query, graph)
      return lastQueryResult.value
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function uploadRdf(connectionId: string, graphUri: string, content: string, format: 'turtle' | 'rdfxml' | 'ntriples' | 'jsonld') {
    loading.value = true
    error.value = null
    try {
      const result = await api.uploadRdf(connectionId, graphUri, content, format)
      await fetchGraphs(connectionId)
      return result
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deleteGraph(connectionId: string, graphUri: string) {
    loading.value = true
    error.value = null
    try {
      await api.deleteGraph(connectionId, graphUri)
      graphs.value = graphs.value.filter(g => g.uri !== graphUri)
      if (currentGraph.value === graphUri) {
        currentGraph.value = null
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function exportGraph(connectionId: string, graphUri: string, format: 'turtle' | 'rdfxml' | 'ntriples' | 'jsonld') {
    try {
      return await api.exportGraph(connectionId, graphUri, format)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  function setCurrentGraph(graphUri: string | null) {
    currentGraph.value = graphUri
    currentResource.value = null
  }

  function clearCurrentResource() {
    currentResource.value = null
  }

  return {
    connections,
    currentConnection,
    graphs,
    currentGraph,
    currentResource,
    lastQueryResult,
    loading,
    error,
    fetchConnections,
    fetchConnection,
    createConnection,
    updateConnection,
    deleteConnection,
    testConnection,
    connect,
    fetchGraphs,
    fetchGraphResources,
    searchResources,
    fetchResource,
    executeSparql,
    uploadRdf,
    deleteGraph,
    exportGraph,
    setCurrentGraph,
    clearCurrentResource
  }
})
