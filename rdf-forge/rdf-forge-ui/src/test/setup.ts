import { vi } from 'vitest'
import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// Mock PrimeVue components
vi.mock('primevue/button', () => ({
  default: {
    name: 'Button',
    template: '<button><slot /></button>',
    props: ['label', 'icon', 'disabled', 'loading']
  }
}))

vi.mock('primevue/inputtext', () => ({
  default: {
    name: 'InputText',
    template: '<input type="text" />',
    props: ['modelValue']
  }
}))

vi.mock('primevue/dropdown', () => ({
  default: {
    name: 'Dropdown',
    template: '<select><slot /></select>',
    props: ['modelValue', 'options', 'optionLabel', 'optionValue']
  }
}))

vi.mock('primevue/datatable', () => ({
  default: {
    name: 'DataTable',
    template: '<table><slot /></table>',
    props: ['value', 'loading', 'paginator', 'rows']
  }
}))

vi.mock('primevue/column', () => ({
  default: {
    name: 'Column',
    template: '<td><slot /></td>',
    props: ['field', 'header', 'sortable']
  }
}))

// Mock Vue Router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    go: vi.fn(),
    back: vi.fn(),
    forward: vi.fn()
  }),
  useRoute: () => ({
    params: {},
    query: {},
    path: '/',
    name: 'home'
  }),
  RouterLink: {
    template: '<a><slot /></a>'
  },
  RouterView: {
    template: '<div><slot /></div>'
  }
}))

// Mock Keycloak
vi.mock('keycloak-js', () => ({
  default: vi.fn(() => ({
    init: vi.fn().mockResolvedValue(true),
    login: vi.fn(),
    logout: vi.fn(),
    loadUserProfile: vi.fn().mockResolvedValue({
      username: 'testuser',
      email: 'test@example.com',
      firstName: 'Test',
      lastName: 'User'
    }),
    updateToken: vi.fn().mockResolvedValue(true),
    token: 'mock-token',
    refreshToken: 'mock-refresh-token'
  }))
}))

// Global test configuration
config.global.stubs = {
  teleport: true
}

// Setup Pinia for each test
beforeEach(() => {
  setActivePinia(createPinia())
})

// Clean up after each test
afterEach(() => {
  vi.clearAllMocks()
})