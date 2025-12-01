<template>
  <div v-if="error" class="error-boundary">
    <div class="error-content">
      <i class="pi pi-exclamation-triangle error-icon"></i>
      <h2>Something went wrong</h2>
      <p class="error-message">{{ error.message }}</p>
      <div class="error-actions">
        <Button label="Reload Page" icon="pi pi-refresh" @click="reloadPage" />
        <Button label="Go Home" icon="pi pi-home" class="p-button-outlined" @click="goHome" />
      </div>
      <div v-if="errorInfo" class="error-details">
        <details>
          <summary>Technical Details</summary>
          <pre>{{ errorInfo }}</pre>
        </details>
      </div>
    </div>
  </div>
  <slot v-else></slot>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

const error = ref<Error | null>(null)
const errorInfo = ref<string | null>(null)
const router = useRouter()

onErrorCaptured((err, instance, info) => {
  error.value = err as Error
  errorInfo.value = info
  console.error('Error captured in ErrorBoundary:', err, info)
  return false // Prevent error from propagating further
})

const reloadPage = () => {
  window.location.reload()
}

const goHome = () => {
  error.value = null
  errorInfo.value = null
  router.push('/')
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
  padding: 2rem;
  background-color: var(--surface-ground);
}

.error-content {
  text-align: center;
  background: white;
  padding: 3rem;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  max-width: 600px;
  width: 100%;
}

.error-icon {
  font-size: 4rem;
  color: var(--red-500);
  margin-bottom: 1.5rem;
}

h2 {
  margin-bottom: 1rem;
  color: var(--text-color);
}

.error-message {
  color: var(--text-color-secondary);
  margin-bottom: 2rem;
}

.error-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  margin-bottom: 2rem;
}

.error-details {
  text-align: left;
  margin-top: 2rem;
  padding-top: 1rem;
  border-top: 1px solid var(--surface-border);
}

details {
  cursor: pointer;
  color: var(--text-color-secondary);
}

pre {
  background: var(--surface-ground);
  padding: 1rem;
  border-radius: 4px;
  overflow: auto;
  margin-top: 0.5rem;
  font-size: 0.875rem;
}
</style>