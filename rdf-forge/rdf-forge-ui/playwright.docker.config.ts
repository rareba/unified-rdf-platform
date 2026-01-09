import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for testing against Docker UI (port 3000)
 * Use this config when Docker containers are running
 */
export default defineConfig({
  testDir: './e2e/tests',
  fullyParallel: false, // Run tests sequentially for full workflow
  forbidOnly: !!process.env['CI'],
  retries: 0,
  workers: 1,

  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list']
  ],

  use: {
    // Docker UI runs on port 3000
    // Use 127.0.0.1 instead of localhost for better Docker compatibility
    baseURL: 'http://127.0.0.1:3000',
    trace: 'on',
    screenshot: 'on',
    video: 'on',
    // Longer timeouts for Docker networking
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // No webServer - we expect Docker to be running
  webServer: undefined,

  timeout: 120000,

  expect: {
    timeout: 10000
  },
});
