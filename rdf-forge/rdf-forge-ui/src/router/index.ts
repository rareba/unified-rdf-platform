import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/Dashboard.vue')
    },
    {
      path: '/pipelines',
      name: 'pipelines',
      component: () => import('@/views/pipeline/PipelineList.vue')
    },
    {
      path: '/pipelines/new',
      name: 'pipeline-new',
      component: () => import('@/views/pipeline/PipelineDesigner.vue')
    },
    {
      path: '/pipelines/:id',
      name: 'pipeline-edit',
      component: () => import('@/views/pipeline/PipelineDesigner.vue')
    },
    {
      path: '/shacl',
      name: 'shacl',
      component: () => import('@/views/shacl/ShaclStudio.vue')
    },
    {
      path: '/shacl/new',
      name: 'shape-new',
      component: () => import('@/views/shacl/ShapeEditor.vue')
    },
    {
      path: '/shacl/:id',
      name: 'shape-edit',
      component: () => import('@/views/shacl/ShapeEditor.vue')
    },
    {
      path: '/cube',
      name: 'cube',
      component: () => import('@/views/cube/CubeWizard.vue')
    },
    {
      path: '/jobs',
      name: 'jobs',
      component: () => import('@/views/job/JobList.vue')
    },
    {
      path: '/jobs/:id',
      name: 'job-detail',
      component: () => import('@/views/job/JobMonitor.vue')
    },
    {
      path: '/data',
      name: 'data',
      component: () => import('@/views/data/DataManager.vue')
    },
    {
      path: '/dimensions',
      name: 'dimensions',
      component: () => import('@/views/dimension/DimensionManager.vue')
    },
    {
      path: '/triplestore',
      name: 'triplestore',
      component: () => import('@/views/triplestore/TriplestoreBrowser.vue')
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/settings/Settings.vue')
    },
    {
      path: '/jobs/:id/logs',
      name: 'job-logs',
      component: () => import('@/views/job/JobMonitor.vue')
    }
  ]
})

export default router
