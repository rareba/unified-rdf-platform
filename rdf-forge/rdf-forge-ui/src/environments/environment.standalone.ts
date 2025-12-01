export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: false,
    keycloak: undefined as undefined | { url: string; realm: string; clientId: string }
  }
};
