<template>
  <div class="app-container">
    <Toast />
    <ConfirmDialog />
    
    <div class="layout-wrapper">
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="logo">
          <i class="pi pi-sitemap"></i>
          <span v-if="!sidebarCollapsed">RDF Forge</span>
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
            <Avatar label="U" class="user-avatar" shape="circle" />
          </div>
        </header>
        
        <div class="page-content">
          <router-view />
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
  return titles[route.path] || 'RDF Forge'
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app-container {
  min-height: 100vh;
  background: #f8f9fa;
}

.layout-wrapper {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 250px;
  background: #1e293b;
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}

.sidebar.collapsed {
  width: 60px;
}

.logo {
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.25rem;
  font-weight: 600;
  border-bottom: 1px solid #334155;
}

.logo i {
  font-size: 1.5rem;
  color: #60a5fa;
}

.nav-menu {
  flex: 1;
  padding: 1rem 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1.5rem;
  color: #94a3b8;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-item:hover {
  background: #334155;
  color: white;
}

.nav-item.router-link-active {
  background: #3b82f6;
  color: white;
}

.nav-item i {
  font-size: 1.1rem;
  width: 20px;
  text-align: center;
}

.sidebar-footer {
  padding: 1rem;
  border-top: 1px solid #334155;
}

.toggle-btn {
  width: 100%;
  background: transparent;
  border: 1px solid #475569;
  color: #94a3b8;
  padding: 0.5rem;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  background: #334155;
  color: white;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.topbar {
  background: white;
  padding: 1rem 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.breadcrumb {
  font-size: 1.125rem;
  font-weight: 500;
  color: #1e293b;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-avatar {
  background: #3b82f6;
  color: white;
  cursor: pointer;
}

.page-content {
  flex: 1;
  padding: 1.5rem;
  overflow: auto;
}
</style>
