export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  baseUri: '/',
  auth: {
    enabled: false,
    keycloak: undefined as undefined | { url: string; realm: string; clientId: string }
  }
};
