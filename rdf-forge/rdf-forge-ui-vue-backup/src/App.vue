<template>
  <div class="app-container">
    <Toast />
    <ConfirmDialog />
    
    <div class="layout-wrapper">
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="logo">
          <div class="logo-icon">
            <i class="pi pi-box"></i>
          </div>
          <div v-if="!sidebarCollapsed" class="logo-text">
            <span class="cube">Cube</span>
            <span class="creator">Creator</span>
            <span class="x">X</span>
          </div>
        </div>
        
        <nav class="nav-menu">
          <router-link to="/" class="nav-item" v-tooltip.right="'Dashboard'">
            <i class="pi pi-home"></i>
            <span v-if="!sidebarCollapsed">Dashboard</span>
          </router-link>
          
          <router-link to="/pipelines" class="nav-item" v-tooltip.right="'Pipelines'">
            <i class="pi pi-sitemap"></i>
            <span v-if="!sidebarCollapsed">Pipelines</span>
          </router-link>
          
          <router-link to="/shacl" class="nav-item" v-tooltip.right="'SHACL Studio'">
            <i class="pi pi-check-square"></i>
            <span v-if="!sidebarCollapsed">SHACL Studio</span>
          </router-link>
          
          <router-link to="/cube" class="nav-item" v-tooltip.right="'Cube Creator'">
            <i class="pi pi-box"></i>
            <span v-if="!sidebarCollapsed">Cube Creator</span>
          </router-link>
          
          <router-link to="/jobs" class="nav-item" v-tooltip.right="'Jobs'">
            <i class="pi pi-sync"></i>
            <span v-if="!sidebarCollapsed">Jobs</span>
          </router-link>
          
          <router-link to="/data" class="nav-item" v-tooltip.right="'Data Sources'">
            <i class="pi pi-database"></i>
            <span v-if="!sidebarCollapsed">Data Sources</span>
          </router-link>
          
          <router-link to="/triplestores" class="nav-item" v-tooltip.right="'Triplestores'">
            <i class="pi pi-server"></i>
            <span v-if="!sidebarCollapsed">Triplestores</span>
          </router-link>
        </nav>
        
        <div class="sidebar-footer">
          <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">
            <i :class="sidebarCollapsed ? 'pi pi-angle-right' : 'pi pi-angle-left'"></i>
          </button>
        </div>
      </aside>
      
      <main class="main-content">
        <header class="topbar">
          <div class="breadcrumb">
            <span>{{ currentPageTitle }}</span>
          </div>
          <div class="toolbar">
            <Button icon="pi pi-bell" class="p-button-text p-button-rounded" />
            <Button icon="pi pi-cog" class="p-button-text p-button-rounded" @click="$router.push('/settings')" />
            <Avatar label="CC" class="user-avatar" shape="circle" />
          </div>
        </header>
        
        <div class="page-content">
          <ErrorBoundary>
            <router-view />
          </ErrorBoundary>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'

const route = useRoute()
const sidebarCollapsed = ref(false)

const currentPageTitle = computed(() => {
  const titles: Record<string, string> = {
    '/': 'Dashboard',
    '/pipelines': 'Pipelines',
    '/shacl': 'SHACL Studio',
    '/cube': 'Cube Creator',
    '/jobs': 'Jobs',
    '/data': 'Data Sources',
    '/triplestores': 'Triplestores',
    '/settings': 'Settings'
  }
  return titles[route.path] || 'Cube Creator X'
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

:root {
  --primary-color: #6366f1;
  --primary-dark: #4f46e5;
  --secondary-color: #0f172a;
  --text-color: #334155;
  --bg-color: #f1f5f9;
  --sidebar-width: 260px;
  --sidebar-collapsed-width: 70px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Inter', sans-serif;
  color: var(--text-color);
  background-color: var(--bg-color);
}

.app-container {
  min-height: 100vh;
  background: var(--bg-color);
}

.layout-wrapper {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-width);
  background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.1);
  z-index: 100;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.logo {
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  height: 80px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #6366f1 0%, #818cf8 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.logo-icon i {
  font-size: 1.5rem;
  color: white;
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.cube {
  font-weight: 700;
  font-size: 1.1rem;
  letter-spacing: 0.5px;
}

.creator {
  font-weight: 400;
  font-size: 0.9rem;
  opacity: 0.8;
}

.x {
  position: absolute;
  right: 1.5rem;
  top: 1.8rem;
  font-weight: 800;
  color: #6366f1;
  opacity: 0.5;
}

.nav-menu {
  flex: 1;
  padding: 1.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.875rem 1rem;
  color: #94a3b8;
  text-decoration: none;
  transition: all 0.2s ease;
  border-radius: 0.75rem;
  font-weight: 500;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: white;
  transform: translateX(4px);
}

.nav-item.router-link-active {
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.15) 0%, rgba(99, 102, 241, 0.05) 100%);
  color: #818cf8;
  border-left: 3px solid #6366f1;
}

.nav-item i {
  font-size: 1.25rem;
  width: 24px;
  text-align: center;
}

.sidebar-footer {
  padding: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.toggle-btn {
  width: 100%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  padding: 0.75rem;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
}

.topbar {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 90;
}

.breadcrumb {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-avatar {
  background: linear-gradient(135deg, #6366f1 0%, #818cf8 100%);
  color: white;
  cursor: pointer;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.2);
}

.page-content {
  flex: 1;
  padding: 2rem;
  overflow: auto;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
}

/* Custom scrollbar */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
