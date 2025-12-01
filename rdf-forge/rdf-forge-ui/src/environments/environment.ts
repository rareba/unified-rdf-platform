export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: true,
    keycloak: {
      url: 'http://localhost:8080',
      realm: 'rdfforge',
      clientId: 'rdf-forge-ui'
    }
  }
};