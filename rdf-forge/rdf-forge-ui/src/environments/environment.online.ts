/**
 * Online environment configuration.
 *
 * Keycloak and API settings can be overridden at deploy time by either:
 * 1. Setting window.__env before the app bootstraps (e.g., via a <script> tag in index.html)
 * 2. Providing a /assets/config.json file mounted into the container
 *
 * See RuntimeConfigService for details.
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: true,
    keycloak: {
      url: '__KEYCLOAK_URL__',
      realm: '__KEYCLOAK_REALM__',
      clientId: '__KEYCLOAK_CLIENT_ID__'
    }
  }
};
