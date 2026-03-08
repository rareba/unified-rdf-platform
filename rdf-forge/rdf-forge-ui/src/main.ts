import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { RuntimeConfigService } from './app/core/services/runtime-config.service';

// Load runtime configuration before bootstrapping the app.
// This allows Keycloak URL and other settings to be overridden at deploy time
// via window.__env or /assets/config.json.
const runtimeConfig = new RuntimeConfigService();
runtimeConfig.load().then(() => {
  bootstrapApplication(App, appConfig)
    .catch((err) => console.error(err));
});
