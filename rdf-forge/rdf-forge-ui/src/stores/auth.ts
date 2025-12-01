import { defineStore } from 'pinia'
import { ref } from 'vue'
import Keycloak from 'keycloak-js'

export const useAuthStore = defineStore('auth', () => {
  const keycloak = ref<Keycloak | null>(null)
  const isAuthenticated = ref(false)
  const userProfile = ref<any>(null)
  const token = ref<string | undefined>(undefined)

  const initKeycloak = async () => {
    try {
      const kc = new Keycloak({
        url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
        realm: import.meta.env.VITE_KEYCLOAK_REALM || 'rdfforge',
        clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'rdf-forge-ui'
      })

      const auth = await kc.init({
        onLoad: 'login-required',
        checkLoginIframe: false
      })

      keycloak.value = kc
      isAuthenticated.value = auth
      token.value = kc.token

      if (auth) {
        userProfile.value = await kc.loadUserProfile()
      }
      
      // Token refresh logic
      setInterval(() => {
        kc.updateToken(70).then((refreshed) => {
          if (refreshed) {
            token.value = kc.token
          }
        }).catch(() => {
          console.error('Failed to refresh token')
        })
      }, 60000)

    } catch (error) {
      console.error('Failed to initialize Keycloak', error)
    }
  }

  const login = () => keycloak.value?.login()
  const logout = () => keycloak.value?.logout()
  
  const getToken = () => token.value

  return {
    keycloak,
    isAuthenticated,
    userProfile,
    token,
    initKeycloak,
    login,
    logout,
    getToken
  }
})
