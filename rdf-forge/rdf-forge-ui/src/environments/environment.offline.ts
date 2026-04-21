export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  baseUri: 'http://localhost:4200/offline/',
  auth: {
    enabled: false,
    keycloak: undefined as undefined | { url: string; realm: string; clientId: string }
  }
};