export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: false,
    keycloak: undefined as undefined | { url: string; realm: string; clientId: string }
  }
};