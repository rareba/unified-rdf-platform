import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api/job'

export const useJobStore = defineStore('job', () => {
  const jobs = ref<api.Job[]>([])
  const currentJob = ref<api.Job | null>(null)
  const currentJobLogs = ref<api.JobLog[]>([])
  const schedules = ref<api.JobSchedule[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const runningJobs = computed(() => jobs.value.filter(j => j.status === 'running'))
  const pendingJobs = computed(() => jobs.value.filter(j => j.status === 'pending'))

  async function fetchJobs(params?: { status?: string; pipelineId?: string }) {
    loading.value = true
    error.value = null
    try {
      jobs.value = await api.fetchJobs(params)
      return jobs.value
    } catch (e: any) {
      error.value = e.message
      return []
    } finally {
      loading.value = false
    }
  }

  async function fetchJob(id: string) {
    loading.value = true
    error.value = null
    try {
      currentJob.value = await api.fetchJob(id)
      return currentJob.value
    } catch (e: any) {
      error.value = e.message
      return null
    } finally {
      loading.value = false
    }
  }

  async function createJob(pipelineId: string, variables?: Record<string, any>, priority?: number) {
    loading.value = true
    error.value = null
    try {
      const job = await api.createJob(pipelineId, variables, priority)
      jobs.value.unshift(job)
      return job
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      loading.value = false
    }
  }

  async function cancelJob(id: string) {
    try {
      await api.cancelJob(id)
      const index = jobs.value.findIndex(j => j.id === id)
      if (index >= 0) {
        jobs.value[index].status = 'cancelled'
      }
      if (currentJob.value?.id === id) {
        currentJob.value.status = 'cancelled'
      }
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function retryJob(id: string) {
    try {
      const job = await api.retryJob(id)
      jobs.value.unshift(job)
      return job
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function fetchJobLogs(id: string, params?: { level?: string; limit?: number }) {
    try {
      currentJobLogs.value = await api.fetchJobLogs(id, params)
      return currentJobLogs.value
    } catch (e: any) {
      error.value = e.message
      return []
    }
  }

  function appendLog(log: api.JobLog) {
    currentJobLogs.value.push(log)
  }

  async function fetchSchedules() {
    try {
      schedules.value = await api.fetchSchedules()
      return schedules.value
    } catch (e: any) {
      error.value = e.message
      return []
    }
  }

  async function createSchedule(pipelineId: string, cronExpression: string, variables?: Record<string, any>) {
    try {
      const schedule = await api.createSchedule(pipelineId, cronExpression, variables)
      schedules.value.push(schedule)
      return schedule
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  async function deleteSchedule(id: string) {
    try {
      await api.deleteSchedule(id)
      schedules.value = schedules.value.filter(s => s.id !== id)
    } catch (e: any) {
      error.value = e.message
      throw e
    }
  }

  function clearCurrentJob() {
    currentJob.value = null
    currentJobLogs.value = []
  }

  return {
    jobs,
    currentJob,
    currentJobLogs,
    schedules,
    loading,
    error,
    runningJobs,
    pendingJobs,
    fetchJobs,
    fetchJob,
    createJob,
    cancelJob,
    retryJob,
    fetchJobLogs,
    appendLog,
    fetchSchedules,
    createSchedule,
    deleteSchedule,
    clearCurrentJob
  }
})
