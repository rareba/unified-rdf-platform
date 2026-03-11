import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Runtime configuration that can be overridden at deploy time.
 *
 * Supports two override mechanisms (checked in order):
 *
 * 1. `window.__env` - Set via a `<script>` tag in index.html before the app loads:
 *    ```html
 *    <script>
 *      window.__env = {
 *        KEYCLOAK_URL: 'https://auth.example.com',
 *        KEYCLOAK_REALM: 'my-realm',
 *        KEYCLOAK_CLIENT_ID: 'my-client',
 *        API_BASE_URL: '/api/v1'
 *      };
 *    </script>
 *    ```
 *
 * 2. `/assets/config.json` - A JSON file that can be volume-mounted in a container:
 *    ```json
 *    {
 *      "KEYCLOAK_URL": "https://auth.example.com",
 *      "KEYCLOAK_REALM": "my-realm",
 *      "KEYCLOAK_CLIENT_ID": "my-client",
 *      "API_BASE_URL": "/api/v1"
 *    }
 *    ```
 *
 * If neither is provided, the build-time environment values are used.
 * Values starting with `__` and ending with `__` (placeholders) are treated as unset.
 */
@Injectable({
  providedIn: 'root'
})
export class RuntimeConfigService {
  private config: Record<string, string> = {};
  private loaded = false;

  /**
   * Load runtime configuration. Call this before app initialization.
   * Tries window.__env first, then falls back to /assets/config.json.
   */
  async load(): Promise<void> {
    if (this.loaded) return;

    // Check window.__env first
    const windowEnv = (window as unknown as Record<string, unknown>)['__env'];
    if (windowEnv && typeof windowEnv === 'object') {
      this.config = { ...(windowEnv as Record<string, string>) };
      this.loaded = true;
      this.applyConfig();
      return;
    }

    // Try loading /assets/config.json
    try {
      const response = await fetch('/assets/config.json');
      if (response.ok) {
        const json = await response.json();
        if (json && typeof json === 'object') {
          this.config = json;
        }
      }
    } catch {
      // config.json is optional - silently continue with build-time values
    }

    this.loaded = true;
    this.applyConfig();
  }

  /**
   * Apply runtime config values to the environment object.
   */
  private applyConfig(): void {
    const env = environment as Record<string, unknown>;
    const auth = env['auth'] as Record<string, unknown> | undefined;
    const keycloak = auth?.['keycloak'] as Record<string, string> | undefined;

    if (this.config['API_BASE_URL']) {
      env['apiBaseUrl'] = this.config['API_BASE_URL'];
    }

    if (keycloak) {
      if (this.config['KEYCLOAK_URL'] || this.isPlaceholder(keycloak['url'])) {
        keycloak['url'] = this.config['KEYCLOAK_URL'] || keycloak['url'];
      }
      if (this.config['KEYCLOAK_REALM'] || this.isPlaceholder(keycloak['realm'])) {
        keycloak['realm'] = this.config['KEYCLOAK_REALM'] || keycloak['realm'];
      }
      if (this.config['KEYCLOAK_CLIENT_ID'] || this.isPlaceholder(keycloak['clientId'])) {
        keycloak['clientId'] = this.config['KEYCLOAK_CLIENT_ID'] || keycloak['clientId'];
      }
    }
  }

  /**
   * Check if a value is a deploy-time placeholder (e.g., __KEYCLOAK_URL__).
   */
  private isPlaceholder(value: string): boolean {
    return typeof value === 'string' && value.startsWith('__') && value.endsWith('__');
  }

  /**
   * Get a runtime config value by key.
   */
  get(key: string): string | undefined {
    return this.config[key];
  }
}
