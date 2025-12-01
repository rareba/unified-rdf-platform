<template>
  <div v-if="loading" class="loading-overlay">
    <div class="loading-content">
      <i class="pi pi-spin pi-spinner loading-icon"></i>
      <span v-if="message" class="loading-message">{{ message }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  loading: boolean
  message?: string
}>()
</script>

<style scoped>
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
  border-radius: inherit;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.loading-icon {
  font-size: 2.5rem;
  color: var(--primary-color);
}

.loading-message {
  font-weight: 500;
  color: var(--text-color);
}
</style>