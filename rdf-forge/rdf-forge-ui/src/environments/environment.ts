export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  baseUri: 'http://localhost:8000/',
  auth: {
    enabled: true,
    keycloak: {
      url: 'http://localhost:8080',
      realm: 'rdfforge',
      clientId: 'rdf-forge-ui'
    }
  }
};
