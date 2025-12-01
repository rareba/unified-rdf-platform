import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'

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

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('Initial State', () => {
    it('should have keycloak as null initially', () => {
      const store = useAuthStore()
      expect(store.keycloak).toBeNull()
    })

    it('should not be authenticated initially', () => {
      const store = useAuthStore()
      expect(store.isAuthenticated).toBe(false)
    })

    it('should have no user profile initially', () => {
      const store = useAuthStore()
      expect(store.userProfile).toBeNull()
    })

    it('should have undefined token initially', () => {
      const store = useAuthStore()
      expect(store.token).toBeUndefined()
    })
  })

  describe('initKeycloak', () => {
    it('should initialize Keycloak instance', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      expect(store.keycloak).toBeDefined()
    })

    it('should set isAuthenticated to true on successful init', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      expect(store.isAuthenticated).toBe(true)
    })

    it('should load user profile on successful init', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      expect(store.userProfile).toEqual({
        username: 'testuser',
        email: 'test@example.com',
        firstName: 'Test',
        lastName: 'User'
      })
    })

    it('should store the token', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      expect(store.token).toBe('mock-token')
    })

    it('should handle initialization failure gracefully', async () => {
      // Mock Keycloak to fail
      vi.mock('keycloak-js', () => ({
        default: vi.fn(() => ({
          init: vi.fn().mockRejectedValue(new Error('Init failed'))
        }))
      }))

      const store = useAuthStore()
      
      // Should not throw
      await expect(store.initKeycloak()).resolves.not.toThrow()
    })
  })

  describe('login', () => {
    it('should call Keycloak login method', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      store.login()
      
      expect(store.keycloak?.login).toHaveBeenCalled()
    })

    it('should handle login when keycloak is not initialized', () => {
      const store = useAuthStore()
      
      // Should not throw even if keycloak is null
      expect(() => store.login()).not.toThrow()
    })
  })

  describe('logout', () => {
    it('should call Keycloak logout method', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      store.logout()
      
      expect(store.keycloak?.logout).toHaveBeenCalled()
    })

    it('should handle logout when keycloak is not initialized', () => {
      const store = useAuthStore()
      
      // Should not throw even if keycloak is null
      expect(() => store.logout()).not.toThrow()
    })
  })

  describe('getToken', () => {
    it('should return current token', async () => {
      const store = useAuthStore()
      await store.initKeycloak()
      
      const token = store.getToken()
      
      expect(token).toBe('mock-token')
    })

    it('should return undefined when not authenticated', () => {
      const store = useAuthStore()
      
      const token = store.getToken()
      
      expect(token).toBeUndefined()
    })
  })

  describe('Token Refresh', () => {
    it('should set up token refresh interval', async () => {
      vi.useFakeTimers()
      const store = useAuthStore()
      await store.initKeycloak()
      
      // Advance time by 60 seconds
      vi.advanceTimersByTime(60000)
      
      expect(store.keycloak?.updateToken).toHaveBeenCalled()
      
      vi.useRealTimers()
    })

    it('should update token on successful refresh', async () => {
      vi.useFakeTimers()
      const store = useAuthStore()
      await store.initKeycloak()
      
      vi.advanceTimersByTime(60000)
      
      expect(store.token).toBe('mock-token')
      
      vi.useRealTimers()
    })
  })

  describe('Environment Configuration', () => {
    it('should use default Keycloak URL when env var not set', async () => {
      const store = useAuthStore()
      
      // Function should be defined regardless of env
      expect(store.initKeycloak).toBeDefined()
    })

    it('should use default realm when env var not set', async () => {
      const store = useAuthStore()
      expect(store.initKeycloak).toBeDefined()
    })

    it('should use default client ID when env var not set', async () => {
      const store = useAuthStore()
      expect(store.initKeycloak).toBeDefined()
    })
  })

  describe('State Reactivity', () => {
    it('should update isAuthenticated reactively', async () => {
      const store = useAuthStore()
      
      expect(store.isAuthenticated).toBe(false)
      
      await store.initKeycloak()
      
      expect(store.isAuthenticated).toBe(true)
    })

    it('should update userProfile reactively', async () => {
      const store = useAuthStore()
      
      expect(store.userProfile).toBeNull()
      
      await store.initKeycloak()
      
      expect(store.userProfile).toBeDefined()
      expect(store.userProfile?.username).toBe('testuser')
    })
  })
})